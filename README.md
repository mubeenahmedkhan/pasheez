# pasheez

Live crypto prices, the moment they move.

Eight of the most traded crypto pairs, streamed to the browser over a single open connection —
nothing to refresh. Each row shows the live **mid price** (halfway between the best bid and ask)
and its **change** over the exchange's window, in a light or dark Persian-tilework theme.
*Pasheez* is an old Persian word for a small copper coin.

- **Live site:** https://pasheez.vercel.app
- **API:** https://pasheez-api.onrender.com — e.g. [`/prices/BTCUSDT`](https://pasheez-api.onrender.com/prices/BTCUSDT)

> The API runs on a free instance that sleeps when idle. The first visit after a quiet spell can
> take a minute while it wakes up; after that prices stream normally.

## Stack

- **Backend:** Java 21, Spring Boot 4, Server-Sent Events, Docker
- **Frontend:** React 19, TypeScript, Vite
- **Prices:** Kraken in production, Binance locally — chosen by configuration ([why](docs/adr/0005-select-the-price-source-by-configuration.md)); mid price and labelled change window ([why](docs/adr/0006-mid-price-and-honest-change-window.md))
- **CI:** GitHub Actions — backend tests, frontend type-check and build, Docker image build

See [docs/architecture.md](docs/architecture.md) for diagrams and the decision records.

## API

| Endpoint | Returns |
|---|---|
| `GET /prices/{symbol}` | latest tick, e.g. `/prices/BTCUSDT` → `{"symbol","price","changePercent","timestamp"}` |
| `GET /prices/stream?symbols=BTCUSDT,ETHUSDT` | SSE stream of `price` events (max 20 symbols) |
| `GET /prices/source` | exchange in use and its change window, e.g. `{"name":"Kraken","changeWindow":"since 00:00 UTC"}` |
| `GET /actuator/health` | liveness for the hosting platform |

Unknown symbols return `404`; upstream problems return `503`, both as RFC 9457 problem details.

## Run locally

Backend (port 8080):

```bash
./mvnw spring-boot:run
# or, in a container:
docker build -t pasheez:local .
docker run --rm -p 8080:8080 pasheez:local
```

Frontend (port 5173, reads `VITE_API_BASE_URL` from `frontend/.env.development`):

```bash
cd frontend
nvm use          # Node version from .nvmrc
npm ci
npm run dev
```

Tests: `./mvnw test`.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PRICE_SOURCE_PROVIDER` | `binance` | `binance` or `kraken` |
| `FRONTEND_ALLOWED_ORIGINS` | `http://localhost:5173` | CORS origin **patterns**, comma-separated |
| `PORT` | `8080` | set by the hosting platform |
| `VITE_API_BASE_URL` | — | frontend build-time API URL; the build fails if unset |
