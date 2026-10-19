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
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Symbol(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Symbol("   "));
    }
}
