import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, api, post, setSessionLostHandler, tokenStore } from '../lib/api'

export interface UserProfile {
  id: string
  username: string
  displayName: string
  email?: string | null
  phone?: string | null
  role: string
  status: string
  permissions: string[]
  staff: boolean
  student: boolean
  parent: boolean
  studentId?: string | null
  mustChangePassword: boolean
  lastLoginAt?: string | null
}

interface TokenResponse {
  tokenType: string
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
  user: UserProfile
}

interface AuthValue {
  user: UserProfile | null
  /** True until the first profile load settles, so routes do not flash the login screen. */
  initialising: boolean
  signIn: (loginId: string, password: string) => Promise<UserProfile>
  signOut: (options?: { passwordChanged?: boolean }) => Promise<void>
  /**
   * Set when a password change signs the user out. The sign-in screen has to say why, and it
   * cannot read it from the URL: signing out drops the route the notice was about to use.
   */
  passwordChanged: boolean
  can: (...permissions: string[]) => boolean
  canAny: (...permissions: string[]) => boolean
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [initialising, setInitialising] = useState(true)
  const [passwordChanged, setPasswordChanged] = useState(false)

  const signOut = useCallback(async (options?: { passwordChanged?: boolean }) => {
    const refreshToken = tokenStore.refresh()
    try {
      await post('/api/v1/auth/logout', refreshToken ? { refreshToken } : {})
    } catch {
      // Signing out locally matters more than the server acknowledging it.
    }
    tokenStore.clear()
    if (options?.passwordChanged) setPasswordChanged(true)
    setUser(null)
  }, [])

  // The API client calls this when a session cannot be recovered, so a hard 401 anywhere
  // in the app returns the user to the login screen instead of leaving dead queries behind.
  useEffect(() => {
    setSessionLostHandler(() => setUser(null))
    return () => setSessionLostHandler(null)
  }, [])

  // Restore the session on load: trust the stored refresh token, not a cached profile.
  useEffect(() => {
    let cancelled = false
    const restore = async () => {
      if (!tokenStore.refresh()) {
        if (!cancelled) setInitialising(false)
        return
      }
      try {
        const profile = await api<UserProfile>('/api/v1/auth/me')
        if (!cancelled) setUser(profile)
      } catch {
        if (!cancelled) tokenStore.clear()
      } finally {
        if (!cancelled) setInitialising(false)
      }
    }
    void restore()
    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (loginId: string, password: string) => {
    const tokens = await post<TokenResponse>('/api/v1/auth/login', { loginId, password })
    tokenStore.save(tokens.accessToken, tokens.refreshToken)
    setPasswordChanged(false)
    setUser(tokens.user)
    return tokens.user
  }, [])

  const value = useMemo<AuthValue>(() => {
    const granted = new Set(user?.permissions ?? [])
    return {
      user,
      initialising,
      signIn,
      signOut,
      passwordChanged,
      can: (...permissions) => permissions.every((p) => granted.has(p)),
      canAny: (...permissions) => permissions.some((p) => granted.has(p)),
    }
  }, [user, initialising, signIn, signOut, passwordChanged])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

/** Turns any thrown value into a sentence a person can act on. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return 'Something went wrong. Please try again.'
}