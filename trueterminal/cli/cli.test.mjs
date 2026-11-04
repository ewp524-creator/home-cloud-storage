import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import {
  clearSession,
  loadSession,
  normalizeServer,
  saveSession,
} from './config.mjs'
import { tokenizeCommand } from './terminal.mjs'

test('normalizes API server URLs', () => {
  assert.equal(normalizeServer('localhost:8080/'), 'http://localhost:8080')
  assert.equal(
    normalizeServer('https://files.example.com/'),
    'https://files.example.com',
  )
})

test('tokenizes quoted CLI arguments', () => {
  assert.deepEqual(tokenizeCommand('mv "my file.txt" archive/'), [
    'mv',
    'my file.txt',
    'archive/',
  ])
})

test('persists and clears CLI session', async () => {
  const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'fhterm-test-'))
  const environment = {
    FILEHOME_TERMINAL_CONFIG: path.join(temporary, 'session.json'),
  }
  try {
    await saveSession(
      {
        server: 'http://127.0.0.1:8080',
        accessToken: 'token',
        user: { id: 'user-id', username: 'tester' },
      },
      environment,
    )
    const loaded = await loadSession(environment)
    assert.equal(loaded.accessToken, 'token')
    assert.equal(loaded.user.username, 'tester')
    await clearSession(environment)
    assert.equal(await loadSession(environment), null)
  } finally {
    await fs.rm(temporary, { recursive: true, force: true })
  }
})
