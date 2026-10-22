package com.pasheez.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

public interface PriceSource {

    /** Human-readable name of the upstream, shown to users so the data's origin is honest. */
    String name();

    /**
     * What {@link PriceTick#changePercent()} is measured against. Exchanges differ — Binance
     * reports a rolling 24 hours, Kraken only the change since 00:00 UTC — and a column labelled
     * "24h" that is really "since midnight" would quietly mislead.
     */
    String changeWindow();

    PriceTick latestPrice(Symbol symbol);

    /**
     * Fetch several prices at once.
     * <p>
     * A {@code default} method so adding this did not break every existing implementor:
     * sources without a batch API inherit the one-at-a-time loop, and an adapter whose
     * upstream supports batching overrides it.
     */
    default List<PriceTick> latestPrices(Collection<Symbol> symbols) {
        return symbols.stream().map(this::latestPrice).toList();
    }

    /** Halfway between best bid and best ask; falls back to the last trade if either is missing. */
    static BigDecimal midPrice(BigDecimal bid, BigDecimal ask, BigDecimal lastTrade) {
        if (bid == null || ask == null || bid.signum() <= 0 || ask.signum() <= 0) {
            return lastTrade;
        }
        // Dividing by two always terminates, so this is exact.
        return bid.add(ask).divide(BigDecimal.TWO);
    }

    /** Percentage change from {@code reference} to {@code current}, two decimal places. */
    static BigDecimal percentChange(BigDecimal reference, BigDecimal current) {
        if (reference == null || current == null || reference.signum() == 0) {
            return null;
        }
        return current.subtract(reference)
                .multiply(BigDecimal.valueOf(100))
                .divide(reference, 2, RoundingMode.HALF_UP);
    }
}
