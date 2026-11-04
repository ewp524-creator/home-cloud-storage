import { apiRequest } from './apiClient'

export function getUserSettings() {
  return apiRequest('/users/me/settings')
}

export function updatePreferences(payload) {
  return apiRequest('/users/me/settings', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function updateProfile(payload) {
  return apiRequest('/users/me/profile', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function changePassword(payload) {
  return apiRequest('/users/me/password', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deactivateAccount(payload) {
  return apiRequest('/users/me/deactivate', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
