# Security and Performance Review

## Completed Security Improvements

- Replaced hard-coded runtime secrets with ignored local environment values.
- Split JWT signing and share-token secrets so they can be rotated independently.
- Added JWT issuer validation and reduced the default lifetime from 24 hours to 2 hours.
- Rejects disabled accounts and malformed or oversized bearer tokens.
- Added fixed-window rate limits for login, registration, share metadata, code verification, and download.
- Increased password hashing cost and bounded password length.
- New share extraction codes use `crypto.getRandomValues` and PBKDF2.
- Legacy extraction codes remain valid through a constant-time compatibility path.
- Removed owner account, email, and quota fields from public share responses.
- Enforced upload quota, bounded MIME types, and retained filename path sanitization.
- Reduced CORS to fixed local origins and enabled credentials only for those
  explicit origins.
- Bound Spring Boot and Nginx to loopback by default.
- Added CSP and standard browser security headers.
- Added HMAC-signed, file-bound preview tickets with a 10-minute lifetime.
- Media previews use streamed MinIO ranges, so video seeking does not load the
  complete object into application memory.
- Preview tickets are scope-bound to the owner or share and are checked against
  the current share row on every request; revoking a share invalidates already
  issued tickets immediately.
- Text and source previews are capped at 2 MiB on the client, rendered with a
  safe escaped highlighter, and served as `text/plain` with `nosniff`.
- Nginx does not retain signed preview query strings in the local access log;
  the container deployment disables access logging for the media route.
- Added focused automated tests for preview ticket signatures, byte ranges,
  format detection, password hashing, and rate limiting.
- Added `fhterm`, an authenticated command-line client that runs in `cmd.exe`,
  PowerShell, Git Bash, and Linux shells.
- The CLI validates credentials against the existing File Home login endpoint,
  hides password input, stores only the JWT session, and supports cleanup with
  the `logout` command.
- Replaced browser JWT storage in `localStorage` with opaque, revocable MySQL
  sessions delivered through an HttpOnly cookie.
- Added HMAC-based CSRF validation for every cookie-authenticated state change,
  including uploads, share changes, file mutations, and logout.
- Added session listing and revocation APIs, immediate server-side logout,
  account-disable enforcement, and scheduled expired-session cleanup.
- Kept a separate rate-limited CLI login endpoint so command-line tokens never
  need to be exposed to browser JavaScript.
- Added an explicit logout confirmation in the profile and sidebar controls.
- Added avatar upload, bounded image decoding, center-crop normalization to PNG,
  MinIO storage, private HTTP caching, and immediate session-cache invalidation.

## Remaining Security Work

- Redis rate limiting is shared across processes; in-memory limiting remains a
  five-second fail-open fallback when Redis is unavailable.
- There is no email verification, MFA, password reset, refresh-token rotation, or server-side token revocation.
- Uploaded files are not malware scanned.
- Production should use a dedicated MySQL user with least privilege instead of `root`.
- TLS, WAF, centralized audit logs, and retention jobs for share access logs are not configured.
- Large uploads use resumable MinIO multipart sessions with 16 MiB parts, four
  concurrent transfers, per-part SHA-256 checks, Redis part locks, quota
  reservations, and owner-bound completion.
- Office and archive content still has no server-side conversion/listing
  pipeline. The UI reports these formats honestly instead of displaying fake
  previews.

## Local Performance Snapshot

Measured on the development machine against a small demo account:

- `/api/v1/health`: average 1.8 ms, p95 4.8 ms.
- `/api/v1/files`: average 11.6 ms, p95 15.9 ms over 20 sequential requests.
- Login: median 340.6 ms, dominated by PBKDF2 password verification.

After the P0 optimization pass, an isolated benchmark with 2005 items in one
folder and an 80-item page produced:

- Average: 51.9 ms
- p50: 42.9 ms
- p95: 95.2 ms
- Response size: 80 items instead of all 2005

The benchmark data was removed immediately after measurement.

These numbers verify that the local request path is responsive, not that the
application is production-scale.

## Implemented Performance Work

- Directory lists use keyset pagination with a maximum of 200 items per request.
- Search, type filtering, tag filtering, and sorting execute in MySQL.
- Folder and file counts use one aggregate query.
- Child counts are loaded in one batched query per page.
- Breadcrumbs use one recursive ancestor query.
- The navigation tree loads one folder level at a time.
- Share lists batch root entries, child counts, and access counters.
- The authenticated user loaded by the interceptor is reused within the request.
- `idx_file_entry_page` supports owner, parent, deletion, update-time, and ID ordering.
- File lists, trees, details, recent files, and starred files are cached in
  Redis as JSON with per-user versioned keys.
- Cache versions are invalidated in an `afterCommit` transaction callback, so a
  concurrent reader cannot repopulate a new cache generation with pre-commit data.
- Recent and starred lists use dedicated indexed SQL instead of loading the
  complete user tree.
- Upload quota checks use one aggregate query instead of loading all entries.
- HikariCP and Connector/J prepared-statement caches are tuned for repeated
  metadata queries.
- Authentication session resolution uses a 60-second Redis cache with full-lifetime
  revocation markers, eliminating MySQL from warm authenticated requests.
- HikariCP and Tomcat concurrency limits are configurable for larger deployments.

## Redis Optimization Snapshot

Redis 8.10.3 was tested locally on `127.0.0.1:6379` with the demo account.
Measurements use persistent HTTP connections and report warm-cache results.

- Cached directory list, 200 sequential requests:
  average `5.55 ms`, p50 `5.37 ms`, p95 `7.85 ms`.
- Previous local list measurement: average `9.9 ms`, p95 `11.2 ms`.
- Sequential average latency reduction: about `44%`; p95 reduction: about `30%`.
- Cold query `10.86 ms`; second cache hit `3.82 ms`, about `2.85x` faster.
- Equal-concurrency comparison, 16 workers and 400 requests:
  - cache miss/MySQL path: `673.9 req/s`, average `23.36 ms`, p95 `31.02 ms`
  - warm Redis path: `1353.3 req/s`, average `11.70 ms`, p95 `15.45 ms`
  - throughput increase: about `101%`

Repeated local runs produced warm-cache throughput gains between roughly `2x`
and `3.2x`; the lower result is the conservative planning figure.

These are local synthetic measurements with a small demo dataset. They do not
represent Internet latency or production capacity. The main production benefit
is reduced MySQL load: a cache-covered request performs zero MySQL reads while
the cached response is valid.

## Main Scaling Limits

- Recursive operations such as copy, trash, and ZIP creation still load a user's full tree and should be replaced with recursive subtree queries.
- Search uses `LIKE '%keyword%'`, so very large directories need a full-text or search-engine index.
- Spring multipart uploads and MinIO transfers are streamed, but the browser still sends one all-or-nothing request.
- PBKDF2 intentionally consumes CPU; rate limiting is required to prevent login flooding.
- Cache correctness depends on all mutations passing through the application;
  direct database edits require an explicit cache-version invalidation.
- The frontend uses explicit "load more" rather than virtualized rendering; very large loaded pages can still create many DOM nodes.

The next meaningful performance step is pagination plus targeted subtree queries,
not a different ORM. MyBatis improves query isolation and lets index-backed
lookups replace the previous load-all-and-filter behavior for single entries.
