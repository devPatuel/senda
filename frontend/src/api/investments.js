// API module for /api/investments (asset classes, holdings, buys, lots, prices,
// price refresh and NFTs).
import { http } from './http'

/** @typedef {'CRYPTO'|'METAL'|'FUND'|'MANUAL'} PricingSource */

/**
 * @typedef {Object} AssetClass
 * @property {number} id
 * @property {string} name
 * @property {PricingSource} pricingSource
 * @property {string} createdAt
 */

/**
 * @typedef {Object} Holding
 * @property {number} id
 * @property {number} assetClassId
 * @property {string} assetClassName
 * @property {PricingSource} pricingSource
 * @property {string} symbol
 * @property {string} name
 * @property {number} quantity
 * @property {number} avgCost
 * @property {number|null} currentPrice
 * @property {string|null} lastPricedAt
 * @property {number|null} marketValue
 * @property {number|null} pnl
 * @property {number} cost
 */

/**
 * @typedef {Object} HoldingLot
 * @property {number} id
 * @property {number} quantity
 * @property {number} unitPrice
 * @property {string} date
 * @property {string} createdAt
 */

/**
 * @typedef {Object} Nft
 * @property {number} id
 * @property {string} name
 * @property {string|null} collection
 * @property {string} buyCryptoSymbol
 * @property {number} buyCryptoAmount
 * @property {number} fiatValueAtPurchase
 * @property {number} ourCurrentValue
 * @property {string|null} utility
 * @property {number|null} currentPurchaseValue
 * @property {string} createdAt
 */

// --- Asset classes ---

/** @returns {Promise<AssetClass[]>} */
export function listAssetClasses() {
  return http.get('/investments/asset-classes')
}

/**
 * @param {{name: string, pricingSource: PricingSource}} data
 * @returns {Promise<AssetClass>}
 */
export function createAssetClass(data) {
  return http.post('/investments/asset-classes', data)
}

/**
 * @param {number} id
 * @param {{name: string, pricingSource: PricingSource}} data
 * @returns {Promise<AssetClass>}
 */
export function updateAssetClass(id, data) {
  return http.put(`/investments/asset-classes/${id}`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeAssetClass(id) {
  return http.delete(`/investments/asset-classes/${id}`)
}

// --- Holdings ---

/**
 * @param {{assetClassId?: number}} [options]
 * @returns {Promise<Holding[]>}
 */
export function listHoldings({ assetClassId } = {}) {
  const query = assetClassId != null ? `?assetClassId=${assetClassId}` : ''
  return http.get(`/investments/holdings${query}`)
}

/**
 * @param {number} id
 * @returns {Promise<Holding>}
 */
export function getHolding(id) {
  return http.get(`/investments/holdings/${id}`)
}

/**
 * @param {{assetClassId: number, symbol: string, name: string, quantity: number, avgCost: number}} data
 * @returns {Promise<Holding>}
 */
export function createHolding(data) {
  return http.post('/investments/holdings', data)
}

/**
 * Adds a buy ("lot") to an existing holding; the backend recomputes quantity and
 * weighted-average cost.
 * @param {number} id
 * @param {{quantity: number, unitPrice: number, date: string}} data
 * @returns {Promise<Holding>}
 */
export function addBuy(id, data) {
  return http.post(`/investments/holdings/${id}/buys`, data)
}

/**
 * @param {number} id
 * @returns {Promise<HoldingLot[]>}
 */
export function listLots(id) {
  return http.get(`/investments/holdings/${id}/lots`)
}

/**
 * Sets the holding's current price by hand (for MANUAL/FUND/METAL sources).
 * @param {number} id
 * @param {{currentPrice: number}} data
 * @returns {Promise<Holding>}
 */
export function setHoldingPrice(id, data) {
  return http.put(`/investments/holdings/${id}/price`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeHolding(id) {
  return http.delete(`/investments/holdings/${id}`)
}

/**
 * Refreshes CRYPTO holdings' prices and returns the full holdings list.
 * @returns {Promise<Holding[]>}
 */
export function refreshPrices() {
  return http.post('/investments/refresh-prices')
}

// --- NFTs ---

/** @returns {Promise<Nft[]>} */
export function listNfts() {
  return http.get('/investments/nfts')
}

/**
 * @param {{name: string, collection?: string, buyCryptoSymbol: string, buyCryptoAmount: number,
 *   fiatValueAtPurchase: number, ourCurrentValue: number, utility?: string}} data
 * @returns {Promise<Nft>}
 */
export function createNft(data) {
  return http.post('/investments/nfts', data)
}

/**
 * @param {number} id
 * @param {{name: string, collection?: string, buyCryptoSymbol: string, buyCryptoAmount: number,
 *   fiatValueAtPurchase: number, ourCurrentValue: number, utility?: string}} data
 * @returns {Promise<Nft>}
 */
export function updateNft(id, data) {
  return http.put(`/investments/nfts/${id}`, data)
}

/**
 * @param {number} id
 * @returns {Promise<null>}
 */
export function removeNft(id) {
  return http.delete(`/investments/nfts/${id}`)
}
