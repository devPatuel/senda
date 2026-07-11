// API module for /api/products (food catalog + per-supermarket price history).
import { http } from './http'

/** @typedef {'WEIGHT'|'QUANTITY'} UnitType */

/**
 * @typedef {Object} CurrentPrice
 * @property {string} supermarket
 * @property {number} price
 * @property {string} recordedAt  ISO instant
 */

/**
 * @typedef {Object} Product
 * @property {number}         id
 * @property {string}         name
 * @property {UnitType}       unitType
 * @property {number}         amount
 * @property {string}         unit
 * @property {string}         createdAt
 * @property {CurrentPrice[]} currentPrices  latest price per supermarket, cheapest first
 */

/** @returns {Promise<Product[]>} */
export function listProducts() {
  return http.get('/products')
}

/** @returns {Promise<Product>} */
export function getProduct(id) {
  return http.get(`/products/${id}`)
}

/**
 * @param {{name:string, unitType:UnitType, amount:number, unit:string}} data
 * @returns {Promise<Product>}
 */
export function createProduct(data) {
  return http.post('/products', data)
}

/** @returns {Promise<Product>} */
export function updateProduct(id, data) {
  return http.put(`/products/${id}`, data)
}

/** @returns {Promise<null>} */
export function removeProduct(id) {
  return http.delete(`/products/${id}`)
}

/**
 * Registers a new price entry for a product in a supermarket.
 * @param {number} id
 * @param {{price:number, supermarket:string}} data
 */
export function addPrice(id, data) {
  return http.post(`/products/${id}/prices`, data)
}

/**
 * Full price history of a product (newest first).
 * @param {number} id
 */
export function listPrices(id) {
  return http.get(`/products/${id}/prices`)
}
