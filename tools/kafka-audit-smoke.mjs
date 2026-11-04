const baseUrl = 'http://localhost'
const account = process.env.FILEHOME_ACCOUNT
const password = process.env.FILEHOME_PASSWORD

if (!account || !password) {
  throw new Error('Set FILEHOME_ACCOUNT and FILEHOME_PASSWORD.')
}

async function main() {
  const login = await fetch(`${baseUrl}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  if (!login.ok) throw new Error(await login.text())
  const cookies = login.headers.getSetCookie()
    .map((value) => value.split(';', 1)[0])
    .join('; ')
  const csrf = login.headers.getSetCookie()
    .find((value) => value.startsWith('FH_CSRF='))
    .split(';', 1)[0]
    .slice('FH_CSRF='.length)
  const headers = {
    Cookie: cookies,
    'X-CSRF-Token': csrf,
    'Content-Type': 'application/json',
  }
  const name = `kafka-audit-${Date.now()}`
  const createdResponse = await fetch(`${baseUrl}/api/v1/folders`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ parentId: null, name }),
  })
  if (!createdResponse.ok) throw new Error(await createdResponse.text())
  const created = await createdResponse.json()
  const deleted = await fetch(
    `${baseUrl}/api/v1/folders/${created.id}/permanent?recursive=true`,
    { method: 'DELETE', headers },
  )
  if (!deleted.ok) throw new Error(await deleted.text())
  await new Promise((resolve) => globalThis.setTimeout(resolve, 1500))
  await fetch(`${baseUrl}/api/v1/auth/logout`, {
    method: 'POST',
    headers: { Cookie: cookies, 'X-CSRF-Token': csrf },
  })
  process.stdout.write(
    `${JSON.stringify({ folderId: created.id, name })}\n`,
  )
}

await main()
