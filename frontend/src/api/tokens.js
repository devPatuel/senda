// API module for /api/tokens (personal access tokens for the Apple Shortcut).
import { http } from './http'

/**
 * @typedef {Object} Token
 * @property {number} id
 * @property {string} name
 * @property {string} createdAt
 * @property {string|null} lastUsedAt
 * @property {string|null} revokedAt
 */

/** @returns {Promise<Token[]>} */
export function listTokens() {
  return http.get('/tokens')
}

/**
 * Creates a token. The clear value is returned ONCE in the `value` field.
 * @param {string} name
 * @returns {Promise<{id: number, name: string, value: string}>}
 */
export function createToken(name) {
  return http.post('/tokens', { name })
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function revokeToken(id) {
  return http.delete(`/tokens/${id}`)
}
