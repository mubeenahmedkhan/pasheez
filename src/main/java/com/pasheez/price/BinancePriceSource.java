package com.pasheez.price;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
public class BinancePriceSource implements PriceSource {

    private final RestClient restClient;

    BinancePriceSource(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PriceTick latestPrice(Symbol symbol) {
        BinanceTickerResponse response;
        try {
            response = restClient.get()
                    .uri("/api/v3/ticker/price?symbol={s}", symbol.value())
                    .retrieve()
                    // Binance answers 400 for a symbol it doesn't know. That is the caller's
                    // mistake, so it must not be reported as an upstream failure.
                    .onStatus(status -> status.value() == 400, (request, res) -> {
                        throw new UnknownSymbolException(symbol);
                    })
                    // Everything else that is an error status (429/418 rate limits, any 5xx)
                    // is Binance's problem, and is potentially retryable.
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new PriceUnavailableException(
                                "Binance returned " + res.getStatusCode(), null);
                    })
                    .body(BinanceTickerResponse.class);
        } catch (ResourceAccessException ex) {
            // Timeouts and connection failures land here — including the connect/read
            // timeouts configured in BinanceClientConfig.
            throw new PriceUnavailableException("Binance unreachable: " + ex.getMessage(), ex);
        }

        if (response == null || response.price() == null) {
            throw new PriceUnavailableException("Binance returned an empty body", null);
        }

        return new PriceTick(symbol, response.price(), Instant.now());
    }
}
