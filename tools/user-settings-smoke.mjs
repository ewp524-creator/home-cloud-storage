import { randomBytes } from 'node:crypto'

const baseUrl = 'http://localhost'
const suffix = Date.now().toString(36)
const username = `phase4${suffix}`
const email = `${username}@example.com`
const initialPassword = `T-${randomBytes(18).toString('base64url')}`
const newPassword = `N-${randomBytes(18).toString('base64url')}`

async function request(path, options, cookies = '', csrf = '') {
  const headers = new Headers(options.headers || {})
  if (cookies) headers.set('Cookie', cookies)
  if (csrf && !['GET', 'HEAD'].includes(options.method || 'GET')) {
    headers.set('X-CSRF-Token', csrf)
  }
  if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  return fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
    body:
      options.json === undefined
        ? options.body
        : JSON.stringify(options.json),
  })
}

async function main() {
  const registration = await request('/api/v1/auth/register', {
    method: 'POST',
    json: {
      username,
      email,
      password: initialPassword,
      displayName: 'Phase Four Test',
    },
  })
  if (!registration.ok) throw new Error(await registration.text())
  const registered = await registration.json()
  const cookies = registration.headers.getSetCookie()
    .map((value) => value.split(';', 1)[0])
    .join('; ')
  const csrf = registration.headers.getSetCookie()
    .find((value) => value.startsWith('FH_CSRF='))
    .split(';', 1)[0]
    .slice('FH_CSRF='.length)

  const settingsResponse = await request(
    '/api/v1/users/me/settings',
    { method: 'GET' },
    cookies,
    csrf,
  )
  if (!settingsResponse.ok) throw new Error(await settingsResponse.text())
  const settings = await settingsResponse.json()

  const preferencesResponse = await request(
    '/api/v1/users/me/settings',
    {
      method: 'PATCH',
      json: {
        defaultView: 'LIST',
        defaultSort: 'name-asc',
        reduceMotion: true,
        uploadConcurrency: 2,
      },
    },
    cookies,
    csrf,
  )
  if (!preferencesResponse.ok) throw new Error(await preferencesResponse.text())

  const profileResponse = await request(
    '/api/v1/users/me/profile',
    {
      method: 'PATCH',
      json: {
        displayName: 'Phase Four Updated',
        email,
      },
    },
    cookies,
    csrf,
  )
  if (!profileResponse.ok) throw new Error(await profileResponse.text())

  const passwordResponse = await request(
    '/api/v1/users/me/password',
    {
      method: 'POST',
      json: {
        currentPassword: initialPassword,
        newPassword,
      },
    },
    cookies,
    csrf,
  )
  if (!passwordResponse.ok) throw new Error(await passwordResponse.text())

  const deactivation = await request(
    '/api/v1/users/me/deactivate',
    {
      method: 'POST',
      json: {
        password: newPassword,
        confirmation: username,
      },
    },
    cookies,
    csrf,
  )
  if (!deactivation.ok) throw new Error(await deactivation.text())

  const loginAfterDeactivation = await request('/api/v1/auth/login', {
    method: 'POST',
    json: { account: username, password: newPassword },
  })
  const result = {
    userId: registered.user.id,
    username,
    defaultSettings: settings.preferences,
    preferencesUpdated: true,
    profileUpdated: true,
    passwordChanged: true,
    deactivationStatus: deactivation.status,
    loginAfterDeactivation: loginAfterDeactivation.status,
  }
  process.stdout.write(`${JSON.stringify(result)}\n`)
}

await main()
