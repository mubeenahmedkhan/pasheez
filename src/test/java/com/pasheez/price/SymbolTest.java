package com.pasheez.price;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolTest {

    @Test
    void upperCasesTheValue() {
        assertEquals("BTCUSDT", new Symbol("btcusdt").value());
    }

    @Test
    void leavesAnAlreadyUpperCaseValueAlone() {
        assertEquals("ETHUSDT", new Symbol("ETHUSDT").value());
    }

    @Test
    void theDefaultBatchMethodFallsBackToOneCallPerSymbol() {
        // HardcodedPriceSource does not override latestPrices, so this exercises the
        // default implementation on the PriceSource interface.
        java.util.List<PriceTick> ticks = new HardcodedPriceSource()
                .latestPrices(java.util.List.of(new Symbol("btcusdt"), new Symbol("ethusdt")));

        assertEquals(2, ticks.size());
        assertEquals("BTCUSDT", ticks.get(0).symbol().value());
        assertEquals("ETHUSDT", ticks.get(1).symbol().value());
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Symbol(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Symbol("   "));
    }
}
