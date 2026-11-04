import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'

export const DEFAULT_SERVER = 'http://127.0.0.1:8080'

export function normalizeServer(value = DEFAULT_SERVER) {
  const raw = String(value || DEFAULT_SERVER).trim()
  const withScheme = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`
  return withScheme.replace(/\/+$/, '')
}

export function sessionFile(environment = process.env) {
  if (environment.FILEHOME_TERMINAL_CONFIG) {
    return path.resolve(environment.FILEHOME_TERMINAL_CONFIG)
  }
  return path.join(os.homedir(), '.filehome-terminal', 'session.json')
}

export async function loadSession(environment = process.env) {
  try {
    const raw = await fs.readFile(sessionFile(environment), 'utf8')
    const session = JSON.parse(raw)
    if (!session.accessToken || !session.server || !session.user?.id) {
      return null
    }
    return {
      ...session,
      server: normalizeServer(session.server),
    }
  } catch {
    return null
  }
}

export async function saveSession(session, environment = process.env) {
  const target = sessionFile(environment)
  await fs.mkdir(path.dirname(target), {
    recursive: true,
    mode: 0o700,
  })
  await fs.writeFile(
    target,
    `${JSON.stringify(
      {
        ...session,
        server: normalizeServer(session.server),
        savedAt: new Date().toISOString(),
      },
      null,
      2,
    )}\n`,
    { mode: 0o600 },
  )
  if (process.platform !== 'win32') {
    await fs.chmod(target, 0o600)
  }
  return target
}

export async function clearSession(environment = process.env) {
  await fs.rm(sessionFile(environment), { force: true })
}
