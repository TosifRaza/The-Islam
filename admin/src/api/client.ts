/** Approved success envelope {"data": ..., "meta": ...}. */
export interface Envelope<T> {
  data: T
  meta?: PageMeta
}

export interface PageMeta {
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** RFC 7807 problem+json with the approved stable error codes. */
export interface ProblemBody {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: string
  instance?: string
}

export class ApiError extends Error {
  code: string
  status: number
  detail: string

  constructor(status: number, body: ProblemBody) {
    super(body.detail || body.title || `HTTP ${status}`)
    this.code = body.code || `HTTP-${status}`
    this.status = status
    this.detail = body.detail || body.title || `HTTP ${status}`
  }
}

const BASE = '/api/v1'
const ACCESS_KEY = 'ikp.admin.access'
const REFRESH_KEY = 'ikp.admin.refresh'

export function storeTokens(access: string, refresh: string) {
  sessionStorage.setItem(ACCESS_KEY, access)
  sessionStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens() {
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}

export function accessToken(): string | null {
  return sessionStorage.getItem(ACCESS_KEY)
}

export function refreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_KEY)
}

/** One single-flight refresh so concurrent 401s only rotate the token once. */
let refreshInFlight: Promise<string | null> | null = null

async function rotateTokens(): Promise<string | null> {
  const refresh = refreshToken()
  if (!refresh) return null
  try {
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: refresh }),
    })
    if (!res.ok) return null
    const body = (await res.json()) as Envelope<{
      accessToken: string
      refreshToken: string
      expiresIn: number
    }>
    storeTokens(body.data.accessToken, body.data.refreshToken)
    return body.data.accessToken
  } catch {
    return null
  }
}

/** Rotates the token pair once for many concurrent 401s; returns the new access token. */
export function rotateOnce(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = rotateTokens().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

export interface ApiOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  raw?: boolean // return the full envelope instead of data
  skipAuth?: boolean
  skipRefresh?: boolean
}

/**
 * Central API client: envelope unwrapping, bearer auth, single-flight token
 * refresh on 401 and problem+json error translation (approved API conventions).
 */
export async function api<T = unknown>(path: string, opts: ApiOptions = {}): Promise<Envelope<T>> {
  const doFetch = (token: string | null) =>
    fetch(`${BASE}${path}`, {
      method: opts.method || 'GET',
      headers: {
        ...(opts.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: 'application/json',
      },
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    })

  let res = await doFetch(opts.skipAuth ? null : accessToken())
  if (res.status === 401 && !opts.skipAuth && !opts.skipRefresh) {
    const next = await rotateOnce()
    if (next) {
      res = await doFetch(next)
    }
  }
  if (res.status === 204) return { data: undefined as T }

  const text = await res.text()
  const parsed = text ? (JSON.parse(text) as unknown) : {}
  const isProblem = (p: unknown): p is ProblemBody =>
    typeof p === 'object' && p !== null && ('title' in p || 'code' in p || 'status' in p)
  if (!res.ok) {
    if (isProblem(parsed)) throw new ApiError(res.status, parsed)
    throw new ApiError(res.status, { status: res.status, title: `HTTP ${res.status}` })
  }
  if (isProblem(parsed) && !('data' in parsed)) {
    // safety net: 2xx problem payloads are contract violations
    throw new ApiError(res.status, parsed)
  }
  return parsed as Envelope<T>
}

/** Convenience: returns only the data member. */
export async function apiData<T = unknown>(path: string, opts: ApiOptions = {}): Promise<T> {
  return (await api<T>(path, opts)).data
}

/** Multipart upload with progress (XMLHttpRequest gives upload progress events). */
export function apiUpload<T = unknown>(
  path: string,
  form: FormData,
  onProgress?: (pct: number) => void,
): Promise<Envelope<T>> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${BASE}${path}`)
    xhr.setRequestHeader('Accept', 'application/json')
    const token = accessToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100))
    })
    xhr.addEventListener('load', () => {
      try {
        const body = JSON.parse(xhr.responseText || '{}')
        if (xhr.status >= 200 && xhr.status < 300) resolve(body as Envelope<T>)
        else
          reject(
            new ApiError(xhr.status, {
              status: xhr.status,
              code: body.code,
              title: body.title,
              detail: body.detail,
            }),
          )
      } catch {
        reject(new ApiError(xhr.status, { status: xhr.status, title: `HTTP ${xhr.status}` }))
      }
    })
    xhr.addEventListener('error', () =>
      reject(new ApiError(0, { status: 0, title: 'Network error', detail: 'upload failed' })),
    )
    xhr.send(form)
  })
}
