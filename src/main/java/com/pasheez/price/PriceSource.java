package com.pasheez.price;

import java.util.Collection;
import java.util.List;

public interface PriceSource {

    PriceTick latestPrice(Symbol symbol);

    /**
     * Fetch several prices at once.
     * <p>
     * A {@code default} method so adding this did not break every existing implementor:
     * sources without a batch API inherit the one-at-a-time loop, and an adapter whose
     * upstream supports batching overrides it. This is exactly what interface default
     * methods are for — evolving a published interface without a breaking change.
     */
    default List<PriceTick> latestPrices(Collection<Symbol> symbols) {
        return symbols.stream().map(this::latestPrice).toList();
    }
}
