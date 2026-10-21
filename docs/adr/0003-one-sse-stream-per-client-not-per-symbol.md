# 3. One SSE stream per client, not per symbol

- **Status:** Accepted
- **Date:** 2026-10-21
- **Milestone:** M3

## Context

M2 exposed `GET /prices/{symbol}/stream` — one SSE connection per symbol. That was fine
while the UI showed a single price. M3 displays a watchlist of eight pairs, which under the
original design means eight simultaneous `EventSource` objects from one browser tab.

That does not work, for a reason outside our code: **browsers cap concurrent HTTP/1.1
connections at roughly six per origin.** An SSE response is deliberately never closed, so
each stream occupies one of those slots for as long as the page is open. Eight streams would
leave two symbols permanently unconnected *and* starve the page of connections for every
other request it needs to make.

HTTP/2 multiplexing would raise the ceiling dramatically, but it cannot be relied on: it
requires HTTPS end to end, and the local dev server speaks HTTP/1.1.

A second pressure pointed the same way. The poller fetched each subscribed symbol with its
own upstream request, sequentially, on a single scheduler thread. At eight symbols with a 3 s
read timeout, a slow run can exceed the 2 s poll interval and ticks start lagging — quite
apart from Binance's request-weight limits.

## Decision

Add `GET /prices/stream?symbols=A,B,C` — **one stream carrying many symbols**.

- One `SseEmitter` is registered in the subscriber registry under each requested symbol;
  teardown unregisters it from all of them.
- `PriceSource` gains `latestPrices(Collection<Symbol>)` as a **`default` method**, so the
  interface could grow without breaking existing implementors. `BinancePriceSource` overrides
  it using Binance's batch endpoint (`/api/v3/ticker/price?symbols=["A","B"]`); sources with
  no batch API inherit a one-at-a-time loop.
- The poller makes **one upstream request per tick regardless of symbol count**.
- `MAX_SYMBOLS_PER_STREAM = 20`, returning 400 past that.
- The per-symbol endpoint is kept — it is still the simplest thing to `curl`, and it now
  delegates to the multi-symbol path.

## Consequences

**Good**

- One connection per client, so the browser's connection budget is a non-issue no matter how
  long the watchlist grows.
- One upstream request per tick instead of N: request weight stops scaling with watchlist
  size, and the single scheduler thread no longer makes N sequential blocking calls.
- Every price in a batch shares one timestamp, which is honest — they were read together.

**Bad / accepted costs**

- **A batch failure now affects every symbol at once**, where the per-symbol loop isolated
  failures. Accepted: it is a single HTTP request, so in practice a failure was already total.
- Binance does **not** echo the requested order, so results must be keyed by the `symbol`
  field in each element and never by array position. There is a test that responds in
  deliberately reversed order to pin this down.
- A 400 from the batch call does not say *which* symbol was invalid, so the error names every
  symbol that was requested.
- Changing a watchlist means closing the stream and opening a new one. Acceptable for now; if
  live subscription changes are ever needed, that is the point at which WebSocket's
  bidirectionality would genuinely earn its complexity (see ADR 0001).
