package com.pasheez.price;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapter for Kraken's public ticker. Two things make it differ from Binance, and both are
 * absorbed here so nothing outside this class ever sees them:
 * <ul>
 *   <li>Kraken accepts {@code BTCUSDT} in a request but answers under its own legacy names
 *   ({@code XBTUSDT}, {@code XDGUSDT}), so response keys are translated back.</li>
 *   <li>Kraken reports errors as HTTP 200 with an {@code error} array, so the status code alone
 *   proves nothing — the body has to be inspected.</li>
 * </ul>
 */
public class KrakenPriceSource implements PriceSource {

    /** Kraken's legacy asset codes → the common codes the rest of the system uses. */
    private static final Map<String, String> KRAKEN_TO_COMMON = Map.of(
            "XBT", "BTC",
            "XDG", "DOGE");

    private final RestClient restClient;

    KrakenPriceSource(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return "Kraken";
    }

    @Override
    public PriceTick latestPrice(Symbol symbol) {
        return latestPrices(List.of(symbol)).stream()
                .findFirst()
                .orElseThrow(() -> new PriceUnavailableException("Kraken returned no price", null));
    }

    @Override
    public List<PriceTick> latestPrices(Collection<Symbol> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }

        String pairs = symbols.stream().map(Symbol::value).collect(Collectors.joining(","));

        KrakenTickerResponse response;
        try {
            response = restClient.get()
                    .uri("/0/public/Ticker?pair={pairs}", pairs)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new PriceUnavailableException("Kraken returned " + res.getStatusCode(), null);
                    })
                    .body(KrakenTickerResponse.class);
        } catch (ResourceAccessException ex) {
            throw new PriceUnavailableException("Kraken unreachable: " + ex.getMessage(), ex);
        }

        if (response == null) {
            throw new PriceUnavailableException("Kraken returned an empty body", null);
        }
        if (response.error() != null && !response.error().isEmpty()) {
            // A 200 that is really a failure. Unknown pairs are the caller's mistake; anything
            // else (rate limiting, maintenance) is Kraken's, and retryable.
            if (response.error().stream().anyMatch(e -> e.contains("Unknown asset pair"))) {
                throw new UnknownSymbolException(symbols);
            }
            throw new PriceUnavailableException("Kraken error: " + response.error(), null);
        }
        if (response.result() == null) {
            throw new PriceUnavailableException("Kraken returned no result", null);
        }

        Instant readAt = Instant.now();
        return response.result().entrySet().stream()
                .filter(entry -> entry.getValue().c() != null && !entry.getValue().c().isEmpty())
                .map(entry -> new PriceTick(
                        new Symbol(toCommonSymbol(entry.getKey())),
                        new BigDecimal(entry.getValue().c().getFirst()),
                        readAt))
                .toList();
    }

    static String toCommonSymbol(String krakenPair) {
        for (Map.Entry<String, String> alias : KRAKEN_TO_COMMON.entrySet()) {
            if (krakenPair.startsWith(alias.getKey())) {
                return alias.getValue() + krakenPair.substring(alias.getKey().length());
            }
        }
        return krakenPair;
    }
}
