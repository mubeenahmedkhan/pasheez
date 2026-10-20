package com.pasheez.price;

/** We could not get a price right now: upstream was down, slow, or rate-limiting us. */
public class PriceUnavailableException extends RuntimeException {

    public PriceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
