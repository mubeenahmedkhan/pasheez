package com.pasheez.price;

import java.math.BigDecimal;

public record BinanceTickerResponse(String symbol, BigDecimal price) {
}
