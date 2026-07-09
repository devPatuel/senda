// API module for /api/spaces (couple spaces + membership).
import { http } from './http'

/** @typedef {'PENDING'|'ACTIVE'} MemberStatus */

/**
 * @typedef {Object} Space
 * @property {number} id
 * @property {string} name
 * @property {MemberStatus} myStatus
 * @property {number} createdBy
 * @property {string} createdAt
 */

/**
 * @typedef {Object} SpaceMember
 * @property {number} userId
 * @property {string} email
 * @property {string} name
 * @property {MemberStatus} status
 */

/** @returns {Promise<Space[]>} */
export function listSpaces() {
  return http.get('/spaces')
}

/** @param {string} name @returns {Promise<Space>} */
export function createSpace(name) {
  return http.post('/spaces', { name })
}

/** @param {number} spaceId @param {string} email @returns {Promise<SpaceMember>} */
export function addMember(spaceId, email) {
  return http.post(`/spaces/${spaceId}/members`, { email })
}

/** @param {number} spaceId @returns {Promise<null>} */
export function acceptSpace(spaceId) {
  return http.post(`/spaces/${spaceId}/accept`)
}

/** @param {number} spaceId @returns {Promise<null>} */
export function declineSpace(spaceId) {
  return http.post(`/spaces/${spaceId}/decline`)
}

/** @param {number} spaceId @returns {Promise<null>} */
export function leaveSpace(spaceId) {
  return http.delete(`/spaces/${spaceId}/members/me`)
}

/** @param {number} spaceId @returns {Promise<SpaceMember[]>} */
export function listMembers(spaceId) {
  return http.get(`/spaces/${spaceId}/members`)
}
