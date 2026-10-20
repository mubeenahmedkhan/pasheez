package com.pasheez.price;

import com.fasterxml.jackson.annotation.JsonValue;

public record Symbol(@JsonValue String value) {
    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }
        value = value.toUpperCase();
    }
}
