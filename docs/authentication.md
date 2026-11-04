# Authentication Architecture

## Phase 1 Scope

The browser and command-line clients use separate authentication channels:

- Browsers use an opaque session backed by MySQL and delivered in the
  `FH_SESSION` HttpOnly cookie.
- `fhterm` uses a short-lived signed Bearer token issued by
  `POST /api/v1/auth/cli/login`.
- The browser login endpoints no longer return an access token to JavaScript.

This separation keeps the browser credential unavailable to page scripts while
preserving the existing command-line workflow.

## Browser Session Flow

1. `GET /api/v1/auth/captcha` returns a one-time image challenge and captcha ID.
   The answer is stored in Redis for two minutes and is never returned.
2. `POST /api/v1/auth/login` verifies the captcha, then verifies the password and creates an
   `auth_session` row.
3. The response sets `FH_SESSION` as `HttpOnly` and `FH_CSRF` as a
   JavaScript-readable CSRF cookie. Both use `SameSite=Lax`, `Path=/`, and the
   configured `Secure` flag.
4. The database stores only the SHA-256 hash of the random session token.
5. Authenticated requests validate the session hash, expiry, revocation state,
   and account status through Redis. A cache miss falls back to one indexed
   MySQL query.
6. Requests using cookie authentication and a non-safe HTTP method must send
   `X-CSRF-Token`. The server derives the expected value with HMAC-SHA-256 from
   the session token and a domain-separated signing key.
7. `POST /api/v1/auth/logout` revokes the current database session immediately
   and expires both cookies.

Expired and long-revoked sessions are removed by a scheduled cleanup job.
Revocation markers remain until the session would have expired, so logout is
immediate even if a concurrent request repopulates the session cache.

## Session Management API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/auth/captcha` | Issue a two-minute, one-time graphical captcha |
| `GET` | `/api/v1/auth/session` | Restore the current browser session |
| `POST` | `/api/v1/auth/logout` | Revoke the current session |
| `GET` | `/api/v1/auth/sessions` | List active sessions |
| `DELETE` | `/api/v1/auth/sessions/{id}` | Revoke one owned session |
| `POST` | `/api/v1/auth/sessions/revoke-others` | Revoke all other sessions |

Session-list responses contain a device label (`User-Agent`), timestamps, and a
`current` flag. Token hashes and IP hashes are never returned.

## Configuration

| Variable | Default | Notes |
| --- | --- | --- |
| `FILEHOME_SESSION_HOURS` | `12` | Absolute browser session lifetime |
| `FILEHOME_COOKIE_SECURE` | `false` | Set to `true` behind production HTTPS |
| `FILEHOME_SESSION_CACHE_SECONDS` | `60` | Redis session-resolution cache TTL |
| `FILEHOME_JWT_MINUTES` | `120` | CLI Bearer token lifetime |

`FILEHOME_COOKIE_SECURE=true` is mandatory for an Internet-facing deployment.
Local HTTP development must use `false`.

## Module Boundaries

- `AuthController` handles HTTP contracts and rate limits.
- `AuthService` owns password authentication and CLI token issuance.
- `CaptchaService` owns image generation, one-time challenge storage, and
  constant-time answer comparison.
- `AuthSessionService` owns session lifecycle and revocation.
- `SessionResolutionCache` owns the Redis session adapter and revocation
  markers.
- `AuthCookieService` is the only browser Cookie adapter.
- `RequestAuthenticator` resolves either session or Bearer credentials.
- `AuthSessionRepository` owns session persistence.
- The MVC interceptor enforces authentication and CSRF policy.

Controllers and file services do not parse JWTs, read Cookies, or query session
rows directly.
