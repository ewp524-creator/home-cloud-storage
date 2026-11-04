import {
  apiBlob,
  apiRequest,
  saveBlob,
  uploadWithProgress,
} from './apiClient'
import { uploadMultipartFile } from './multipartUpload'

const MULTIPART_THRESHOLD = 8 * 1024 * 1024

function query(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, value)
    }
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

export function listFiles(params) {
  return apiRequest(`/files${query(params)}`)
}

export function recentFiles(limit = 80) {
  return apiRequest(`/files/recent${query({ limit })}`)
}

export function starredFiles() {
  return apiRequest('/files/starred')
}

export function folderTree(parentId) {
  return apiRequest(`/folders/tree${query({ parentId })}`)
}

export function fileDetails(id) {
  return apiRequest(`/files/${id}`)
}

export function filePreviewAccess(id) {
  return apiRequest(`/files/${id}/preview-access`)
}

export function createFolder(parentId, name) {
  return apiRequest('/folders', {
    method: 'POST',
    body: JSON.stringify({ parentId, name }),
  })
}

export function renameFile(id, name) {
  return apiRequest(`/files/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  })
}

export function starFile(id, starred) {
  return apiRequest(`/files/${id}/star`, {
    method: 'PATCH',
    body: JSON.stringify({ starred }),
  })
}

export function moveFiles(ids, targetParentId) {
  return apiRequest('/files/move', {
    method: 'POST',
    body: JSON.stringify({ ids, targetParentId }),
  })
}

export function copyFiles(ids, targetParentId, nameOverrides = []) {
  return apiRequest('/files/copy', {
    method: 'POST',
    body: JSON.stringify({ ids, targetParentId, nameOverrides }),
  })
}

export function trashFiles(ids) {
  return apiRequest('/trash', {
    method: 'POST',
    body: JSON.stringify({ ids }),
  })
}

export function trashList() {
  return apiRequest('/trash')
}

export function restoreFiles(ids) {
  return apiRequest('/trash/restore', {
    method: 'POST',
    body: JSON.stringify({ ids }),
  })
}

export function deleteFilePermanent(id) {
  return apiRequest(`/files/${id}/permanent`, { method: 'DELETE' })
}

export function deleteFolderPermanent(id) {
  return apiRequest(`/folders/${id}/permanent?recursive=true`, {
    method: 'DELETE',
  })
}

export function emptyTrash() {
  return apiRequest('/trash', { method: 'DELETE' })
}

export async function uploadFile(file, parentId, onProgress, onState) {
  if (file.size >= MULTIPART_THRESHOLD) {
    return uploadMultipartFile(file, parentId, onProgress, onState)
  }
  const formData = new FormData()
  formData.append('file', file)
  return uploadWithProgress(
    `/files/upload${query({ parentId })}`,
    formData,
    onProgress,
  )
}

export async function downloadEntry(entry) {
  const path =
    entry.kind === 'folder'
      ? `/folders/${entry.id}/download`
      : `/files/${entry.id}/download`
  const blob = await apiBlob(path)
  const fileName =
    entry.kind === 'folder' ? `${entry.name}.zip` : entry.name
  saveBlob(blob, fileName)
}
