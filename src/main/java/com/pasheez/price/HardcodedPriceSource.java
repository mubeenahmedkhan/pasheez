package com.pasheez.price;


import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class HardcodedPriceSource implements PriceSource {
    @Override
    public PriceTick
    latestPrice(Symbol symbol) {
        return new PriceTick(symbol, new BigDecimal("65000.00"), Instant.now());
    }
}