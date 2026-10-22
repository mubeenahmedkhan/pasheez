package com.pasheez.price;

import java.math.BigDecimal;
import java.time.Instant;

// Not a Spring bean anymore - kept as a plain test double now that BinancePriceSource is the real adapter.
public class HardcodedPriceSource implements PriceSource {
    @Override
    public String name() {
        return "Hardcoded";
    }

    @Override
    public PriceTick latestPrice(Symbol symbol) {
        return new PriceTick(symbol, new BigDecimal("65000.00"), Instant.now());
    }
}