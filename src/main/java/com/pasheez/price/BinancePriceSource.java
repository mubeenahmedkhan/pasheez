package com.pasheez.price;

import org.springframework.stereotype.Component;
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
        BinanceTickerResponse response = restClient.get()
                .uri("/api/v3/ticker/price?symbol={s}", symbol.value())
                .retrieve()
                .body(BinanceTickerResponse.class);

        return new PriceTick(symbol, response.price(), Instant.now());
    }
}
