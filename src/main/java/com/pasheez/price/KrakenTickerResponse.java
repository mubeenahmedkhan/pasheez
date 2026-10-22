package com.pasheez.price;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Kraken's wire format for /0/public/Ticker. Unlike Binance, failures arrive as HTTP 200 with
 * a non-empty {@code error} list, and results are keyed by Kraken's own pair names.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record KrakenTickerResponse(List<String> error, Map<String, Ticker> result) {

    /** {@code c} is the last closed trade: [price, lot volume]. Everything else is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ticker(List<String> c) {
    }
}
