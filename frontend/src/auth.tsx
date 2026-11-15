import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, session, setUnauthorizedHandler } from './api'
import type { Profile } from './types'

interface LoginResponse {
  token: string
  expiresAt: string
  profile: Profile
}

interface AuthState {
  profile: Profile | null
  /** false finché non abbiamo riletto il profilo dal server (ruolo e permessi possono essere cambiati). */
  ready: boolean
  /** L'utente ha questo permesso? (i permessi arrivano dal server, che li impone comunque) */
  can: (permission: string) => boolean
  /** L'utente ha almeno uno di questi permessi? */
  canAny: (...permissions: string[]) => boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [profile, setProfile] = useState<Profile | null>(() => (session.token() ? session.profile() : null))
  const [ready, setReady] = useState(() => !session.token())

  const logout = useCallback(() => {
    session.clear()
    setProfile(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => setProfile(null))
  }, [])

  const refresh = useCallback(async () => {
    const p = await api.get<Profile>('/auth/me')
    session.saveProfile(p)
    setProfile(p)
  }, [])

  // All'avvio, se c'è una sessione salvata, rileggo ruolo e permessi dal server
  useEffect(() => {
    if (!session.token()) return
    refresh()
      .catch(() => {})
      .finally(() => setReady(true))
  }, [refresh])

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.post<LoginResponse>('/auth/login', { username, password })
    session.save(res.token, res.profile)
    setProfile(res.profile)
    setReady(true)
  }, [])

  const value = useMemo<AuthState>(() => {
    const perms = new Set(profile?.permissions ?? [])
    return {
      profile,
      ready,
      can: (p) => perms.has(p),
      canAny: (...ps) => ps.some((p) => perms.has(p)),
      login,
      logout,
      refresh,
    }
  }, [profile, ready, login, logout, refresh])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth fuori da AuthProvider')
  return ctx
}
