// API module for /api/alerts (heuristic spending alerts for the home screen).
import { http } from './http'

/**
 * @typedef {Object} AntExpenseAlert
 * @property {number} categoryId
 * @property {string} categoryName
 * @property {string} categoryColor
 * @property {number} count
 * @property {number} total
 */

/**
 * @typedef {Object} ForgottenSubscriptionAlert
 * @property {number} recurringId
 * @property {string} name
 * @property {number} categoryId
 * @property {string} categoryName
 * @property {string} categoryColor
 */

/**
 * @typedef {Object} Alerts
 * @property {AntExpenseAlert[]} antExpenses
 * @property {ForgottenSubscriptionAlert[]} forgottenSubscriptions
 */

/**
 * @returns {Promise<Alerts>}
 */
export function getAlerts() {
  return http.get('/alerts')
}
