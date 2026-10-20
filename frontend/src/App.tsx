import { useEffect, useRef, useState } from 'react'
import PriceRow from './PriceRow'
import type { Direction, PriceTick, Status } from './types'
import './App.css'

const API_BASE = 'http://localhost:8080'

// The watchlist: eight of the most heavily traded USDT pairs on Binance.
const WATCHLIST = [
  { symbol: 'BTCUSDT', name: 'Bitcoin' },
  { symbol: 'ETHUSDT', name: 'Ethereum' },
  { symbol: 'BNBUSDT', name: 'BNB' },
  { symbol: 'SOLUSDT', name: 'Solana' },
  { symbol: 'XRPUSDT', name: 'XRP' },
  { symbol: 'DOGEUSDT', name: 'Dogecoin' },
  { symbol: 'ADAUSDT', name: 'Cardano' },
  { symbol: 'AVAXUSDT', name: 'Avalanche' },
]

const SYMBOLS = WATCHLIST.map((asset) => asset.symbol).join(',')

function App() {
  // Keyed by symbol rather than an array, so a tick updates one entry without
  // searching or reordering.
  const [ticks, setTicks] = useState<Record<string, PriceTick>>({})
  const [directions, setDirections] = useState<Record<string, Direction>>({})
  const [status, setStatus] = useState<Status>('connecting')

  // Previous prices are bookkeeping, not something we draw — a ref survives renders
  // without causing them.
  const previousPrices = useRef<Record<string, number>>({})

  useEffect(() => {
    // ONE connection for the whole watchlist. Browsers cap concurrent HTTP/1.1
    // connections per origin at about six, and each SSE stream holds one open
    // indefinitely — eight separate EventSources would starve the page.
    const source = new EventSource(`${API_BASE}/prices/stream?symbols=${SYMBOLS}`)

    source.addEventListener('price', (event) => {
      const tick = JSON.parse((event as MessageEvent).data) as PriceTick

      const previous = previousPrices.current[tick.symbol]
      const direction: Direction =
        previous === undefined || tick.price === previous
          ? 'flat'
          : tick.price > previous
            ? 'up'
            : 'down'
      previousPrices.current[tick.symbol] = tick.price

      // Functional updates: React may batch several ticks together, so we derive the
      // next state from the previous state rather than from a possibly stale closure.
      // And we build a NEW object instead of mutating — React decides what changed by
      // comparing references, so a mutation would render nothing.
      setTicks((current) => ({ ...current, [tick.symbol]: tick }))
      setDirections((current) => ({ ...current, [tick.symbol]: direction }))
      setStatus('live')
    })

    source.onerror = () => setStatus('error')

    // Runs on unmount and before the effect re-runs. Without it every re-render would
    // leak another open connection that the Spring-side poller keeps serving.
    return () => source.close()
  }, [])

  const lastUpdate = Object.values(ticks)
    .map((tick) => tick.timestamp)
    .sort()
    .at(-1)

  return (
    <main className="panel">
      <header className="masthead">
        <h1 className="wordmark">pasheez</h1>
        <p className="tagline">Live crypto prices, the moment they move</p>
        <p className="blurb">
          The eight most traded pairs on Binance, streamed to this page over a single open
          connection. Nothing to refresh — the numbers update themselves.
        </p>
      </header>

      <ul className="quotes">
        {WATCHLIST.map((asset) => (
          // key lets React match each element to the same DOM node across renders.
          // Without it React falls back to position and reuses the wrong nodes.
          <PriceRow
            key={asset.symbol}
            name={asset.name}
            symbol={asset.symbol}
            tick={ticks[asset.symbol]}
            direction={directions[asset.symbol] ?? 'flat'}
          />
        ))}
      </ul>

      <footer className="meta">
        <span className={`status ${status}`}>{status}</span>
        <span>
          {lastUpdate ? `updated ${new Date(lastUpdate).toLocaleTimeString()}` : 'awaiting first tick'}
        </span>
      </footer>

      <p className="footnote">
        Prices from Binance. <em>Pasheez</em> — an old Persian word for a small copper coin.
      </p>
    </main>
  )
}

export default App
