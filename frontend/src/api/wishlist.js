// API module for /api/wishlist (F9). BASE_URL already includes /api.
import { http } from './http'

/** @returns {Promise<{items: object[], total: number}>} */
export function getWishlist() {
  return http.get('/wishlist')
}

export function createWishItem(data) {
  return http.post('/wishlist', data)
}

export function updateWishItem(id, data) {
  return http.put(`/wishlist/${id}`, data)
}

export function removeWishItem(id) {
  return http.delete(`/wishlist/${id}`)
}
