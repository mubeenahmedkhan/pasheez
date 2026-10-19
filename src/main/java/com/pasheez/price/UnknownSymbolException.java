package com.pasheez.price;

/** The upstream rejected the symbol — it does not exist (client's fault, not ours). */
public class UnknownSymbolException extends RuntimeException {

    public UnknownSymbolException(Symbol symbol) {
        super("Unknown symbol: " + symbol.value());
    }
}
