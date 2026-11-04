import {
  ApiRequestError,
  apiRequest,
  apiUploadChunk,
} from './apiClient'

let maxConcurrentParts = 4
const MAX_RETRIES = 8
let activeParts = 0
const partWaiters = []

function acquirePartSlot() {
  return new Promise((resolve) => {
    if (activeParts < maxConcurrentParts) {
      activeParts += 1
      resolve()
      return
    }
    partWaiters.push(resolve)
  })
}

function releasePartSlot() {
  const next = partWaiters.shift()
  if (next) {
    next()
    return
  }
  activeParts = Math.max(0, activeParts - 1)
}

function hex(buffer) {
  return Array.from(new Uint8Array(buffer), (value) =>
    value.toString(16).padStart(2, '0'),
  ).join('')
}

async function sha256(buffer) {
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前浏览器不支持安全分片校验，请升级浏览器')
  }
  return hex(await globalThis.crypto.subtle.digest('SHA-256', buffer))
}

async function fileFingerprint(file, parentId) {
  const value = [
    file.name,
    file.size,
    file.lastModified,
    parentId || 'root',
  ].join('|')
  return sha256(new TextEncoder().encode(value))
}

function delay(milliseconds) {
  return new Promise((resolve) => globalThis.setTimeout(resolve, milliseconds))
}

function backoffDelay(attempt) {
  const ceiling = Math.min(15_000, 250 * 2 ** attempt)
  return ceiling / 2 + Math.random() * (ceiling / 2)
}

function isRetryable(error) {
  return (
    error instanceof ApiRequestError
      ? error.status === 409 || error.status >= 500
      : true
  )
}

async function putPartWithRetry(
  uploadId,
  partNumber,
  blob,
  partSha,
  onRetry,
) {
  let lastError
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt += 1) {
    await acquirePartSlot()
    let failed
    try {
      return await apiUploadChunk(
        `/uploads/${uploadId}/parts/${partNumber}`,
        blob,
        partSha,
      )
    } catch (error) {
      failed = error
    } finally {
      releasePartSlot()
    }
    lastError = failed
    if (attempt === MAX_RETRIES || !isRetryable(failed)) throw failed
    onRetry?.({ attempt: attempt + 1, maxRetries: MAX_RETRIES })
    await delay(backoffDelay(attempt))
  }
  throw lastError
}

async function activeSessionFor(file, parentId) {
  const fingerprint = await fileFingerprint(file, parentId)
  const response = await apiRequest('/uploads')
  const session = (response.items || []).find(
    (item) =>
      item.clientFingerprint === fingerprint &&
      item.fileName === file.name &&
      item.sizeBytes === file.size &&
      (item.parentId || null) === (parentId || null),
  )
  return session || null
}

async function sessionMatchesFile(file, session) {
  for (const part of session.uploadedParts || []) {
    const start = (part.partNumber - 1) * session.partSizeBytes
    const blob = file.slice(start, start + part.sizeBytes)
    const digest = await sha256(await blob.arrayBuffer())
    if (digest !== part.sha256) return false
  }
  return true
}

async function createSession(file, parentId, fingerprint) {
  return apiRequest('/uploads', {
    method: 'POST',
    body: JSON.stringify({
      parentId: parentId || null,
      fileName: file.name,
      sizeBytes: file.size,
      contentType: file.type || 'application/octet-stream',
      clientFingerprint: fingerprint,
    }),
  })
}

async function resolveSession(file, parentId) {
  const fingerprint = await fileFingerprint(file, parentId)
  const existing = await activeSessionFor(file, parentId)
  if (existing && (await sessionMatchesFile(file, existing))) {
    return existing
  }
  if (existing) {
    await apiRequest(`/uploads/${existing.id}`, { method: 'DELETE' })
  }
  return createSession(file, parentId, fingerprint)
}

export async function uploadMultipartFile(
  file,
  parentId,
  onProgress,
  onState,
) {
  const session = await resolveSession(file, parentId)
  const uploaded = new Map(
    (session.uploadedParts || []).map((part) => [
      part.partNumber,
      part.sizeBytes,
    ]),
  )
  let uploadedBytes = [...uploaded.values()].reduce(
    (sum, size) => sum + size,
    0,
  )
  const progress = () => {
    onProgress?.(
      Math.min(
        99,
        Math.round((uploadedBytes / Math.max(1, file.size)) * 100),
      ),
    )
  }
  onState?.({
    uploadedParts: uploaded.size,
    totalParts: session.totalParts,
    resumed: uploaded.size > 0,
  })
  progress()

  if (session.status !== 'COMPLETED') {
    const missing = []
    for (let partNumber = 1; partNumber <= session.totalParts; partNumber += 1) {
      if (!uploaded.has(partNumber)) missing.push(partNumber)
    }

    let cursor = 0
    let fatalError = null
    const worker = async () => {
      while (!fatalError) {
        const index = cursor
        cursor += 1
        if (index >= missing.length) return
        const partNumber = missing[index]
        const start = (partNumber - 1) * session.partSizeBytes
        const end = Math.min(file.size, start + session.partSizeBytes)
        const blob = file.slice(start, end)
        try {
          const partSha = await sha256(await blob.arrayBuffer())
          await putPartWithRetry(
            session.id,
            partNumber,
            blob,
            partSha,
            (retry) =>
              onState?.({
                retrying: true,
                attempt: retry.attempt,
                maxRetries: retry.maxRetries,
                uploadedParts: uploaded.size,
                totalParts: session.totalParts,
                resumed: false,
              }),
          )
          uploaded.set(partNumber, blob.size)
          uploadedBytes += blob.size
          onState?.({
            uploadedParts: uploaded.size,
            totalParts: session.totalParts,
            resumed: false,
          })
          progress()
        } catch (error) {
          fatalError = error
        }
      }
    }
    const workerCount = Math.min(maxConcurrentParts, missing.length)
    await Promise.all(Array.from({ length: workerCount }, worker))
    if (fatalError) {
      throw fatalError
    }
  }

  const completed = await apiRequest(`/uploads/${session.id}/complete`, {
    method: 'POST',
  })
  onProgress?.(100)
  onState?.({
    uploadedParts: session.totalParts,
    totalParts: session.totalParts,
    resumed: false,
  })
  return completed.file
}

export { fileFingerprint }

export function setUploadConcurrency(value) {
  maxConcurrentParts = Math.min(4, Math.max(1, Number(value) || 4))
}
