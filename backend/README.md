# File Home Backend

Spring Boot 4.0.6 backend for the File Home project.

## Runtime

- Java 17
- Maven Wrapper 3.10
- MySQL 8 at `127.0.0.1:3306`
- Database: `file_home`
- Redis 8.10.3 at `127.0.0.1:6379`
- MinIO endpoint: `127.0.0.1:9000`
- MinIO console: `127.0.0.1:9001`
- Object bucket: `file-home`
- MinIO data directory: configured by `FILEHOME_WINDOWS_STORAGE`,
  `.secrets\storage.path`, or `FILEHOME_STORAGE_DATA_ROOT`
  (default: `data\storage`)
- Persistence: MyBatis 3.5.19 with Spring Boot starter 4.0.0

Start all services from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-filehome.ps1
```

Start the backend alone:

```powershell
cd backend\backend
powershell -ExecutionPolicy Bypass -File ..\..\scripts\run-backend.ps1 -Port 8080
```

Start Redis alone:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-redis.ps1
```

The portable Redis distribution is installed under `tools\redis`, and its
loopback-only configuration is in `config\redis.conf`. Redis data and logs are
ignored by Git.

Runtime secrets are loaded from `.secrets\filehome.env.ps1`, which is ignored by
Git. Production deployments should provide the `FILEHOME_*` variables through
the deployment environment or a secret manager.

## Module Boundaries

```text
com.filehome
├─ auth       Authentication, JWT, password hashing, request identity
├─ user       Account registration and user-scoped root directories
├─ file       File tree, upload, download, ZIP, move/copy, trash
├─ storage    Object storage port and MinIO adapter
├─ share      Random-token sharing and public access
├─ security   Client identity and request rate limiting
├─ persistence MyBatis type handlers
├─ config     Application properties, CORS, MyBatis, MinIO and interceptors
└─ common     API errors and health endpoint
```

The file module depends on `ObjectStoragePort`, not directly on MinIO. This keeps
storage concerns isolated from file-tree business rules.

Repository interfaces are MyBatis mappers. SQL and explicit record-constructor
mappings live under `src/main/resources/mappers`.

## Performance

- Directory listing uses cursor pagination and defaults to 80 items.
- Search, filtering, and sorting run in MySQL.
- File lists, folder trees, details, recent files, and starred files use
  versioned Redis JSON caches with short TTLs.
- Any successful file mutation changes the user's cache version after the
  database transaction commits, preventing stale reads.
- Folder navigation is loaded lazily one level at a time.
- Storage counts and page child counts use aggregate or batched queries.
- Recent and starred files use dedicated indexed queries instead of loading the
  user's complete tree.
- Upload quota checks use an aggregate storage query instead of loading every
  file entry.
- The composite `idx_file_entry_page` index supports directory ordering.
- HikariCP and MySQL prepared-statement caches are tuned for the local service.
- Redis rate limiting is shared across backend instances and falls back to an
  in-memory limiter when Redis is unavailable.

See `../docs/security-and-performance.md` for measurements and remaining limits.

## User Root Directory

Registration is transactional:

1. Username and email uniqueness are validated.
2. The user account is created.
3. A root `FOLDER` entry named exactly after the username is created.
4. MinIO receives a `username/.filehome-root` marker.
5. All user objects are stored under the username prefix.

The generated unique root index prevents a user from receiving multiple root
folders.

## Database

Schema: `src/main/resources/db/schema.sql`

The standalone schema is also available at
`database/file_home_schema.sql`.

The current MySQL server is `8.0.4-rc`. Its generated-column/self-reference
combination rejects the file-tree foreign key, so that constraint is enforced
in the service layer. Other ownership and share relationships remain protected
by database constraints.

## Sharing

- Public URL format: `/s/{token}`.
- The token is random and contains no file ID.
- Only the token SHA-256 hash is stored.
- Access codes are stored as salted SHA-256 hashes.
- Folder shares are recursive through the root entry relationship.
- A user can have only one share record per file or folder; sharing again updates
  and can revive that record instead of inserting a duplicate.
- Access codes are stored as PBKDF2 hashes for validation and encrypted with
  AES-GCM for authenticated owner display in the share center.
- File listings expose the real active-share state so cards and context menus
  can show management and cancellation actions.
- Anonymous visitors may view share metadata and previews.
- Download requires `download_requires_auth = 1` and a valid login.
- New extraction codes use PBKDF2; legacy SHA-256 hashes remain verifiable.
- Public share metadata never includes the owner's email, account ID, or quota.

## Security Controls

- Login, registration, public share metadata, verification, and download are rate limited.
- JWT tokens have an issuer and a two-hour default lifetime.
- Disabled accounts are rejected even when they still hold an unexpired token.
- Upload quota, filename, and MIME type are validated server-side.
- CORS is allow-listed, credentials are disabled, and the backend binds to loopback.
- Nginx adds CSP, anti-framing, MIME-sniffing, referrer, and permissions headers.
