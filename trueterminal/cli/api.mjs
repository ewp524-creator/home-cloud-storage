import { openAsBlob } from 'node:fs'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { normalizeServer } from './config.mjs'

export class ApiError extends Error {
  constructor(message, status, code = '') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export class AuthenticationError extends ApiError {
  constructor(message = '登录状态已失效，请重新登录') {
    super(message, 401, 'UNAUTHORIZED')
    this.name = 'AuthenticationError'
  }
}

const MULTIPART_THRESHOLD = 8 * 1024 * 1024
const MAX_PART_CONCURRENCY = 4

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function retryDelay(attempt) {
  const ceiling = Math.min(15_000, 250 * 2 ** attempt)
  return ceiling / 2 + Math.random() * (ceiling / 2)
}

function queryString(params = {}) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  }
  const text = query.toString()
  return text ? `?${text}` : ''
}

export class FileHomeApi {
  constructor(server, accessToken = '') {
    this.server = normalizeServer(server)
    this.accessToken = accessToken
  }

  setToken(token) {
    this.accessToken = token || ''
  }

  async request(apiPath, options = {}) {
    const headers = new Headers(options.headers || {})
    if (this.accessToken) {
      headers.set('Authorization', `Bearer ${this.accessToken}`)
    }
    let body = options.body
    if (options.json !== undefined) {
      headers.set('Content-Type', 'application/json')
      body = JSON.stringify(options.json)
    }
    const response = await fetch(`${this.server}/api/v1${apiPath}`, {
      ...options,
      body,
      headers,
    })
    if (!response.ok) {
      let message = `HTTP ${response.status}`
      let code = ''
      try {
        const error = await response.json()
        message = error.message || error.code || message
        code = error.code || ''
      } catch {
        // Keep the HTTP status when the response is not JSON.
      }
      throw response.status === 401
        ? new AuthenticationError(message)
        : new ApiError(message, response.status, code)
    }
    if (response.status === 204) return null
    const contentType = response.headers.get('Content-Type') || ''
    if (contentType.includes('application/json')) return response.json()
    return response
  }

  async login(account, password) {
    const response = await this.request('/auth/cli/login', {
      method: 'POST',
      json: { account, password },
      headers: {},
    })
    this.setToken(response.accessToken)
    return response
  }

  me() {
    return this.request('/users/me')
  }

  list({ parentId, query, type = 'all', sort = 'name-asc', limit = 200 } = {}) {
    return this.request(
      `/files${queryString({ parentId, q: query, type, sort, limit })}`,
    )
  }

  recent(limit = 80) {
    return this.request(`/files/recent${queryString({ limit })}`)
  }

  starred() {
    return this.request('/files/starred')
  }

  details(id) {
    return this.request(`/files/${id}`)
  }

  createFolder(parentId, name) {
    return this.request('/folders', {
      method: 'POST',
      json: { parentId, name },
    })
  }

  rename(id, name) {
    return this.request(`/files/${id}`, {
      method: 'PATCH',
      json: { name },
    })
  }

  move(ids, targetParentId) {
    return this.request('/files/move', {
      method: 'POST',
      json: { ids, targetParentId },
    })
  }

  copy(ids, targetParentId, nameOverrides = []) {
    return this.request('/files/copy', {
      method: 'POST',
      json: { ids, targetParentId, nameOverrides },
    })
  }

  deleteFile(id) {
    return this.request(`/files/${id}/permanent`, { method: 'DELETE' })
  }

  deleteFolder(id) {
    return this.request(`/folders/${id}/permanent?recursive=true`, {
      method: 'DELETE',
    })
  }

  previewAccess(id) {
    return this.request(`/files/${id}/preview-access`)
  }

  mediaUrl(access) {
    return new URL(access.url, this.server).toString()
  }

  async textPreview(id, limit = 2 * 1024 * 1024) {
    const access = await this.previewAccess(id)
    if (!['text', 'code'].includes(access.previewKind)) {
      throw new ApiError('该文件不是文本或代码格式，请使用 preview 或 download', 400)
    }
    const headers =
      access.size > limit
        ? { Range: `bytes=0-${limit - 1}` }
        : {}
    const response = await fetch(this.mediaUrl(access), {
      headers,
    })
    if (!response.ok && response.status !== 206) {
      throw new ApiError(`预览读取失败：HTTP ${response.status}`, response.status)
    }
    return {
      text: await response.text(),
      truncated: access.size > limit,
      access,
    }
  }

  async download(id, folder = false) {
    const response = await this.authorizedFetch(
      folder ? `/folders/${id}/download` : `/files/${id}/download`,
    )
    return response.body
  }

  async authorizedFetch(apiPath) {
    const headers = new Headers()
    if (this.accessToken) {
      headers.set('Authorization', `Bearer ${this.accessToken}`)
    }
    const response = await fetch(`${this.server}/api/v1${apiPath}`, {
      headers,
    })
    if (!response.ok) {
      throw response.status === 401
        ? new AuthenticationError()
        : new ApiError(`下载失败：HTTP ${response.status}`, response.status)
    }
    return response
  }

  async upload(localPath, parentId, onProgress) {
    const stats = await import('node:fs/promises').then((fs) =>
      fs.stat(localPath),
    )
    const blob = await openAsBlob(localPath)
    if (stats.size >= MULTIPART_THRESHOLD) {
      return this.uploadMultipart(localPath, blob, stats, parentId, onProgress)
    }
    const form = new FormData()
    form.append('file', blob, path.basename(localPath))
    return this.request(
      `/files/upload${queryString({ parentId })}`,
      {
        method: 'POST',
        body: form,
      },
    )
  }

  async uploadMultipart(localPath, blob, stats, parentId, onProgress) {
    const name = path.basename(localPath)
    const fingerprint = sha256(
      `${name}|${stats.size}|${Math.trunc(stats.mtimeMs)}|${parentId || 'root'}`,
    )
    const active = await this.request('/uploads')
    let session = (active.items || []).find(
      (item) => item.clientFingerprint === fingerprint,
    )

    if (session) {
      for (const part of session.uploadedParts || []) {
        const start = (part.partNumber - 1) * session.partSizeBytes
        const chunk = blob.slice(start, start + part.sizeBytes)
        if (sha256(Buffer.from(await chunk.arrayBuffer())) !== part.sha256) {
          await this.request(`/uploads/${session.id}`, { method: 'DELETE' })
          session = null
          break
        }
      }
    }
    if (!session) {
      session = await this.request('/uploads', {
        method: 'POST',
        json: {
          parentId: parentId || null,
          fileName: name,
          sizeBytes: stats.size,
          contentType: blob.type || 'application/octet-stream',
          clientFingerprint: fingerprint,
        },
      })
    }

    const uploaded = new Map(
      (session.uploadedParts || []).map((part) => [
        part.partNumber,
        part.sizeBytes,
      ]),
    )
    let uploadedBytes = [...uploaded.values()].reduce(
      (sum, value) => sum + value,
      0,
    )
    const missing = []
    for (let partNumber = 1; partNumber <= session.totalParts; partNumber += 1) {
      if (!uploaded.has(partNumber)) missing.push(partNumber)
    }
    let cursor = 0
    let failure = null
    const worker = async () => {
      while (!failure) {
        const index = cursor
        cursor += 1
        if (index >= missing.length) return
        const partNumber = missing[index]
        const start = (partNumber - 1) * session.partSizeBytes
        const end = Math.min(stats.size, start + session.partSizeBytes)
        const chunk = blob.slice(start, end)
        const digest = sha256(Buffer.from(await chunk.arrayBuffer()))
        try {
          await this.uploadPartWithRetry(session.id, partNumber, chunk, digest)
          uploadedBytes += chunk.size
          onProgress?.(
            Math.min(99, Math.round((uploadedBytes / stats.size) * 100)),
          )
        } catch (error) {
          failure = error
        }
      }
    }
    await Promise.all(
      Array.from(
        { length: Math.min(MAX_PART_CONCURRENCY, missing.length) },
        worker,
      ),
    )
    if (failure) throw failure
    const completed = await this.request(`/uploads/${session.id}/complete`, {
      method: 'POST',
    })
    onProgress?.(100)
    return completed.file
  }

  async uploadPartWithRetry(uploadId, partNumber, chunk, digest) {
    let lastError
    for (let attempt = 0; attempt < 8; attempt += 1) {
      try {
        return await this.request(`/uploads/${uploadId}/parts/${partNumber}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/octet-stream',
            'X-Chunk-SHA256': digest,
          },
          body: chunk,
        })
      } catch (error) {
        lastError = error
        if (!(error instanceof ApiError) || (error.status !== 409 && error.status < 500)) {
          throw error
        }
        await new Promise((resolve) =>
          globalThis.setTimeout(
            resolve,
            retryDelay(attempt),
          ),
        )
      }
    }
    throw lastError
  }

  listShares() {
    return this.request('/shares')
  }

  createShare(payload) {
    return this.request('/shares', {
      method: 'POST',
      json: payload,
    })
  }

  revokeShare(id) {
    return this.request(`/shares/${id}`, { method: 'DELETE' })
  }
}
