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
 * @param {{page?: number, size?: number, from?: string, to?: string, categoryId?: number|string, type?: TransactionType}} [options]
 * @returns {Promise<TransactionPage>}
 */
export function listTransactions({ page = 0, size = 20, from, to, categoryId, type } = {}) {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  if (categoryId) params.set('categoryId', String(categoryId))
  if (type) params.set('type', type)
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
 * @param {{categoryId: number, type: TransactionType, amount: number, date: string, description: string|null}} data
 * @returns {Promise<Transaction>}
 */
export function createTransaction(data) {
  return http.post('/transactions', data)
}

/**
 * @param {number} id
 * @param {{categoryId: number, type: TransactionType, amount: number, date: string, description: string|null}} data
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
 * @param {number} year
 * @param {number} month 1-based month
 * @returns {Promise<MonthlySummary>}
 */
export function getSummary(year, month) {
  return http.get(`/transactions/summary?year=${year}&month=${month}`)
}
