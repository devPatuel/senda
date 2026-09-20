// API module for /api/transactions.
import { http } from './http'

/** @typedef {'INCOME'|'EXPENSE'} TransactionType */

/**
 * @typedef {Object} Transaction
 * @property {number} id
 * @property {number} categoryId
 * @property {string} categoryName
 * @property {string} categoryColor
 * @property {TransactionType} type
 * @property {number} amount
 * @property {string} date ISO date (YYYY-MM-DD)
 * @property {string|null} description
 * @property {string} createdAt
 */

/**
 * @typedef {Object} TransactionPage
 * @property {Transaction[]} content
 * @property {number} page
 * @property {number} size
 * @property {number} totalElements
 * @property {number} totalPages
 */

/**
 * @typedef {Object} CategorySummary
 * @property {number} categoryId
 * @property {string} categoryName
 * @property {string} categoryColor
 * @property {TransactionType} type
 * @property {number} total
 */

/**
 * @typedef {Object} MonthlySummary
 * @property {number} year
 * @property {number} month
 * @property {number} totalIncome
 * @property {number} totalExpense
 * @property {number} balance
 * @property {CategorySummary[]} byCategory
 */

/**
 * Lists the user's transactions, paginated and ordered by date desc.
 * When `spaceId` is set, lists the couple space's transactions instead.
 * @param {{page?: number, size?: number, from?: string, to?: string, categoryId?: number|string, type?: TransactionType, spaceId?: number}} [options]
 * @returns {Promise<TransactionPage>}
 */
export function listTransactions({ page = 0, size = 20, from, to, categoryId, type, spaceId } = {}) {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  if (categoryId) params.set('categoryId', String(categoryId))
  if (type) params.set('type', type)
  if (spaceId != null) params.set('spaceId', String(spaceId))
  return http.get(`/transactions?${params.toString()}`)
}

/**
 * @param {number} id
 * @returns {Promise<Transaction>}
 */
export function getTransaction(id) {
  return http.get(`/transactions/${id}`)
}

/**
 * Creates a transaction. Include `spaceId` in `data` to record it in a couple space.
 * @param {{categoryId: number, type: TransactionType, amount: number, date: string, description: string|null, spaceId?: number}} data
 * @returns {Promise<Transaction>}
 */
export function createTransaction(data) {
  return http.post('/transactions', data)
}

/**
 * @param {number} id
 * @param {{categoryId: number, type: TransactionType, amount: number, date: string, description: string|null, spaceId?: number}} data
 * @returns {Promise<Transaction>}
 */
export function updateTransaction(id, data) {
  return http.put(`/transactions/${id}`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeTransaction(id) {
  return http.delete(`/transactions/${id}`)
}

/**
 * Monthly summary: totals, balance and per-category breakdown.
 * When `spaceId` is set, summarizes the couple space's transactions instead.
 * @param {number} year
 * @param {number} month 1-based month
 * @param {number} [spaceId]
 * @returns {Promise<MonthlySummary>}
 */
export function getSummary(year, month, spaceId) {
  const params = new URLSearchParams({ year: String(year), month: String(month) })
  if (spaceId != null) params.set('spaceId', String(spaceId))
  return http.get(`/transactions/summary?${params.toString()}`)
}

/**
 * @typedef {Object} MonthlyTrend
 * @property {number} year
 * @property {number} month   1-based month
 * @property {number} income
 * @property {number} expense
 * @property {number} balance
 */

/**
 * @typedef {Object} YearSummary
 * @property {number} year
 * @property {number} totalIncome
 * @property {number} totalExpense
 * @property {number} balance
 * @property {number} monthlyAverageExpense
 * @property {MonthlyTrend[]} months  always twelve, January first
 * @property {CategorySummary[]} byCategory
 */

/**
 * Whole-year summary: totals, the twelve months and the per-category breakdown.
 * When `spaceId` is set, summarizes the couple space's transactions instead.
 * @param {number} year
 * @param {number} [spaceId]
 * @returns {Promise<YearSummary>}
 */
export function getYearSummary(year, spaceId) {
  const params = new URLSearchParams({ year: String(year) })
  if (spaceId != null) params.set('spaceId', String(spaceId))
  return http.get(`/transactions/summary/year?${params.toString()}`)
}

/**
 * Income/expense/balance series for the last `months` months (default 6),
 * oldest first, with zero-filled gaps.
 * @param {number} [months]
 * @returns {Promise<MonthlyTrend[]>}
 */
export function getTrends(months = 6) {
  return http.get(`/transactions/trends?months=${months}`)
}
