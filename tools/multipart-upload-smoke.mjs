import { createHash, randomBytes } from 'node:crypto'

const baseUrl = 'http://localhost'
const account = process.env.FILEHOME_ACCOUNT
const password = process.env.FILEHOME_PASSWORD
const size = Number(process.env.FILEHOME_SMOKE_SIZE || 52 * 1024 * 1024 + 123)

if (!account || !password) {
  throw new Error('Set FILEHOME_ACCOUNT and FILEHOME_PASSWORD.')
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function cookieHeader(setCookies) {
  return setCookies.map((value) => value.split(';', 1)[0]).join('; ')
}

async function jsonRequest(path, options, cookies, csrf) {
  const headers = new Headers(options.headers || {})
  headers.set('Cookie', cookies)
  if (csrf && !['GET', 'HEAD'].includes(options.method || 'GET')) {
    headers.set('X-CSRF-Token', csrf)
  }
  if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
    body:
      options.json === undefined
        ? options.body
        : JSON.stringify(options.json),
  })
  if (!response.ok) {
    throw new Error(`${response.status}: ${await response.text()}`)
  }
  return response.status === 204 ? null : response.json()
}

async function main() {
  const login = await fetch(`${baseUrl}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  if (!login.ok) throw new Error(`Login failed: ${await login.text()}`)
  const setCookies = login.headers.getSetCookie()
  const cookies = cookieHeader(setCookies)
  const csrf = setCookies
    .find((value) => value.startsWith('FH_CSRF='))
    .split(';', 1)[0]
    .slice('FH_CSRF='.length)

  const content = randomBytes(size)
  const fingerprint = sha256(
    `smoke-${Date.now()}|${size}|${Date.now()}|root`,
  )
  const session = await jsonRequest(
    '/api/v1/uploads',
    {
      method: 'POST',
      json: {
        parentId: null,
        fileName: `multipart-smoke-${Date.now()}.bin`,
        sizeBytes: size,
        contentType: 'application/octet-stream',
        clientFingerprint: fingerprint,
      },
    },
    cookies,
    csrf,
  )

  let file
  try {
    const firstStart = 0
    const firstEnd = Math.min(size, session.partSizeBytes)
    const firstChunk = content.subarray(firstStart, firstEnd)
    const rejected = await fetch(
      `${baseUrl}/api/v1/uploads/${session.id}/parts/1`,
      {
        method: 'PUT',
        headers: {
          Cookie: cookies,
          'X-CSRF-Token': csrf,
          'X-Chunk-SHA256': '0'.repeat(64),
          'Content-Type': 'application/octet-stream',
        },
        body: firstChunk,
      },
    )
    if (rejected.status !== 400) {
      throw new Error(`Bad checksum was not rejected: ${rejected.status}`)
    }
    const uploadStartedAt = performance.now()
    const firstResponse = await fetch(
      `${baseUrl}/api/v1/uploads/${session.id}/parts/1`,
      {
        method: 'PUT',
        headers: {
          Cookie: cookies,
          'X-CSRF-Token': csrf,
          'X-Chunk-SHA256': sha256(firstChunk),
          'Content-Type': 'application/octet-stream',
        },
        body: firstChunk,
      },
    )
    if (!firstResponse.ok) {
      throw new Error(`Part 1: ${await firstResponse.text()}`)
    }
    const resumed = await jsonRequest(
      `/api/v1/uploads/${session.id}`,
      { method: 'GET' },
      cookies,
      csrf,
    )
    if (
      resumed.uploadedParts.length !== 1 ||
      resumed.uploadedParts[0].partNumber !== 1
    ) {
      throw new Error('Upload session did not expose the resumed part')
    }

    let nextPart = 2
    const workers = Array.from(
      { length: Math.min(4, Math.max(0, session.totalParts - 1)) },
      async () => {
        while (true) {
          const partNumber = nextPart
          nextPart += 1
          if (partNumber > session.totalParts) return
          const start = (partNumber - 1) * session.partSizeBytes
          const end = Math.min(size, start + session.partSizeBytes)
          const chunk = content.subarray(start, end)
          const response = await fetch(
            `${baseUrl}/api/v1/uploads/${session.id}/parts/${partNumber}`,
            {
              method: 'PUT',
              headers: {
                Cookie: cookies,
                'X-CSRF-Token': csrf,
                'X-Chunk-SHA256': sha256(chunk),
                'Content-Type': 'application/octet-stream',
              },
              body: chunk,
            },
          )
          if (!response.ok) {
            throw new Error(`Part ${partNumber}: ${await response.text()}`)
          }
        }
      },
    )
    await Promise.all(workers)
    const completed = await jsonRequest(
      `/api/v1/uploads/${session.id}/complete`,
      { method: 'POST' },
      cookies,
      csrf,
    )
    const uploadSeconds = (performance.now() - uploadStartedAt) / 1000
    file = completed.file
    const download = await fetch(
      `${baseUrl}/api/v1/files/${file.id}/download`,
      { headers: { Cookie: cookies } },
    )
    if (!download.ok) throw new Error(`Download: ${await download.text()}`)
    const downloaded = Buffer.from(await download.arrayBuffer())
    const sourceHash = sha256(content)
    const result = {
      uploadId: session.id,
      fileId: file.id,
      parts: session.totalParts,
      size,
      uploadSeconds: Math.round(uploadSeconds * 1000) / 1000,
      uploadMiBPerSecond:
        Math.round((size / 1024 / 1024 / uploadSeconds) * 100) / 100,
      sourceSha256: sourceHash,
      downloadedSha256: sha256(downloaded),
      checksumMatch: sourceHash === sha256(downloaded),
      badChecksumRejected: rejected.status === 400,
      resumedParts: resumed.uploadedParts.length,
    }
    process.stdout.write(`${JSON.stringify(result)}\n`)
    if (!result.checksumMatch) throw new Error('Downloaded checksum mismatch')
  } finally {
    if (file) {
      await jsonRequest(
        `/api/v1/files/${file.id}/permanent`,
        { method: 'DELETE' },
        cookies,
        csrf,
      )
    } else {
      await fetch(`${baseUrl}/api/v1/uploads/${session.id}`, {
        method: 'DELETE',
        headers: { Cookie: cookies, 'X-CSRF-Token': csrf },
      })
    }
    await fetch(`${baseUrl}/api/v1/auth/logout`, {
      method: 'POST',
      headers: { Cookie: cookies, 'X-CSRF-Token': csrf },
    })
  }
}

await main()
