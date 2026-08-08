package com.pasheez.price;

public interface PriceSource {
    PriceTick latestPrice(Symbol symbol);
}