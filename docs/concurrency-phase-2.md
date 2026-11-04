# High-Concurrency Phase 2

## Scope

This phase focuses on the synchronous request path used by directory browsing:

- account logout and avatar management
- authentication session cache
- immediate session revocation through Redis markers
- Tomcat and HikariCP concurrency tuning
- repeatable HTTP load testing

It does not claim multi-region high availability. MySQL, Redis, MinIO, and
Nginx remain single-node services.

## Baseline and Result

Environment: Windows, 8 logical processors, 16 GB RAM, Java 17,
Spring Boot 4.0.6, MySQL 8, Redis 8.10.3, MinIO, Nginx, HTTP keep-alive.

All measurements use `tools/performance-benchmark.mjs`. Each run authenticates,
warms the same endpoint, then measures completed HTTP responses. Post-change
values below are the median of three runs.

### 48 concurrent workers, 1800 requests

| Endpoint | Before req/s | After req/s | Before p95 | After p95 |
| --- | ---: | ---: | ---: | ---: |
| `/api/v1/auth/session` | 908.6 | 1957.8 | 92.0 ms | 37.0 ms |
| `/api/v1/files?limit=80` | 1035.3 | 1652.1 | 75.1 ms | 40.5 ms |

Session-check throughput increased about `115%`; directory-list throughput
increased about `60%`.

### 96 concurrent workers, 3600 requests

- `/api/v1/auth/session`: about `2225 req/s`, p95 `57 ms`
- `/api/v1/files?limit=80`: about `1570 req/s`, p95 `88 ms`
- `/api/v1/health`: median `2501 req/s`

The application currently approaches the local Nginx/Node/Windows test ceiling
on session checks. Directory listing remains lower because it performs session
cache lookup, Redis metadata version lookup, Redis payload lookup, JSON
deserialization, and response serialization.

## Implemented Changes

### Authentication cache

- Session token hashes resolve through Redis before MySQL.
- Cached values never include the password hash.
- Cache misses fall back to the indexed MySQL session query.
- Session cache TTL defaults to 60 seconds.
- `last_seen_at` is updated only on a MySQL cache miss, not on every request.

### Immediate revocation

- Logout writes a revocation marker keyed by the session-token hash.
- The marker remains for the full remaining session lifetime.
- Cached requests check the session and revocation keys together with one
  `MGET`.
- Logout, single-device revocation, and revoke-other-devices all update markers.

### Capacity tuning

- HikariCP maximum pool defaults to 24, minimum idle to 6.
- Tomcat defaults to 200 max threads, 24 minimum spare, accept count 200, and
  8192 max connections.
- All settings can be overridden with `FILEHOME_*` environment variables.

### Avatar consistency

- Avatar images are validated before decoding, limited to 5 MiB and 36 million
  source pixels.
- Images are center-cropped and normalized to a 512 by 512 PNG.
- Objects are stored in MinIO under `avatars/{userId}/avatar`.
- A user-profile event invalidates all cached sessions for that user so an
  uploaded avatar is visible immediately.

## Rejected Experiment

A Redis Lua script combined the metadata-version and metadata-payload reads
into one round trip. At 48 concurrent workers it measured about `1436 req/s`,
slower than the existing two-step implementation at `1554 req/s`. The script
was reverted because server-side script and result-conversion overhead exceeded
the saved network round trip on this deployment.

## Remaining Bottlenecks

1. Directory-read cache deserialization and JSON response serialization.
2. `LIKE '%keyword%'` search on very large datasets; a search engine or MySQL
   full-text index is needed beyond local scale.
3. Recursive move, copy, trash, and ZIP operations still load a user's complete
   tree.
4. Large uploads are streamed by Spring and MinIO but are not resumable.
5. Single-node MySQL, Redis, MinIO, and Nginx remain availability risks.

The next capacity step should be multi-instance backend deployment behind
Nginx, followed by asynchronous recursive jobs and multipart resumable upload.

Phase 4 introduces Kafka-backed asynchronous audit and account/settings
workloads; see `phase-4-kafka-settings-word.md`.
