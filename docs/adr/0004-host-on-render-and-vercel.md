# 4. Host the backend on Render and the frontend on Vercel

- **Status:** Accepted
- **Date:** 2026-10-22
- **Milestone:** M4

## Context

The app only ran on localhost. The backend is a long-lived JVM that holds SSE connections open,
which rules out serverless hosts. The frontend is a static bundle produced by `vite build`.
Budget: free.

## Decision

- **Backend → Render**, free web service, **Docker** runtime (our multi-stage `Dockerfile`),
  **Singapore** region, health check `/actuator/health`.
- **Frontend → Vercel**, root directory `frontend`, `VITE_API_BASE_URL` set in Vercel's
  environment to the Render URL.
- CI (GitHub Actions) runs backend tests, the frontend build, and a Docker build on every push.

## Consequences

**Good**

- Free, HTTPS on both sides, deploy on push, and a portable Docker image: nothing here locks
  the backend to Render.
- Static frontend on a CDN; the only dynamic component is the API.

**Bad / accepted costs**

- **Free instances sleep after ~15 min idle.** The SSE stream dies, `EventSource` retries, and
  the retry pays a JVM cold start on 0.1 CPU. A visitor to a sleeping instance sees `error`
  for a while before prices appear.
- **Shared outbound IPs.** Render's free egress IPs are shared across tenants. Binance drops
  traffic to `api.binance.com` from them (connect timeout via CloudFront) and bans them on
  `data-api.binance.vision` (HTTP 418). This forced ADR 0005.
- **Region matters for upstreams.** Binance answers US IPs with HTTP 451, so US regions are
  excluded. Render does not allow changing a service's region after creation.

## Alternatives considered

- **Oracle Cloud Always Free VM** — own IP (fixes the shared-IP ban), always on, 4 ARM cores /
  24 GB. Deferred: much more operations work (VM, Docker, reverse proxy, TLS). Planned for when
  the sleep wart or the multi-service milestones make it worth it.
- **Railway / Fly.io** — better always-on behaviour, but not free.
- **Vercel for the backend** — impossible: serverless functions cannot hold an SSE connection.
