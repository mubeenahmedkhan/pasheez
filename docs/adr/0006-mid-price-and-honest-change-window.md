# 6. Show the mid price, and label the change window per source

- **Status:** Accepted
- **Date:** 2026-10-23
- **Milestone:** M4 polish

## Context

In production (Kraken, ADR 0005) prices looked frozen: 11 ticks in 20 s carried only 3
distinct values. Polling was fine. The cause was *what* we displayed — the **last trade** —
and Kraken's BTC/USDT market trades rarely, because most Kraken BTC volume is against USD.
Sampled over 10 s, BTC/USDT's last trade never moved while the bid/ask midpoint moved 4 times.

Separately, users needed context for a price: is it up or down? Both exchanges supply a
change figure, but over **different windows**: Binance's `/ticker/24hr` is a rolling 24 hours;
Kraken's ticker only gives today's opening price (00:00 UTC).

## Decision

1. `PriceTick.price` is the **mid price**, `(best bid + best ask) / 2`, on every source, with a
   fallback to the last trade if the book is missing. Same meaning locally and in production.
   Binance now uses `/api/v3/ticker/24hr` (bid, ask and change in one response).
2. `PriceTick` gains `changePercent`. Binance: its `priceChangePercent` (rolling 24 h).
   Kraken: computed from last trade vs today's open.
3. `PriceSource.changeWindow()` (`"24h"` / `"since 00:00 UTC"`) is served from
   `GET /prices/source`, and the UI prints it under the column header. A column labelled "24h"
   that is really "since midnight UTC" would quietly mislead.

## Consequences

**Good**

- Kraken prices visibly move: 7 distinct values in 20 s instead of 3.
- One meaning of "price" everywhere, and every number on screen says what it is.

**Bad / accepted costs**

- The mid price is not a price anyone traded at. Standard for display, wrong for anything
  transactional — labelled in the UI footnote.
- The two environments' change figures are not comparable with each other. Accepted, and
  visible, rather than hidden.
- Binance's 24hr ticker costs more request weight than the plain price ticker; at 8 symbols
  every 2 s it remains far inside the limit.
