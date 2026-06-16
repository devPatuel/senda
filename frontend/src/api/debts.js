// API module for /api/debts.
import { http } from './http'

/** @typedef {'THEY_OWE_ME'|'I_OWE'} DebtDirection */

/**
 * @typedef {Object} Debt
 * @property {number} id
 * @property {DebtDirection} direction
 * @property {string} counterparty
 * @property {string} concept
 * @property {number} originalAmount
 * @property {number} paidAmount
 * @property {number} pendingAmount
 * @property {boolean} settled
 * @property {string} date        - YYYY-MM-DD
 * @property {string} createdAt
 */

/**
 * @typedef {Object} DebtPayment
 * @property {number} id
 * @property {number} debtId
 * @property {number} amount
 * @property {string} date   - YYYY-MM-DD
 * @property {string|null} note
 * @property {string} createdAt
 */

/**
 * Lists all debts of the authenticated user.
 * @param {{direction?: DebtDirection, settled?: boolean}} [filters]
 * @returns {Promise<Debt[]>}
 */
export function listDebts({ direction, settled } = {}) {
  const params = new URLSearchParams()
  if (direction != null) params.set('direction', direction)
  if (settled != null) params.set('settled', String(settled))
  const query = params.size > 0 ? `?${params}` : ''
  return http.get(`/debts${query}`)
}

/**
 * @param {number} id
 * @returns {Promise<Debt>}
 */
export function getDebt(id) {
  return http.get(`/debts/${id}`)
}

/**
 * @param {{direction: DebtDirection, counterparty: string, concept: string, originalAmount: number, date: string}} data
 * @returns {Promise<Debt>}
 */
export function createDebt(data) {
  return http.post('/debts', data)
}

/**
 * @param {number} id
 * @param {{direction: DebtDirection, counterparty: string, concept: string, originalAmount: number, date: string}} data
 * @returns {Promise<Debt>}
 */
export function updateDebt(id, data) {
  return http.put(`/debts/${id}`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeDebt(id) {
  return http.delete(`/debts/${id}`)
}

/**
 * @param {number} debtId
 * @returns {Promise<DebtPayment[]>}
 */
export function listPayments(debtId) {
  return http.get(`/debts/${debtId}/payments`)
}

/**
 * @param {number} debtId
 * @param {{amount: number, date: string, note?: string}} data
 * @returns {Promise<DebtPayment>}
 */
export function addPayment(debtId, data) {
  return http.post(`/debts/${debtId}/payments`, data)
}

/**
 * @param {number} debtId
 * @param {number} paymentId
 * @returns {Promise<null>}
 */
export function removePayment(debtId, paymentId) {
  return http.delete(`/debts/${debtId}/payments/${paymentId}`)
}
