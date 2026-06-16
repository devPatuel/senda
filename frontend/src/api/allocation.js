// API module for /api/allocation.
import { http } from './http'

/**
 * @typedef {Object} EnvelopeResponse
 * @property {number}  id
 * @property {string}  name
 * @property {number}  percentage
 * @property {number}  position
 * @property {number}  balance
 */

/**
 * @typedef {Object} EnvelopeLineInput
 * @property {number|null} id          - Existing envelope id to preserve its balance; null for new.
 * @property {string}      name
 * @property {number}      percentage
 */

/**
 * @typedef {Object} DistributionLine
 * @property {number}      envelopeId
 * @property {string}      envelopeName
 * @property {number}      percentage
 * @property {number}      allocated
 * @property {number|null} balance     - null when persist=false
 */

/**
 * @typedef {Object} DistributionResponse
 * @property {number}           amount
 * @property {DistributionLine[]} lines
 */

/**
 * Returns the user's envelope plan ordered by position, each with its
 * accumulated balance (0 when never distributed).
 * @returns {Promise<EnvelopeResponse[]>}
 */
export function getEnvelopes() {
  return http.get('/allocation/envelopes')
}

/**
 * Atomically replaces the user's allocation plan. Envelopes with a known
 * {@code id} are updated in-place, preserving their accumulated balance.
 * New envelopes (id null/omitted) start with balance 0.
 * The sum of all percentages must equal exactly 100.
 * @param {EnvelopeLineInput[]} envelopes
 * @returns {Promise<EnvelopeResponse[]>}
 */
export function savePlan(envelopes) {
  return http.put('/allocation/envelopes', { envelopes })
}

/**
 * Distributes an amount across envelopes according to their percentages.
 * When {@code persist} is true, the allocated amounts are accumulated into
 * each envelope's balance.
 * @param {number}  amount
 * @param {boolean} persist
 * @returns {Promise<DistributionResponse>}
 */
export function distribute(amount, persist = false) {
  return http.post('/allocation/distribute', { amount, persist })
}
