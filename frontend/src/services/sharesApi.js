import { apiRequest, saveBlob } from './apiClient'

export function createShare(payload) {
  return apiRequest('/shares', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listShares() {
  return apiRequest('/shares')
}

export function revokeShare(id) {
  return apiRequest(`/shares/${id}`, { method: 'DELETE' })
}

export function publicShare(token) {
  return apiRequest(`/public/shares/${encodeURIComponent(token)}`)
}

export function verifyShareCode(token, accessCode) {
  return apiRequest(`/public/shares/${encodeURIComponent(token)}/verify`, {
    method: 'POST',
    body: JSON.stringify({ accessCode }),
  })
}

export function publicSharePreviewAccess(token, accessCode = '') {
  return apiRequest(
    `/public/shares/${encodeURIComponent(token)}/preview-access`,
    {
      method: 'POST',
      body: JSON.stringify({ accessCode }),
    },
  )
}

export async function downloadPublicShare(token, accessCode, fileName, isFolder) {
  const search = new URLSearchParams()
  if (accessCode) search.set('accessCode', accessCode)
  const response = await fetch(
    `/api/v1/public/shares/${encodeURIComponent(token)}/download?${search}`,
    { credentials: 'same-origin' },
  )
  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      message = (await response.json()).message || message
    } catch {
      // Keep HTTP status text.
    }
    throw new Error(message)
  }
  saveBlob(await response.blob(), isFolder ? `${fileName}.zip` : fileName)
}
