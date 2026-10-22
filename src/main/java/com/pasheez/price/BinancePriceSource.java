package com.pasheez.price;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Adapter for Binance's public ticker. Selected by {@link PriceSourceConfig}. */
public class BinancePriceSource implements PriceSource {

    private final RestClient restClient;

    BinancePriceSource(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return "Binance";
    }

    @Override
    public String changeWindow() {
        return "24h";
    }

    @Override
    public PriceTick latestPrice(Symbol symbol) {
        BinanceTicker24h response = get(
                "/api/v3/ticker/24hr?symbol={s}",
                BinanceTicker24h.class,
                () -> new UnknownSymbolException(symbol),
                symbol.value());

        if (response == null || response.lastPrice() == null) {
            throw new PriceUnavailableException("Binance returned an empty body", null);
        }
        return toTick(symbol, response, Instant.now());
    }

    /**
     * One upstream request for every symbol, instead of one request per symbol. Binance
     * wants the list as a JSON array in a query parameter: symbols=["BTCUSDT","ETHUSDT"].
     * The 24hr ticker (rather than the plain price ticker) is used because it carries the
     * best bid/ask for the mid price and the rolling 24h change in the same response.
     */
    @Override
    public List<PriceTick> latestPrices(Collection<Symbol> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }

        String symbolsParam = symbols.stream()
                .map(symbol -> "\"" + symbol.value() + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        BinanceTicker24h[] responses = get(
                "/api/v3/ticker/24hr?symbols={symbols}",
                BinanceTicker24h[].class,
                () -> new UnknownSymbolException(symbols),
                symbolsParam);

        if (responses == null) {
            throw new PriceUnavailableException("Binance returned an empty body", null);
        }

        // One timestamp for the whole batch: these prices were read together.
        Instant readAt = Instant.now();
        return java.util.Arrays.stream(responses)
                .filter(response -> response.lastPrice() != null)
                // Binance does not echo the requested order, so trust the symbol in each
                // element rather than its position in the array.
                .map(response -> toTick(new Symbol(response.symbol()), response, readAt))
                .toList();
    }

    private static PriceTick toTick(Symbol symbol, BinanceTicker24h t, Instant readAt) {
        return new PriceTick(
                symbol,
                PriceSource.midPrice(t.bidPrice(), t.askPrice(), t.lastPrice()),
                t.priceChangePercent(),
                readAt);
    }

    /** Shared request plumbing: the two calls differ only in URI, body type, and 400 meaning. */
    private <T> T get(String uriTemplate,
                      Class<T> bodyType,
                      java.util.function.Supplier<RuntimeException> onBadRequest,
                      Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .retrieve()
                    // Binance answers 400 for a symbol it doesn't know — the caller's mistake.
                    .onStatus(status -> status.value() == 400, (request, res) -> {
                        throw onBadRequest.get();
                    })
                    // Rate limits (429/418) and any 5xx are Binance's problem, and retryable.
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new PriceUnavailableException("Binance returned " + res.getStatusCode(), null);
                    })
                    .body(bodyType);
        } catch (ResourceAccessException ex) {
            // Timeouts and connection failures, including the timeouts configured in
            // BinanceClientConfig.
            throw new PriceUnavailableException("Binance unreachable: " + ex.getMessage(), ex);
        }
    }
}
