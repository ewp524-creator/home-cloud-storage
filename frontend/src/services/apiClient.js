const API_PREFIX = '/api/v1'
const CSRF_COOKIE = 'FH_CSRF'
const LEGACY_TOKEN_KEY = 'filehome.accessToken'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

globalThis.localStorage?.removeItem(LEGACY_TOKEN_KEY)

export class ApiRequestError extends Error {
  constructor(message, status, code = '') {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
  }
}

function readCookie(name) {
  const prefix = `${name}=`
  for (const item of (globalThis.document?.cookie || '').split(';')) {
    const cookie = item.trim()
    if (cookie.startsWith(prefix)) {
      return decodeURIComponent(cookie.slice(prefix.length))
    }
  }
  return ''
}

function prepareHeaders(options, headers) {
  const method = String(options.method || 'GET').toUpperCase()
  if (!SAFE_METHODS.has(method)) {
    const csrfToken = readCookie(CSRF_COOKIE)
    if (csrfToken) headers.set('X-CSRF-Token', csrfToken)
  }
  return headers
}

async function parseError(response) {
  try {
    const error = await response.json()
    return new ApiRequestError(
      error.message || error.code || `HTTP ${response.status}`,
      response.status,
      error.code || '',
    )
  } catch {
    return new ApiRequestError(`HTTP ${response.status}`, response.status)
  }
}

function notifyUnauthorized(path, status) {
  if (
    status !== 401 ||
    path.startsWith('/auth/login') ||
    path.startsWith('/auth/register')
  ) {
    return
  }
  globalThis.dispatchEvent?.(new CustomEvent('filehome:unauthorized'))
}

export async function apiRequest(path, options = {}) {
  const headers = prepareHeaders(options, new Headers(options.headers || {}))
  if (
    options.body &&
    !(options.body instanceof FormData) &&
    !headers.has('Content-Type')
  ) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    ...options,
    credentials: 'same-origin',
    headers,
  })
  if (!response.ok) {
    const error = await parseError(response)
    notifyUnauthorized(path, response.status)
    throw error
  }
  if (response.status === 204) return null
  const contentType = response.headers.get('Content-Type') || ''
  return contentType.includes('application/json')
    ? response.json()
    : response.text()
}

export async function apiBlob(path) {
  const response = await fetch(`${API_PREFIX}${path}`, {
    credentials: 'same-origin',
  })
  if (!response.ok) {
    const error = await parseError(response)
    notifyUnauthorized(path, response.status)
    throw error
  }
  return response.blob()
}

export async function apiUploadChunk(path, blob, sha256) {
  const headers = new Headers({
    'Content-Type': 'application/octet-stream',
    'X-Chunk-SHA256': sha256,
  })
  const csrfToken = readCookie(CSRF_COOKIE)
  if (csrfToken) headers.set('X-CSRF-Token', csrfToken)
  const response = await fetch(`${API_PREFIX}${path}`, {
    method: 'PUT',
    credentials: 'same-origin',
    headers,
    body: blob,
  })
  if (!response.ok) {
    const error = await parseError(response)
    notifyUnauthorized(path, response.status)
    throw error
  }
  return response.json()
}

export function uploadWithProgress(path, formData, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_PREFIX}${path}`)
    xhr.withCredentials = true
    const csrfToken = readCookie(CSRF_COOKIE)
    if (csrfToken) xhr.setRequestHeader('X-CSRF-Token', csrfToken)
    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        onProgress?.(Math.round((event.loaded / event.total) * 100))
      }
    })
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(xhr.responseText ? JSON.parse(xhr.responseText) : null)
        } catch {
          resolve(null)
        }
      } else {
        try {
          reject(
            new ApiRequestError(
              JSON.parse(xhr.responseText).message || `HTTP ${xhr.status}`,
              xhr.status,
            ),
          )
        } catch {
          reject(new ApiRequestError(`HTTP ${xhr.status}`, xhr.status))
        }
      }
    })
    xhr.addEventListener('error', () => reject(new Error('涓婁紶澶辫触')))
    xhr.send(formData)
  })
}

export function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  globalThis.setTimeout(() => URL.revokeObjectURL(url), 1500)
}
