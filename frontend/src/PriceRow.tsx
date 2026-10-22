import type { Direction, PriceTick } from './types'
import { formatPrice } from './format'

type PriceRowProps = {
  name: string
  symbol: string
  tick: PriceTick | undefined
  direction: Direction
}

const percentFormat = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
})

/**
 * Presentational only: it holds no state and performs no fetching. Given the same props
 * it always renders the same thing, which makes it trivial to reason about and reuse.
 */
function PriceRow({ name, symbol, tick, direction }: PriceRowProps) {
  const base = symbol.replace('USDT', '')
  const change = tick?.changePercent ?? null
  const trend = change === null || change === 0 ? 'flat' : change > 0 ? 'up' : 'down'

  return (
    <li className="row">
      <span className="row-asset">
        <span className="row-name">{name}</span>
        <span className="row-pair">{base} / USDT</span>
      </span>

      {/* key = timestamp: a new key makes React mount a fresh element, which restarts the
          CSS flash animation on every tick. Re-using the old element would play it once. */}
      <span key={tick?.timestamp} className={`row-price ${direction} flash-${direction}`}>
        {tick ? formatPrice(tick.price) : '—'}
      </span>

      <span className={`row-change ${trend}`}>
        {change === null ? '—' : (
          <>
            <span aria-hidden="true">{trend === 'up' ? '▲' : trend === 'down' ? '▼' : '•'}</span>
            {percentFormat.format(change)}%
          </>
        )}
      </span>
    </li>
  )
}

export default PriceRow
