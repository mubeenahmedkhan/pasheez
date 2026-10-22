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
 * Precision by magnitude, so every row shows enough digits for its movement to be visible:
 * 81,365.73 · 103.45 · 1.3105 · 0.07862. Two decimals on a $1.31 coin hide every move.
 * A heuristic, not the truth — exchanges publish a real tick size per symbol, which is the
 * correct source once more asset classes arrive.
 */
export function formatPrice(price: number): string {
  const digits = price >= 100 ? 2 : price >= 1 ? 4 : price >= 0.01 ? 5 : 6
  return formatterFor(digits).format(price)
}
