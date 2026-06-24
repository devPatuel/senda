// API module for /api/category-rules (auto-categorization rules).
import { http } from './http'

/**
 * @typedef {Object} CategoryRule
 * @property {number} id
 * @property {string} matchText
 * @property {number} categoryId
 * @property {string} categoryName
 * @property {string} categoryColor
 */

/** @returns {Promise<CategoryRule[]>} */
export function listRules() {
  return http.get('/category-rules')
}

/**
 * @param {{matchText: string, categoryId: number}} data
 * @returns {Promise<CategoryRule>}
 */
export function createRule(data) {
  return http.post('/category-rules', data)
}

/**
 * @param {number} id
 * @param {{matchText: string, categoryId: number}} data
 * @returns {Promise<CategoryRule>}
 */
export function updateRule(id, data) {
  return http.put(`/category-rules/${id}`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeRule(id) {
  return http.delete(`/category-rules/${id}`)
}
