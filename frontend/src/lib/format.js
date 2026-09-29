// Formatting helpers shared by all screens. Amounts are ALWAYS formatted
// through Intl.NumberFormat es-ES with EUR, never concatenated by hand.

const currencyFormatter = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
})

const monthFormatter = new Intl.DateTimeFormat('es-ES', {
  month: 'long',
  year: 'numeric',
})

const dateFormatter = new Intl.DateTimeFormat('es-ES', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

/**
 * @param {number|string} value
 * @returns {string} e.g. "1.234,56 €"
 */
export function formatCurrency(value) {
  return currencyFormatter.format(typeof value === 'string' ? Number(value) : value)
}

/**
 * @param {number} year
 * @param {number} month 1-based month
 * @returns {string} e.g. "junio de 2026"
 */
export function formatMonthLabel(year, month) {
  return monthFormatter.format(new Date(year, month - 1, 1))
}

/**
 * @param {string} isoDate YYYY-MM-DD
 * @returns {string} e.g. "12 jun 2026"
 */
export function formatDate(isoDate) {
  // Append T00:00:00 so the date is parsed in the local timezone, not UTC.
  return dateFormatter.format(new Date(`${isoDate}T00:00:00`))
}

/**
 * @returns {string} today's local date as YYYY-MM-DD
 */
export function todayISO() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/**
 * Quantity with sensible decimals: 4 significant digits below 1, up to 2 decimals
 * below 100 and up to 1 above (e.g. "0,007756", "97,1", "1951,5").
 * @param {number|string} value
 * @returns {string}
 */
export function formatQuantity(value) {
  const n = Number(value)
  const abs = Math.abs(n)
  const options =
    abs > 0 && abs < 1 ? { maximumSignificantDigits: 4 } : { maximumFractionDigits: abs < 100 ? 2 : 1 }
  return new Intl.NumberFormat('es-ES', options).format(n)
}

/**
 * Unit price in EUR: 2 decimals from 1 €, 4 significant digits below (e.g. "0,0633 €").
 * @param {number|string} value
 * @returns {string}
 */
export function formatPrice(value) {
  const n = Number(value)
  if (n === 0 || Math.abs(n) >= 1) return formatCurrency(n)
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency: 'EUR',
    maximumSignificantDigits: 4,
  }).format(n)
}

/**
 * Percentage with an explicit sign for gains, e.g. "+12,3 %".
 * @param {number|string} value
 * @returns {string}
 */
export function formatPercent(value) {
  const n = Number(value)
  const text = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 }).format(n)
  return `${n > 0 ? '+' : ''}${text} %`
}
