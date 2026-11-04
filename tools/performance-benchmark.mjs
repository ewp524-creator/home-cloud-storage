import { performance } from 'node:perf_hooks'

function option(name, fallback) {
  const prefix = `--${name}=`
  const value = process.argv.find((item) => item.startsWith(prefix))
  return value ? value.slice(prefix.length) : fallback
}

const baseUrl = option('url', 'http://127.0.0.1:8080').replace(/\/+$/, '')
const account = option('account', process.env.FILEHOME_ACCOUNT || '')
const password = option('password', process.env.FILEHOME_PASSWORD || '')
const path = option('path', '/api/v1/files?limit=80')
const requestCount = Number(option('requests', '1200'))
const concurrency = Number(option('concurrency', '32'))
const warmupCount = Number(option('warmup', '120'))

if (!account || !password) {
  throw new Error('Set --account and --password, or FILEHOME_ACCOUNT/FILEHOME_PASSWORD')
}
if (!Number.isInteger(requestCount) || requestCount <= 0) {
  throw new Error('--requests must be a positive integer')
}
if (!Number.isInteger(concurrency) || concurrency <= 0) {
  throw new Error('--concurrency must be a positive integer')
}

function percentile(sorted, ratio) {
  const index = Math.min(
    sorted.length - 1,
    Math.max(0, Math.ceil(sorted.length * ratio) - 1),
  )
  return sorted[index]
}

function round(value) {
  return Math.round(value * 100) / 100
}

async function login() {
  const response = await fetch(`${baseUrl}/api/v1/auth/cli/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  if (!response.ok) {
    throw new Error(`Login failed with HTTP ${response.status}: ${await response.text()}`)
  }
  const { accessToken } = await response.json()
  if (!accessToken) throw new Error('Login did not return an access token')
  return { accessToken }
}

async function request(accessToken) {
  const startedAt = performance.now()
  const response = await fetch(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  const body = await response.arrayBuffer()
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${new TextDecoder().decode(body)}`)
  }
  return performance.now() - startedAt
}

async function runPool(total, workers, task, latencies) {
  let next = 0
  await Promise.all(
    Array.from({ length: workers }, async () => {
      while (true) {
        const index = next
        next += 1
        if (index >= total) return
        latencies.push(await task())
      }
    }),
  )
}

const session = await login()
await runPool(warmupCount, Math.min(concurrency, 16), () => request(session.accessToken), [])

const latencies = []
const startedAt = performance.now()
await runPool(requestCount, concurrency, () => request(session.accessToken), latencies)
const elapsedSeconds = (performance.now() - startedAt) / 1000
latencies.sort((left, right) => left - right)

const result = {
  path,
  requests: requestCount,
  concurrency,
  requestsPerSecond: round(requestCount / elapsedSeconds),
  latencyMs: {
    average: round(latencies.reduce((sum, value) => sum + value, 0) / latencies.length),
    p50: round(percentile(latencies, 0.5)),
    p95: round(percentile(latencies, 0.95)),
    p99: round(percentile(latencies, 0.99)),
    max: round(latencies.at(-1)),
  },
}

process.stdout.write(`${JSON.stringify(result)}\n`)
