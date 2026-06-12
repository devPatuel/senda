// Central HTTP client for the Senda API.
// - Prepends the base URL (VITE_API_URL, default http://localhost:8080/api)
// - Attaches the JWT from localStorage as a Bearer token
// - Serializes/parses JSON and normalizes API errors into ApiError
// - On 401 while logged in: clears the token and notifies a registered handler
//   (the auth layer uses it to log out and redirect to /login)

export const TOKEN_KEY = 'senda_token'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

export class ApiError extends Error {
  constructor({ status, error, message, fieldErrors }) {
    super(message || 'Error inesperado')
    this.name = 'ApiError'
    this.status = status
    this.error = error
    this.fieldErrors = fieldErrors || null
  }
}

let unauthorizedHandler = null

// The auth layer registers a callback to react to 401s (logout + redirect).
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const token = getToken()

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(token && { Authorization: `Bearer ${token}` }),
      ...headers,
    },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  })

  if (response.status === 401 && token) {
    localStorage.removeItem(TOKEN_KEY)
    if (unauthorizedHandler) unauthorizedHandler()
  }

  if (!response.ok) {
    let payload = null
    try {
      payload = await response.json()
    } catch {
      // Non-JSON error body (proxy, network appliance...): fall back to status text
    }
    throw new ApiError({
      status: payload?.status ?? response.status,
      error: payload?.error ?? response.statusText,
      message: payload?.message ?? 'Error inesperado. Inténtalo de nuevo.',
      fieldErrors: payload?.fieldErrors,
    })
  }

  if (response.status === 204) return null
  return response.json()
}

export const http = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  delete: (path) => request(path, { method: 'DELETE' }),
}
