# 5. Select the price source by configuration; run Kraken in production

- **Status:** Accepted
- **Date:** 2026-10-22
- **Milestone:** M4 (pulls the core of M5 forward)

## Context

Deployed to Render (ADR 0004), every Binance call failed — not because of our code, but because
Binance refuses Render's shared outbound IPs: a silent connect timeout on `api.binance.com`, and
HTTP 418 ("IP banned") on `data-api.binance.vision`. The same image works from a residential
connection. We needed prices in production without leaving the free tier.

## Decision

Introduce a second adapter, `KrakenPriceSource`, behind the existing `PriceSource` port, and pick
the adapter by configuration:

```yaml
price-source:
  provider: binance   # binance | kraken
```

`PriceSourceConfig` declares one `@Bean` per adapter, each guarded by `@ConditionalOnProperty`,
so exactly one `PriceSource` exists at runtime. Production sets `PRICE_SOURCE_PROVIDER=kraken`;
local development keeps Binance. The bounded HTTP client from ADR 0002 moved into
`PriceSourceConfig` so every adapter gets identical timeouts (`upstream.*`).

`PriceSource` gained `name()`, exposed at `GET /prices/source`, so the UI states which exchange
the numbers come from instead of hardcoding one.

## Consequences

**Good**

- The controller, stream service and poller did not change at all. This is the first real
  payoff of the M1 port/adapter split: a new upstream is one adapter and one bean method.
- Kraken's quirks are contained in its adapter: it answers under legacy names (`XBTUSDT`,
  `XDGUSDT`) that are translated back, and it reports failures as **HTTP 200 with an `error`
  array**, so the body — not the status — decides success. Both are pinned by tests.

**Bad / accepted costs**

- Production and development read different exchanges, so prices differ slightly between them.
  Acceptable for a price display; it would not be for anything transactional.
- Kraken may also throttle shared cloud IPs in future. If so, ADR 0004's Oracle VM alternative
  becomes the fix.
- An unrecognised `provider` value leaves no `PriceSource` bean and startup fails. That is the
  desired fail-fast behaviour, but the error names a missing bean rather than the bad value.

## Alternatives considered

- **Other Binance hosts** (`api1`, `api-gcp`, `data-api.binance.vision`) — bans are per IP, so
  switching hostnames does not help.
- **KuCoin / OKX** — viable; Kraken chosen because it lists all eight watchlist pairs, needs no
  key for public data, and has a stable, well-documented ticker API.
- **Calling an exchange from the browser** — would bypass our backend entirely and defeat the
  architecture (caching, rate-limit control, single connection).
