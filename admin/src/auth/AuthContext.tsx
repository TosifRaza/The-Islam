import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, apiData, clearTokens, refreshToken as storedRefresh, storeTokens } from '../api/client'
import type { MeInfo, SessionInfo, TokenPair } from '../api/types'

/**
 * Admin auth context on the approved M1/M2 backend foundation:
 * POST /auth/login (Argon2id server-side), 15-min access token, rotating
 * refresh token with reuse detection (AuthService). OD-04 stays open — this
 * is the email+password baseline only; no OTP/social provider is chosen.
 */
interface AuthState {
  me: MeInfo | null
  loading: boolean // initial /me load
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshMe: () => Promise<void>
  hasPermission: (code: string) => boolean
  hasAnyRole: (...roles: string[]) => boolean
  listSessions: () => Promise<SessionInfo[]>
  revokeSession: (id: number) => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<MeInfo | null>(null)
  const [loading, setLoading] = useState(true)

  const loadMe = useCallback(async () => {
    if (!storedRefresh()) {
      setMe(null)
      return false
    }
    try {
      const info = await apiData<MeInfo>('/auth/me')
      setMe(info)
      return true
    } catch {
      clearTokens()
      setMe(null)
      return false
    }
  }, [])

  useEffect(() => {
    loadMe().finally(() => setLoading(false))
  }, [loadMe])

  const login = useCallback(
    async (email: string, password: string) => {
      const pair = await apiData<TokenPair>('/auth/login', {
        method: 'POST',
        body: { email, password, deviceName: 'web-admin' },
        skipAuth: true,
      })
      storeTokens(pair.accessToken, pair.refreshToken)
      const info = await apiData<MeInfo>('/auth/me')
      setMe(info)
    },
    [],
  )

  const logout = useCallback(async () => {
    try {
      await api('/auth/logout', { method: 'POST', body: {} })
    } catch {
      // the session is dropped client-side regardless
    }
    clearTokens()
    setMe(null)
  }, [])

  const hasPermission = useCallback((code: string) => !!me?.permissions?.includes(code), [me])

  const hasAnyRole = useCallback(
    (...roles: string[]) => !!me?.roles?.some((r) => roles.includes(r)),
    [me],
  )

  const listSessions = useCallback(() => apiData<SessionInfo[]>('/auth/sessions'), [])

  const revokeSession = useCallback(async (id: number) => {
    await api(`/auth/sessions/${id}`, { method: 'DELETE' })
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      me,
      loading,
      login,
      logout,
      refreshMe: async () => {
        await loadMe()
      },
      hasPermission,
      hasAnyRole,
      listSessions,
      revokeSession,
    }),
    [me, loading, login, logout, loadMe, hasPermission, hasAnyRole, listSessions, revokeSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
