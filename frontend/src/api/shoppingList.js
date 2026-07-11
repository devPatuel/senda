// api/shoppingList.js — /api/shopping-list (F10). BASE_URL already includes /api.
import { http } from './http'

export function getShoppingList() {
  return http.get('/shopping-list')
}
export function addToList(data) {           // { productId, quantity? }
  return http.post('/shopping-list', data)
}
export function updateListItem(id, data) {  // { quantity?, checked? }
  return http.put(`/shopping-list/${id}`, data)
}
export function removeListItem(id) {
  return http.delete(`/shopping-list/${id}`)
}
export function clearChecked() {
  return http.delete('/shopping-list/checked')
}
