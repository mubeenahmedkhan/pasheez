package com.pasheez.price;

import com.fasterxml.jackson.annotation.JsonValue;

public record Symbol(@JsonValue String value) {
    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }
        // Trim before upper-casing: " btc " would otherwise be sent to Binance as
        // " BTC " and rejected with a 400.
        value = value.trim().toUpperCase();
    }
}
