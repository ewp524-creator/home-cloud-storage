const baseUrl = 'http://localhost'
const account = process.env.FILEHOME_ACCOUNT
const password = process.env.FILEHOME_PASSWORD
const fileName = process.env.FILEHOME_DOCX_NAME

if (!account || !password || !fileName) {
  throw new Error(
    'Set FILEHOME_ACCOUNT, FILEHOME_PASSWORD, and FILEHOME_DOCX_NAME.',
  )
}

async function main() {
  const login = await fetch(`${baseUrl}/api/v1/auth/cli/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  if (!login.ok) throw new Error(await login.text())
  const { accessToken } = await login.json()
  const headers = { Authorization: `Bearer ${accessToken}` }
  const list = await fetch(
    `${baseUrl}/api/v1/files?q=${encodeURIComponent(fileName)}&limit=10`,
    { headers },
  )
  if (!list.ok) throw new Error(await list.text())
  const files = await list.json()
  const file = files.items.find((item) => item.name === fileName)
  if (!file) throw new Error(`File not found: ${fileName}`)
  const access = await fetch(
    `${baseUrl}/api/v1/files/${file.id}/preview-access`,
    { headers },
  )
  if (!access.ok) throw new Error(await access.text())
  const preview = await access.json()
  const media = await fetch(new URL(preview.url, baseUrl), {
    headers,
  })
  if (!media.ok) throw new Error(await media.text())
  const bytes = new Uint8Array(await media.arrayBuffer())
  const result = {
    fileId: file.id,
    name: file.name,
    previewKind: preview.previewKind,
    contentType: media.headers.get('Content-Type'),
    contentLength: Number(media.headers.get('Content-Length')),
    zipMagic: bytes[0] === 0x50 && bytes[1] === 0x4b,
    expiresAt: preview.expiresAt,
  }
  process.stdout.write(`${JSON.stringify(result)}\n`)
}

await main()
