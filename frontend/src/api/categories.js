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
