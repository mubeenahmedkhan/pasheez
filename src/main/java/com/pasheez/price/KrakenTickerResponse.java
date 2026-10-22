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

    /**
     * Kraken's single-letter fields: {@code a} best ask, {@code b} best bid, {@code c} last trade —
     * each a list whose first element is the price — and {@code o}, the opening price at
     * 00:00 UTC today. Everything else is ignored.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ticker(List<String> a, List<String> b, List<String> c, String o) {
    }
}
