// API module for /api/categories.
import { http } from './http'

/** @typedef {'INCOME'|'EXPENSE'} TransactionType */

/**
 * @typedef {Object} Category
 * @property {number} id
 * @property {string} name
 * @property {TransactionType} type
 * @property {string} color
 * @property {boolean} active
 */

/**
 * Lists the user's categories. Active only by default.
 * @param {{type?: TransactionType, includeInactive?: boolean}} [options]
 * @returns {Promise<Category[]>}
 */
export function listCategories({ type, includeInactive } = {}) {
  const params = new URLSearchParams()
  if (type) params.set('type', type)
  if (includeInactive) params.set('includeInactive', 'true')
  const query = params.toString()
  return http.get(`/categories${query ? `?${query}` : ''}`)
}

/**
 * @param {{name: string, type: TransactionType, color: string}} data
 * @returns {Promise<Category>}
 */
export function createCategory(data) {
  return http.post('/categories', data)
}

/**
 * @param {number} id
 * @param {{name: string, type: TransactionType, color: string, active: boolean}} data
 * @returns {Promise<Category>}
 */
export function updateCategory(id, data) {
  return http.put(`/categories/${id}`, data)
}

/**
 * Deletes a category. The backend deactivates it instead when it has
 * transactions, so callers should re-fetch to reflect the actual outcome.
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeCategory(id) {
  return http.delete(`/categories/${id}`)
}

/**
 * @typedef {Object} CategoryBudgetLine
 * @property {number}      id
 * @property {string}      name
 * @property {string}      color
 * @property {number}      balance           - persisted envelope balance (may be negative)
 * @property {number}      spentThisMonth    - expense total for the current calendar month
 * @property {number|null} targetPercentage  - share in the allocation plan, null if not in it
 * @property {number|null} targetAmount       - envelope funding target, null if not set
 */

/**
 * @typedef {Object} CategoryBudget
 * @property {number} totalAccounts
 * @property {number} totalAssigned
 * @property {number} toAssign      - totalAccounts - totalAssigned (may be negative)
 * @property {CategoryBudgetLine[]} categories
 */

/**
 * Budget overview: per expense-category balances + the "to assign" summary.
 * @returns {Promise<CategoryBudget>}
 */
export function getBudget() {
  return http.get('/categories/budget')
}

/**
 * Adjusts an expense category's envelope balance by a delta (may be negative).
 * Returns the refreshed budget.
 * @param {number} id
 * @param {number} amount
 * @returns {Promise<CategoryBudget>}
 */
export function assignToCategory(id, amount) {
  return http.post(`/categories/${id}/assign`, { amount })
}

/**
 * Sets (or clears, with null) an expense category's funding target.
 * Returns the refreshed budget.
 * @param {number} id
 * @param {number|null} targetAmount
 * @returns {Promise<CategoryBudget>}
 */
export function setCategoryTarget(id, targetAmount) {
  return http.post(`/categories/${id}/target`, { targetAmount })
}
