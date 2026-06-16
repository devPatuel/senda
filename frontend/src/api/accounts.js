// API module for /api/accounts.
import { http } from './http'

/** @typedef {'BANK'|'CASH'} AccountType */

/**
 * @typedef {Object} Account
 * @property {number} id
 * @property {string} name
 * @property {AccountType} type
 * @property {number} balance
 * @property {string} currency
 * @property {boolean} archived
 * @property {string} createdAt
 */

/**
 * Lists the user's accounts. Non-archived only by default.
 * @param {{includeArchived?: boolean}} [options]
 * @returns {Promise<Account[]>}
 */
export function listAccounts({ includeArchived } = {}) {
  const query = includeArchived ? '?includeArchived=true' : ''
  return http.get(`/accounts${query}`)
}

/**
 * Total liquid balance across non-archived accounts.
 * @returns {Promise<{total: number}>}
 */
export function getTotalBalance() {
  return http.get('/accounts/balance')
}

/**
 * @param {{name: string, type: AccountType, balance: number, currency?: string}} data
 * @returns {Promise<Account>}
 */
export function createAccount(data) {
  return http.post('/accounts', data)
}

/**
 * @param {number} id
 * @param {{name: string, type: AccountType, balance: number, currency?: string, archived?: boolean}} data
 * @returns {Promise<Account>}
 */
export function updateAccount(id, data) {
  return http.put(`/accounts/${id}`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeAccount(id) {
  return http.delete(`/accounts/${id}`)
}
