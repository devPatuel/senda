// API module for /api/shopping.
import { http } from './http'

/** @typedef {'GROCERY'|'WISHLIST'} ShoppingListType */

/**
 * @typedef {Object} ShoppingItem
 * @property {number}              id
 * @property {ShoppingListType}    listType
 * @property {string}              name
 * @property {number|null}         estimatedPrice
 * @property {number|null}         envelopeId
 * @property {string|null}         envelopeName
 * @property {number|null}         envelopeBalance
 * @property {number|null}         priority
 * @property {boolean}             bought
 * @property {boolean|null}        feasible   - null when GROCERY or no envelope/price
 * @property {string|null}         notes
 * @property {string}              createdAt
 */

/**
 * Lists shopping items. When listType is omitted all items are returned.
 * @param {{listType?: ShoppingListType}} [options]
 * @returns {Promise<ShoppingItem[]>}
 */
export function listItems({ listType } = {}) {
  const query = listType ? `?listType=${listType}` : ''
  return http.get(`/shopping/items${query}`)
}

/**
 * Creates a new shopping item.
 * @param {{
 *   listType: ShoppingListType,
 *   name: string,
 *   estimatedPrice?: number,
 *   envelopeId?: number,
 *   priority?: number,
 *   notes?: string
 * }} data
 * @returns {Promise<ShoppingItem>}
 */
export function createItem(data) {
  return http.post('/shopping/items', data)
}

/**
 * Replaces all editable fields of an existing item.
 * Note: listType is immutable after creation.
 * @param {number} id
 * @param {{
 *   listType: ShoppingListType,
 *   name: string,
 *   estimatedPrice?: number,
 *   envelopeId?: number,
 *   priority?: number,
 *   notes?: string
 * }} data
 * @returns {Promise<ShoppingItem>}
 */
export function updateItem(id, data) {
  return http.put(`/shopping/items/${id}`, data)
}

/**
 * Toggles the bought state of an item (optimized for the grocery checkbox).
 * @param {number}  id
 * @param {boolean} bought
 * @returns {Promise<ShoppingItem>}
 */
export function setBought(id, bought) {
  return http.patch(`/shopping/items/${id}/bought`, { bought })
}

/**
 * Deletes a shopping item.
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeItem(id) {
  return http.delete(`/shopping/items/${id}`)
}
