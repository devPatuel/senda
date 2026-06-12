import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { AuthProvider } from './AuthContext'
import { useAuth } from './useAuth'
import { TOKEN_KEY } from '../api/http'

const USER_KEY = 'senda_user'

const fakeUser = { id: 1, email: 'jordi@test.com', name: 'Jordi' }

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderUseAuth() {
  return renderHook(() => useAuth(), {
    wrapper: ({ children }) => <AuthProvider>{children}</AuthProvider>,
  })
}

describe('useAuth', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts unauthenticated when there is no stored session', () => {
    const { result } = renderUseAuth()

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(result.current.token).toBeNull()
  })

  it('login stores token and user in state and localStorage', async () => {
    fetch.mockResolvedValue(jsonResponse({ token: 'jwt-123', user: fakeUser }))
    const { result } = renderUseAuth()

    await act(async () => {
      await result.current.login('jordi@test.com', 'secret123')
    })

    expect(fetch.mock.calls[0][0]).toBe('http://localhost:8080/api/auth/login')
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.token).toBe('jwt-123')
    expect(result.current.user).toEqual(fakeUser)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('jwt-123')
    expect(JSON.parse(localStorage.getItem(USER_KEY))).toEqual(fakeUser)
  })

  it('register stores token and user', async () => {
    fetch.mockResolvedValue(jsonResponse({ token: 'jwt-new', user: fakeUser }, 201))
    const { result } = renderUseAuth()

    await act(async () => {
      await result.current.register('Jordi', 'jordi@test.com', 'secret123')
    })

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/auth/register')
    expect(JSON.parse(options.body)).toEqual({
      name: 'Jordi',
      email: 'jordi@test.com',
      password: 'secret123',
    })
    expect(result.current.isAuthenticated).toBe(true)
    expect(localStorage.getItem(TOKEN_KEY)).toBe('jwt-new')
  })

  it('logout clears state and localStorage', async () => {
    localStorage.setItem(TOKEN_KEY, 'jwt-123')
    localStorage.setItem(USER_KEY, JSON.stringify(fakeUser))
    const { result } = renderUseAuth()

    expect(result.current.isAuthenticated).toBe(true)

    act(() => {
      result.current.logout()
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(USER_KEY)).toBeNull()
  })

  it('a 401 from the API while logged in logs the user out', async () => {
    localStorage.setItem(TOKEN_KEY, 'expired-jwt')
    localStorage.setItem(USER_KEY, JSON.stringify(fakeUser))
    fetch.mockResolvedValue(
      jsonResponse({ status: 401, error: 'Unauthorized', message: 'Token inválido' }, 401),
    )
    const { result } = renderUseAuth()

    const { http } = await import('../api/http')
    await act(async () => {
      await http.get('/transactions').catch(() => {})
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('login propagates ApiError on wrong credentials without storing a session', async () => {
    fetch.mockResolvedValue(
      jsonResponse(
        { status: 401, error: 'Unauthorized', message: 'Email o contraseña incorrectos' },
        401,
      ),
    )
    const { result } = renderUseAuth()

    let error
    await act(async () => {
      error = await result.current.login('jordi@test.com', 'wrong').catch((e) => e)
    })

    expect(error.message).toBe('Email o contraseña incorrectos')
    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })
})
