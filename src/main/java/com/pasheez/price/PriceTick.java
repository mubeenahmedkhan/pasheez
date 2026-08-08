package com.pasheez.price;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceTick(Symbol symbol, BigDecimal price, Instant timestamp) {}
