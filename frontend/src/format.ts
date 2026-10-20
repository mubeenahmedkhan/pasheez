// Intl.NumberFormat instances are comparatively expensive to build, so we keep one
// per precision instead of constructing a new one for every tick of every row.
const formatters = new Map<number, Intl.NumberFormat>()

function formatterFor(digits: number): Intl.NumberFormat {
  let formatter = formatters.get(digits)
  if (!formatter) {
    formatter = new Intl.NumberFormat('en-US', {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    })
    formatters.set(digits, formatter)
  }
  return formatter
}

/**
 * Precision by magnitude: $81,542.01 needs two decimals, $0.079090 needs six.
 * A heuristic, not the truth — Binance publishes a real tick size per symbol, which is
 * the correct source once more asset classes arrive.
 */
export function formatPrice(price: number): string {
  const digits = price >= 1 ? 2 : price >= 0.01 ? 4 : 6
  return formatterFor(digits).format(price)
}
