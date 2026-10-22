import { useEffect, useRef, useState } from 'react'
import PriceRow from './PriceRow'
import type { Direction, PriceTick, SourceInfo, Status, Theme } from './types'
import './App.css'

// Injected at build time from .env.development locally, and from the hosting
// provider's environment variables when deployed. Hardcoding localhost here would mean
// a deployed page tries to call the visitor's own machine.
const API_BASE = import.meta.env.VITE_API_BASE_URL

// The watchlist: eight of the most heavily traded USDT pairs.
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
const THEME_KEY = 'pasheez-theme'

// localStorage can throw (private windows, blocked storage), so every access is guarded
// and the page works without it — the theme simply isn't remembered.
function readSavedTheme(): Theme {
  try {
    return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

function secondsSince(timestamp: string | undefined, now: number): number | null {
  return timestamp ? Math.max(0, Math.round((now - Date.parse(timestamp)) / 1000)) : null
}

function App() {
  // Keyed by symbol rather than an array, so a tick updates one entry without
  // searching or reordering.
  const [ticks, setTicks] = useState<Record<string, PriceTick>>({})
  const [directions, setDirections] = useState<Record<string, Direction>>({})
  const [status, setStatus] = useState<Status>('connecting')
  // Asked for, not assumed: production and local development deliberately use different
  // exchanges, which also measure "change" over different windows (see ADR 0005/0006).
  const [source, setSource] = useState<SourceInfo | null>(null)
  // Lazy initialiser: the function runs once, on first render, not on every render.
  const [theme, setTheme] = useState<Theme>(readSavedTheme)
  const [now, setNow] = useState(() => Date.now())

  // Previous prices are bookkeeping, not something we draw — a ref survives renders
  // without causing them.
  const previousPrices = useRef<Record<string, number>>({})

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch {
      // Not remembered across visits; harmless.
    }
  }, [theme])

  // A one-second clock so "updated 3s ago" counts up between ticks. Same cleanup rule as
  // the stream: an interval left running after unmount is a leak.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    fetch(`${API_BASE}/prices/source`)
      .then((response) => (response.ok ? response.json() : null))
      .then((info: SourceInfo | null) => setSource(info))
      .catch(() => setSource(null))
  }, [])

  useEffect(() => {
    // ONE connection for the whole watchlist. Browsers cap concurrent HTTP/1.1
    // connections per origin at about six, and each SSE stream holds one open
    // indefinitely — eight separate EventSources would starve the page.
    const stream = new EventSource(`${API_BASE}/prices/stream?symbols=${SYMBOLS}`)

    stream.addEventListener('price', (event) => {
      const tick = JSON.parse((event as MessageEvent).data) as PriceTick

      const previous = previousPrices.current[tick.symbol]
      const direction: Direction =
        previous === undefined || tick.price === previous
          ? 'flat'
          : tick.price > previous
            ? 'up'
            : 'down'
      previousPrices.current[tick.symbol] = tick.price

      // Functional updates derive the next state from the previous state, and build a NEW
      // object — React decides what changed by comparing references.
      setTicks((current) => ({ ...current, [tick.symbol]: tick }))
      setDirections((current) => ({ ...current, [tick.symbol]: direction }))
      setStatus('live')
    })

    stream.onerror = () => setStatus('error')

    // Runs on unmount and before the effect re-runs. Without it every re-render would
    // leak another open connection that the Spring-side poller keeps serving.
    return () => stream.close()
  }, [])

  const lastUpdate = Object.values(ticks)
    .map((tick) => tick.timestamp)
    .sort()
    .at(-1)
  const age = secondsSince(lastUpdate, now)

  return (
    <main className="panel">
      <div className="girih-band" aria-hidden="true" />

      <header className="masthead">
        <button
          type="button"
          className="theme-toggle"
          onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
          aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} theme`}
          title={`Switch to ${theme === 'light' ? 'dark' : 'light'} theme`}
        >
          {theme === 'light' ? '☾' : '☀'}
        </button>
        <h1 className="wordmark">pasheez</h1>
        <p className="tagline">Live crypto prices, the moment they move</p>
        <p className="blurb">
          Eight of the most traded crypto pairs, streamed to this page over a single open
          connection. Nothing to refresh — the numbers update themselves.
        </p>
      </header>

      <div className="columns" aria-hidden="true">
        <span>Asset</span>
        <span>Price (USDT)</span>
        <span>
          Change
          {source && <small className="window">{source.changeWindow}</small>}
        </span>
      </div>

      <ul className="quotes">
        {WATCHLIST.map((asset) => (
          // key lets React match each element to the same DOM node across renders.
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
          {age === null ? 'awaiting first tick' : age < 2 ? 'updated just now' : `updated ${age}s ago`}
        </span>
      </footer>

      <p className="footnote">
        Mid prices{source ? ` from ${source.name}` : ''}, halfway between the best bid and ask.
        <br />
        <em>Pasheez</em> — an old Persian word for a small copper coin.
      </p>
    </main>
  )
}

export default App
