import type { Direction, PriceTick } from './types'
import { formatPrice } from './format'

// The component's inputs. Props are just function parameters with a type.
type PriceRowProps = {
  name: string
  symbol: string
  tick: PriceTick | undefined
  direction: Direction
}

/**
 * Presentational only: it holds no state and performs no fetching. Given the same props
 * it always renders the same thing, which makes it trivial to reason about and reuse.
 */
function PriceRow({ name, symbol, tick, direction }: PriceRowProps) {
  const base = symbol.replace('USDT', '')

  return (
    <li className="row">
      <span className="row-asset">
        <span className="row-name">{name}</span>
        <span className="row-pair">
          {base} / USDT
        </span>
      </span>
      <span className={`row-price ${direction}`}>
        {tick ? formatPrice(tick.price) : '—'}
      </span>
    </li>
  )
}

export default PriceRow
