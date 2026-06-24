// API module for /api/allocation.
import { http } from './http'

/**
 * An expense category as seen by the allocation view. `id` is the category id.
 * @typedef {Object} EnvelopeResponse
 * @property {number}  id          - category id
 * @property {string}  name
 * @property {string}  color
 * @property {number}  percentage  - target share in the plan (0 when not assigned)
 * @property {number}  balance
 */

/**
 * @typedef {Object} EnvelopeLineInput
 * @property {number} categoryId
 * @property {number} percentage
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
 * Returns the user's active expense categories ordered by name, each with its
 * target percentage (0 when not in the plan) and current envelope balance.
 * @returns {Promise<EnvelopeResponse[]>}
 */
export function getEnvelopes() {
  return http.get('/allocation/envelopes')
}

/**
 * Sets the allocation plan: the given categories receive their percentage and
 * every other expense category is cleared from the plan. Percentages must sum
 * to exactly 100.
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
