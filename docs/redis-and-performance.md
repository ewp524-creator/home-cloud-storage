# Redis and Performance Optimization

## Redis Runtime

- Distribution: Redis 8.10.3 for Windows, MSYS2 portable build
- Archive SHA-256:
  `AF16DF340525B431E9EC8DABFD625D5E7A233F82805B5418819904EA909C447F`
- Executable: `tools\redis\8.10.3\Redis-8.10.3-Windows-x64-msys2`
- Bind address: `127.0.0.1:6379`
- Configuration: `config\redis.conf`
- Data directory: `.redis-data`
- Memory limit: 256 MB with `allkeys-lru`

The distribution and runtime data are ignored by Git. Redis is loopback-only;
production use should add authentication, TLS or a protected service network,
monitoring, and managed persistence.

Start or verify Redis:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-redis.ps1
```

`start-filehome.ps1` starts Redis before the backend.

## Cache Design

File metadata is cached as JSON under:

```text
filehome:metadata:version:{userId}
filehome:metadata:{namespace}:{userId}:{version}:{fingerprint}
```

Namespaces cover:

- directory pages
- folder-tree levels
- file details
- recent files
- starred files

Default TTLs:

- file lists and recent/starred: 30 seconds
- trees: 60 seconds
- details: 30 seconds

Every successful file mutation changes the user's random cache version after
the database transaction commits. This avoids stale-read races and avoids
scanning or deleting a user's cache keys one by one.

If Redis is unavailable, metadata reads continue against MySQL. A five-second
retry cooldown prevents every request from paying the Redis connection timeout.

Authenticated session resolution is also cached:

```text
filehome:auth:session:{sha256(session-token)}
filehome:auth:revoked:{sha256(session-token)}
```

The session value excludes password hashes. Both keys are read in one `MGET`;
logout and device revocation write the revocation marker before updating MySQL.
Marker lifetime equals the remaining session lifetime, so cache repopulation
cannot make a revoked session valid again.

## Rate Limiting

Redis uses one atomic Lua script per fixed window:

```text
INCR key
PEXPIRE key on first increment
```

Rate limits are therefore shared by multiple backend instances. If Redis is
down, the limiter falls back to the process-local map for five seconds.

## Measured Local Results

Environment: Windows, Java 17, Spring Boot 4.0.6, MySQL 8, Redis 8.10.3,
small demo dataset, persistent HTTP connections.

- Cached directory list over 200 sequential requests:
  average `5.55 ms`, p50 `5.37 ms`, p95 `7.85 ms`.
- Previous local list baseline: average `9.9 ms`, p95 `11.2 ms`.
- Average sequential latency reduction: about `44%`.
- Cold query `10.86 ms`; second cache hit `3.82 ms`, about `2.85x` faster.
- Equal-concurrency test with 16 workers and 400 requests:
  - MySQL/cache-miss path: `673.9 req/s`, average `23.36 ms`
  - Redis/warm path: `1353.3 req/s`, average `11.70 ms`
  - throughput increase: about `101%`

The measurement is synthetic and does not represent internet latency. The
important production effect is lower MySQL read pressure: a warm list request
performs no database query until its cache entry expires or a mutation occurs.
Repeated local runs showed warm-cache throughput gains between about `2x` and
`3.2x`.

## Expected Production Impact

| Area | Expected improvement |
| --- | --- |
| Repeated directory browsing | 40-50% lower response latency |
| Read throughput for cacheable metadata | 2-3x in the local synthetic test |
| MySQL read queries on cache hits | 0 for covered metadata endpoints |
| `recent` and `starred` with large trees | Avoid full-tree loading; query work scales with result count |
| Upload quota check with large trees | No longer proportional to total file count |
| Multi-instance rate limiting | Correct shared counters instead of per-process limits |
| Actual upload/download transfer time | Not directly improved in this pass |

Overall, typical interactive directory browsing should feel roughly
`1.5x-2x` faster once warm, while MySQL load from repeated reads can drop by
the cache hit ratio. Large-file transfer speed is dominated by MinIO, Nginx, and
the network, not Redis.

See `concurrency-phase-2.md` for the authenticated high-concurrency comparison.
