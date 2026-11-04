import { apiRequest } from './apiClient'

export function uploadAvatar(file) {
  const form = new FormData()
  form.append('avatar', file, file.name)
  return apiRequest('/users/me/avatar', {
    method: 'POST',
    body: form,
  })
}

export function removeAvatar() {
  return apiRequest('/users/me/avatar', { method: 'DELETE' })
}
