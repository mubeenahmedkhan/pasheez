package com.pasheez.price;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One observation of a market.
 *
 * @param price         the <b>mid price</b> — halfway between the best bid and best ask. It moves
 *                      whenever the order book moves, not only when a trade happens, and means the
 *                      same thing on every exchange.
 * @param changePercent change over the source's window (see {@link PriceSource#changeWindow()}),
 *                      or {@code null} if the source cannot say.
 */
public record PriceTick(Symbol symbol, BigDecimal price, BigDecimal changePercent, Instant timestamp) {
}
