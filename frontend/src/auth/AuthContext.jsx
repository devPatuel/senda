import { createContext, useCallback, useEffect, useMemo, useState } from 'react'
import { http, onUnauthorized, TOKEN_KEY } from '../api/http'

const USER_KEY = 'senda_user'

const AuthContext = createContext(null)

function readStoredUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [user, setUser] = useState(readStoredUser)

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setToken(null)
    setUser(null)
  }, [])

  // When the API returns 401 (expired/invalid token) the http client
  // clears the token and we reset auth state here. ProtectedRoute then
  // redirects to /login.
  useEffect(() => {
    onUnauthorized(logout)
    return () => onUnauthorized(null)
  }, [logout])

  const applySession = useCallback(({ token: newToken, user: newUser }) => {
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
    setToken(newToken)
    setUser(newUser)
  }, [])

  const login = useCallback(
    async (email, password) => {
      const data = await http.post('/auth/login', { email, password })
      applySession(data)
      return data.user
    },
    [applySession],
  )

  const register = useCallback(
    async (name, email, password) => {
      const data = await http.post('/auth/register', { name, email, password })
      applySession(data)
      return data.user
    },
    [applySession],
  )

  // The API closes every earlier session and answers with a fresh one for this device
  const changePassword = useCallback(
    async (currentPassword, newPassword) => {
      const data = await http.post('/auth/password', { currentPassword, newPassword })
      applySession(data)
    },
    [applySession],
  )

  const value = useMemo(
    () => ({
      user,
      token,
      isAuthenticated: Boolean(token),
      login,
      register,
      changePassword,
      logout,
    }),
    [user, token, login, register, changePassword, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthContext
