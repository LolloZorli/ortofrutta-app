import type { Profile } from './types'

const TOKEN_KEY = 'ortofrutta.token'
const PROFILE_KEY = 'ortofrutta.profile'
const COMPANY_KEY = 'ortofrutta.company'

export const companySession = {
  slug: () => localStorage.getItem(COMPANY_KEY) ?? '',
  select: (slug: string) => {
    const value = slug.trim().toLowerCase()
    if (value) localStorage.setItem(COMPANY_KEY, value)
    else localStorage.removeItem(COMPANY_KEY)
  },
}

export const session = {
  token: () => localStorage.getItem(TOKEN_KEY),
  profile: (): Profile | null => {
    try {
      const raw = localStorage.getItem(PROFILE_KEY)
      return raw ? (JSON.parse(raw) as Profile) : null
    } catch {
      return null
    }
  },
  save: (token: string, profile: Profile) => {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
  },
  saveProfile: (profile: Profile) => localStorage.setItem(PROFILE_KEY, JSON.stringify(profile)),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(PROFILE_KEY)
  },
}

export class ApiError extends Error {
  status: number
  fieldErrors?: Record<string, string>

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

let onUnauthorized: () => void = () => {}
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = session.token()
  if (token) headers.Authorization = `Bearer ${token}`
  if (path !== '/companies' && path !== '/companies/register') {
    const companySlug = companySession.slug()
    if (companySlug) headers['X-Company-Slug'] = companySlug
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}/api${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Server non raggiungibile')
  }

  if (res.status === 401 && path !== '/auth/login') {
    session.clear()
    onUnauthorized()
    throw new ApiError(401, 'Sessione scaduta, accedi di nuovo')
  }

  if (!res.ok) {
    let message = res.status === 403 ? 'Non hai i permessi per questa operazione' : `Errore ${res.status}`
    let fieldErrors: Record<string, string> | undefined
    try {
      const problem = await res.json()
      message = problem.detail ?? problem.title ?? message
      fieldErrors = problem.errors
    } catch {
      /* risposta senza corpo JSON */
    }
    throw new ApiError(res.status, message, fieldErrors)
  }

  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T,>(path: string) => request<T>('GET', path),
  post: <T,>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T,>(path: string, body: unknown) => request<T>('PUT', path, body),
  del: (path: string) => request<void>('DELETE', path),
  download: async (path: string, filename: string) => {
    const headers: Record<string, string> = {}
    const token = session.token()
    if (token) headers.Authorization = `Bearer ${token}`
    const companySlug = companySession.slug()
    if (companySlug) headers['X-Company-Slug'] = companySlug
    const res = await fetch(`${API_BASE_URL}/api${path}`, { headers })
    if (!res.ok) throw new ApiError(res.status, res.status === 403
      ? 'Non hai i permessi per questa operazione'
      : `Errore ${res.status}`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  },
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : 'Errore imprevisto'
}
