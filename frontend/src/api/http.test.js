import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { http, ApiError, onUnauthorized, TOKEN_KEY } from './http'

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('http client', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    onUnauthorized(null)
  })

  it('attaches the Bearer token from localStorage when present', async () => {
    localStorage.setItem(TOKEN_KEY, 'my-jwt')
    fetch.mockResolvedValue(jsonResponse({ ok: true }))

    await http.get('/categories')

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/categories')
    expect(options.headers.Authorization).toBe('Bearer my-jwt')
  })

  it('does not send Authorization header without a token', async () => {
    fetch.mockResolvedValue(jsonResponse({ ok: true }))

    await http.get('/categories')

    const [, options] = fetch.mock.calls[0]
    expect(options.headers.Authorization).toBeUndefined()
  })

  it('serializes the body as JSON on POST', async () => {
    fetch.mockResolvedValue(jsonResponse({ id: 1 }, 201))

    await http.post('/transactions', { amount: '10.50' })

    const [, options] = fetch.mock.calls[0]
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(options.body)).toEqual({ amount: '10.50' })
  })

  it('parses API errors and throws ApiError with status, message and fieldErrors', async () => {
    fetch.mockResolvedValue(
      jsonResponse(
        {
          status: 400,
          error: 'Bad Request',
          message: 'Datos inválidos',
          fieldErrors: { email: 'El email no es válido' },
        },
        400,
      ),
    )

    const error = await http.post('/auth/register', {}).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.error).toBe('Bad Request')
    expect(error.message).toBe('Datos inválidos')
    expect(error.fieldErrors).toEqual({ email: 'El email no es válido' })
  })

  it('on 401 with a stored token: clears the token and calls the unauthorized handler', async () => {
    localStorage.setItem(TOKEN_KEY, 'expired-jwt')
    const handler = vi.fn()
    onUnauthorized(handler)
    fetch.mockResolvedValue(
      jsonResponse({ status: 401, error: 'Unauthorized', message: 'Token inválido' }, 401),
    )

    await expect(http.get('/transactions')).rejects.toMatchObject({ status: 401 })

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(handler).toHaveBeenCalledOnce()
  })

  it('on 401 without token (e.g. failed login) does not call the unauthorized handler', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    fetch.mockResolvedValue(
      jsonResponse({ status: 401, error: 'Unauthorized', message: 'Credenciales incorrectas' }, 401),
    )

    await expect(http.post('/auth/login', {})).rejects.toMatchObject({ status: 401 })

    expect(handler).not.toHaveBeenCalled()
  })

  it('returns null on 204 No Content', async () => {
    fetch.mockResolvedValue(new Response(null, { status: 204 }))

    const result = await http.delete('/transactions/1')

    expect(result).toBeNull()
  })
})
