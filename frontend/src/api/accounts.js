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
 * When `spaceId` is set, lists the couple space's accounts instead.
 * @param {{includeArchived?: boolean, spaceId?: number}} [options]
 * @returns {Promise<Account[]>}
 */
export function listAccounts({ includeArchived, spaceId } = {}) {
  const params = new URLSearchParams()
  if (includeArchived) params.set('includeArchived', 'true')
  if (spaceId != null) params.set('spaceId', String(spaceId))
  const query = params.toString()
  return http.get(`/accounts${query ? `?${query}` : ''}`)
}

/**
 * Total liquid balance across non-archived accounts.
 * @returns {Promise<{total: number}>}
 */
export function getTotalBalance() {
  return http.get('/accounts/balance')
}

/**
 * Creates an account. Include `spaceId` in `data` to create it in a couple space.
 * @param {{name: string, type: AccountType, balance: number, currency?: string, spaceId?: number}} data
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
