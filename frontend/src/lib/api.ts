/**
 * The one place that knows how to talk to the backend.
 *
 * Two backend conventions shape everything here. Success is always an envelope of
 * `{ data, message }`, and failure is always an `ErrorResponse` carrying a stable `code`
 * plus optional per-field messages. Nothing else in the app is allowed to look at a raw
 * fetch response, so error handling stays consistent across every screen.
 */

/** Spring Data's page envelope, unwrapped from the `data` field by {@link api}. */
export interface PageResponse<T> {
  data: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  empty: boolean
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  code: string
  message: string
  fieldErrors?: Record<string, string> | null
  path?: string
  requestId?: string
}

/** An error carrying the backend's own code, so callers can branch on meaning not on wording. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  readonly requestId?: string

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = body.status
    this.code = body.code
    this.fieldErrors = body.fieldErrors ?? {}
    this.requestId = body.requestId
  }

  /** True when the caller simply needs to sign in again. */
  get isUnauthenticated(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }
}

const ACCESS_KEY = 'erp.accessToken'
const REFRESH_KEY = 'erp.refreshToken'

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  save: (access: string, refresh: string) => {
    localStorage.setItem(ACCESS_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

/**
 * Called when a session cannot be recovered, so the app can return to the login screen
 * instead of leaving every query in a permanent error state.
 */
let onSessionLost: (() => void) | null = null
export function setSessionLostHandler(handler: (() => void) | null): void {
  onSessionLost = handler
}

async function send(path: string, init: RequestInit, method: string): Promise<Response> {
  const headers = new Headers(init.headers)
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')
  const token = tokenStore.access()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  // FormData must keep the browser's own content type so the boundary is set for us.
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  void method
  return fetch(path, { ...init, headers, credentials: 'same-origin' })
}

/** A single in-flight refresh, so a burst of 401s rotates the token once, not once per request. */
let refreshing: Promise<boolean> | null = null

function refreshSession(): Promise<boolean> {
  if (refreshing) return refreshing
  const refreshToken = tokenStore.refresh()
  if (!refreshToken) return Promise.resolve(false)

  refreshing = (async () => {
    try {
      const tokens = await send(
        '/api/v1/auth/refresh',
        { method: 'POST', body: JSON.stringify({ refreshToken }) },
        'POST',
      )
      if (!tokens.ok) return false
      const payload = await tokens.json()
      tokenStore.save(payload.data.accessToken, payload.data.refreshToken)
      return true
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

export type QueryParams = Record<string, string | number | boolean | undefined | null>

/** Empty values are dropped rather than sent, so an untouched filter is not a filter. */
function appendQuery(url: URL, query?: QueryParams): void {
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  }
}

export async function api<T>(
  path: string,
  init: RequestInit & { query?: QueryParams } = {},
): Promise<T> {
  const { query, ...rest } = init
  const url = new URL(path, window.location.origin)
  appendQuery(url, query)

  const token = tokenStore.access()
  const headers = new Headers(rest.headers)
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  // FormData must keep the browser's own content type so the boundary is set for us.
  if (rest.body && !(rest.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response = await send(url.pathname + url.search, { ...rest, headers }, rest.method ?? 'GET')

  // One silent retry after refreshing: an expired access token is not a user error.
  if (response.status === 401 && !url.pathname.includes('/auth/')) {
    if (await refreshSession()) {
      const retryHeaders = new Headers(headers)
      const freshToken = tokenStore.access()
      if (freshToken) retryHeaders.set('Authorization', `Bearer ${freshToken}`)
      response = await send(url.pathname + url.search, { ...rest, headers: retryHeaders }, rest.method ?? 'GET')
    } else {
      tokenStore.clear()
      onSessionLost?.()
    }
  }

  if (response.status === 204) return undefined as T

  const text = await response.text()
  const payload = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new ApiError(
      payload ?? {
        timestamp: new Date().toISOString(),
        status: response.status,
        code: 'UNEXPECTED',
        message: 'The server could not be reached. Check your connection and try again.',
      },
    )
  }

  return (payload?.data ?? null) as T
}

/** A download is not JSON, so it bypasses the envelope logic above. */
/** {@link apiDownload} also takes a query string, so exports match the `api` call shape. */
export interface DownloadOptions extends Omit<RequestInit, 'body'> {
  query?: QueryParams
}

export async function apiDownload(path: string, options: DownloadOptions = {}): Promise<Blob> {
  const { query, ...init } = options
  const url = new URL(path, window.location.origin)
  appendQuery(url, query)

  const headers = new Headers(init.headers)
  const token = tokenStore.access()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response = await send(url.pathname + url.search, { ...init, headers }, init.method ?? 'GET')
  if (response.status === 401 && (await refreshSession())) {
    const retryHeaders = new Headers(headers)
    const freshToken = tokenStore.access()
    if (freshToken) retryHeaders.set('Authorization', `Bearer ${freshToken}`)
    response = await send(url.pathname + url.search, { ...init, headers: retryHeaders }, init.method ?? 'GET')
  }

  if (!response.ok) {
    const text = await response.text()
    let body: ApiErrorBody | null = null
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
    throw new ApiError(
      body ?? {
        timestamp: new Date().toISOString(),
        status: response.status,
        code: 'DOWNLOAD_FAILED',
        message: 'The file could not be downloaded.',
      },
    )
  }
  return response.blob()
}

export const post = <T,>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

export const put = <T,>(path: string, body?: unknown) =>
  api<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) })

export const patch = <T,>(path: string, body?: unknown) =>
  api<T>(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) })

export const del = <T,>(path: string) => api<T>(path, { method: 'DELETE' })