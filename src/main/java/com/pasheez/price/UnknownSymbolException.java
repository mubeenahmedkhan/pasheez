package com.pasheez.price;

import java.util.Collection;

/** The upstream rejected the symbol — it does not exist (client's fault, not ours). */
public class UnknownSymbolException extends RuntimeException {

    public UnknownSymbolException(Symbol symbol) {
        super("Unknown symbol: " + symbol.value());
    }

    /**
     * Batch form. Binance rejects the whole request if any one symbol is invalid and does
     * not say which, so the message names every symbol that was asked for.
     */
    public UnknownSymbolException(Collection<Symbol> symbols) {
        super("One or more unknown symbols: " + symbols.stream().map(Symbol::value).toList());
    }
}
