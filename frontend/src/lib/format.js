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
