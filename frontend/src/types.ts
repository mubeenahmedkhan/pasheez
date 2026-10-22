// Shared across components, so it lives in its own module rather than being
// re-declared (and drifting) in each file.

/** What the API sends. Mirrors PriceTick on the Java side — but JSON flattens the
 *  types: BigDecimal arrives as a number, Instant as a string. */
export type PriceTick = {
  symbol: string
  /** Mid price: halfway between the best bid and best ask. */
  price: number
  /** Change over the source's window, or null if the source cannot say. */
  changePercent: number | null
  timestamp: string
}

/** GET /prices/source — which exchange, and what its change figure is measured against. */
export type SourceInfo = {
  name: string
  changeWindow: string
}

export type Direction = 'up' | 'down' | 'flat'

export type Status = 'connecting' | 'live' | 'error'

export type Theme = 'light' | 'dark'
