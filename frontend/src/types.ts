// Shared across components, so it lives in its own module rather than being
// re-declared (and drifting) in each file.

/** What the API sends. Mirrors PriceTick on the Java side — but JSON flattens the
 *  types: BigDecimal arrives as a number, Instant as a string. */
export type PriceTick = {
  symbol: string
  price: number
  timestamp: string
}

export type Direction = 'up' | 'down' | 'flat'

export type Status = 'connecting' | 'live' | 'error'
