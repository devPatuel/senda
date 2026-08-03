// API module for /api/habits. BASE_URL already includes /api.
import { http } from './http'

/** @returns {Promise<object[]>} habits scheduled for today, with value, done and streak */
export function getToday() {
  return http.get('/habits/today')
}

export function getHabits(includeArchived = false) {
  return http.get(`/habits?includeArchived=${includeArchived}`)
}

export function createHabit(data) {
  return http.post('/habits', data)
}

export function updateHabit(id, data) {
  return http.put(`/habits/${id}`, data)
}

export function archiveHabit(id, archived = true) {
  return http.patch(`/habits/${id}/archive?archived=${archived}`)
}

export function removeHabit(id) {
  return http.delete(`/habits/${id}`)
}

export function recordEntry(habitId, date, value = null) {
  return http.put(`/habits/${habitId}/entries/${date}`, { value })
}

export function incrementEntry(habitId, date, amount = 1) {
  return http.post(`/habits/${habitId}/entries/${date}/increment`, { amount })
}

export function clearEntry(habitId, date) {
  return http.delete(`/habits/${habitId}/entries/${date}`)
}

export function getHistory(habitId, from, to) {
  return http.get(`/habits/${habitId}/history?from=${from}&to=${to}`)
}
