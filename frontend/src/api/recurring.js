// API module for /api/recurring (recurring payments — forecast only).
import { http } from './http'

/** @typedef {'WEEKLY'|'MONTHLY'|'QUARTERLY'|'ANNUAL'} RecurringFrequency */

/**
 * @typedef {Object} RecurringPayment
 * @property {number} id
 * @property {string} name
 * @property {number} amount
 * @property {RecurringFrequency} frequency
 * @property {number} categoryId
 * @property {string} categoryName
 * @property {string} categoryColor
 * @property {number} dayOfMonth
 * @property {number|null} month            - 1-12 anchor month for ANNUAL/QUARTERLY, null otherwise
 * @property {number|null} dayOfWeek        - 1 (Mon) - 7 (Sun) for WEEKLY, null otherwise
 * @property {string} nextDueDate           - ISO date (YYYY-MM-DD), derived
 * @property {number} monthlyEquivalent     - normalized monthly cost (weekly*52/12, quarterly/3, annual/12)
 * @property {string|null} endDate          - ISO date cancellation reminder, or null
 * @property {number|null} previousAmount   - amount before the last change, or null
 * @property {number|null} changePct        - % change from previousAmount, or null
 */

/**
 * Lists the user's recurring payments, ordered by next due date.
 * @returns {Promise<RecurringPayment[]>}
 */
export function getRecurring() {
  return http.get('/recurring')
}

/**
 * @param {{name: string, amount: number, frequency: RecurringFrequency, categoryId: number, dayOfMonth: number, month?: number|null, dayOfWeek?: number|null, endDate?: string|null}} data
 * @returns {Promise<RecurringPayment>}
 */
export function createRecurring(data) {
  return http.post('/recurring', data)
}

/**
 * @param {number} id
 * @param {{name: string, amount: number, frequency: RecurringFrequency, categoryId: number, dayOfMonth: number, month?: number|null, dayOfWeek?: number|null, endDate?: string|null}} data
 * @returns {Promise<RecurringPayment>}
 */
export function updateRecurring(id, data) {
  return http.put(`/recurring/${id}`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeRecurring(id) {
  return http.delete(`/recurring/${id}`)
}
