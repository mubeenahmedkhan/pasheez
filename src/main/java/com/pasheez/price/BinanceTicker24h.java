package com.pasheez.price;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Binance's wire format for /api/v3/ticker/24hr — only the fields we use. */
@JsonIgnoreProperties(ignoreUnknown = true)
record BinanceTicker24h(String symbol,
                        BigDecimal lastPrice,
                        BigDecimal priceChangePercent,
                        BigDecimal bidPrice,
                        BigDecimal askPrice) {
}
