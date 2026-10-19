# 1. Use SSE (not WebSocket) for live price push

- **Status:** Accepted
- **Date:** 2026-10-19
- **Milestone:** M2

## Context

Clients need prices that update live. Binance exposes a public REST ticker endpoint and
a WebSocket market stream; our own API needs some way to get continuous updates to a
browser. The traffic is strictly one-way — the server emits prices, the client never
sends anything mid-connection.

## Decision

Push prices to clients over **Server-Sent Events** (`text/event-stream`) via Spring MVC's
`SseEmitter`, fed by a scheduled poller that calls the Binance REST endpoint.

## Consequences

**Good**

- One-way by design, which matches the problem exactly.
- Plain HTTP: no protocol upgrade, so proxies, CDNs, and HTTP/2 work normally.
- The browser's `EventSource` reconnects automatically after a drop; a WebSocket client
  has to implement reconnect and backoff by hand.
- Trivially debuggable from a terminal — `curl -N http://localhost:8080/prices/btcusdt/stream`
  prints the live stream, no special client needed.

**Bad / accepted costs**

- Client → server messages are impossible on the same connection. If a client ever needs
  to change its subscription live, that is a separate request (acceptable: it can simply
  open a different stream URL).
- `EventSource` **cannot send custom headers**, so `Authorization: Bearer …` will not work
  on this connection. When auth arrives, it must be a cookie, a query parameter, or a
  `fetch`-based streaming client instead. This constraint is recorded now so the auth
  design does not assume headers.
- One HTTP connection is held open per client per symbol. Fine at this scale; becomes a
  connection-count problem long before it becomes a CPU problem.
- Polling Binance means our tick rate is our poll interval, not Binance's true tick rate.

## Alternatives considered

- **WebSocket** — rejected: full-duplex capability we do not need, in exchange for manual
  reconnect logic and more proxy friction.
- **Client polling our REST endpoint** — rejected: every client would hammer us on a timer,
  and the update latency would be the client's interval plus ours.
- **Consuming Binance's WebSocket stream instead of its REST endpoint** — deferred, not
  rejected. It is the correct upstream choice once many symbols are live (see M9/M10);
  REST polling is the smaller first step and keeps one blocking client to reason about.
