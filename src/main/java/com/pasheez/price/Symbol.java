package com.pasheez.price;

public record Symbol(String value) {
    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be blank");
        }
        value = value.toUpperCase();
    }
}