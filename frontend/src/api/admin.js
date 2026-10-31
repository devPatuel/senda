// API module for /api/admin (account administration) and /api/users/me.
import { http } from './http'

/**
 * @typedef {Object} AdminUser
 * @property {number} id
 * @property {string} email
 * @property {string} name
 * @property {string} createdAt
 * @property {boolean} admin
 */

/** @returns {Promise<{id: number, email: string, name: string, admin: boolean}>} */
export function getMe() {
  return http.get('/users/me')
}

/** @returns {Promise<AdminUser[]>} */
export function listUsers() {
  return http.get('/admin/users')
}

/** @param {number} userId @returns {Promise<null>} */
export function deleteUser(userId) {
  return http.delete(`/admin/users/${userId}`)
}

/** @param {number} userId @param {string} newPassword @returns {Promise<null>} */
export function resetUserPassword(userId, newPassword) {
  return http.post(`/admin/users/${userId}/password`, { newPassword })
}
