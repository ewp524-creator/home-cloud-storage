# Phase 4: Kafka, Account Settings, and DOCX Preview

## Retry Policy

There is no single official retry count for the Japanese market. Japanese
enterprise software generally follows the same reliability rules used
internationally: retry only transient/idempotent operations, use exponential
backoff with jitter, and cap the total recovery window.

Typical enterprise defaults are:

- 3 attempts for ordinary idempotent API calls
- 5-8 attempts for resumable upload or other long-running user operations
- exponential backoff with random jitter
- no retry for 4xx validation or authorization failures
- explicit user-visible failure after the recovery window

File Home uses up to 8 network attempts for an interrupted multipart part. The
maximum planned backoff is about 46 seconds and the minimum is about 23 seconds
because every delay is jittered. The server upload session remains resumable
after this window.

## Kafka

Local Windows installation:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-kafka.ps1
```

Runtime:

- Apache Kafka 4.4.0, KRaft mode
- broker `127.0.0.1:9092`
- controller `127.0.0.1:9093`
- data directory `.kafka-data`
- topic `filehome.activity.v1`, 6 partitions

Docker Compose uses the matching Kafka 4.1.2 client protocol with the
`apache/kafka` image.

### Audit flow

1. A file operation commits its metadata transaction.
2. `ActivityEventPublisher` sends an event after commit.
3. Kafka stores the event.
4. Three consumer workers write `activity_log` rows.
5. `event_id` is unique, making redelivery idempotent.
6. If Kafka cannot be reached, the publisher falls back to a direct database
   insert.

Activity audit is deliberately not a foreign-key child of `file_entry`. Audit
history must survive deletion of the referenced file.

## Account Settings

New APIs:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/users/me/settings` | Profile and preferences |
| `PATCH` | `/api/v1/users/me/settings` | View, sort, motion, upload concurrency |
| `PATCH` | `/api/v1/users/me/profile` | Display name and email |
| `POST` | `/api/v1/users/me/password` | Change password and revoke other devices |
| `POST` | `/api/v1/users/me/deactivate` | Disable account with password and username confirmation |

Deactivation disables login and revokes all browser sessions. File data is
retained for recovery and audit rather than physically deleted immediately.

## DOCX Preview

- `.docx` files are rendered read-only in the browser with `docx-preview`.
- Rendering code is lazy-loaded in a separate 172 KiB chunk.
- Files larger than 30 MiB are rejected in the renderer and remain downloadable.
- `javascript:`, iframe, object, embed, and non-data image URLs are removed after
  rendering.
- Preview access still uses the existing short-lived signed ticket and ownership
  checks.
- Legacy `.doc` files are not parsed. They display an honest unsupported message.

The preview smoke tool validates real DOCX ZIP structure, content type, ticket,
and raw object access:

```powershell
node .\tools\docx-preview-smoke.mjs
```

## Performance Work

- Audit DB writes moved off the request path in the normal Kafka path.
- Producer uses idempotence, LZ4 compression, batching, and bounded retry
  timing.
- Consumer uses three workers.
- Word renderer is lazy-loaded only when needed.
- Per-user upload concurrency can be reduced from 4 to 1 without changing code.
- Retry backoff now uses jitter to prevent synchronized reconnect storms.

## Remaining Production Work

- Run at least three Kafka brokers with replication factor 3.
- Add Schema Registry and versioned event contracts.
- Move recursive file jobs and media conversion to Kafka workers.
- Add ClamAV and document sanitization before Word preview.
- Add TLS and SASL for Kafka outside a private network.

## Verification

- Kafka topic `filehome.activity.v1` is created with six partitions.
- A real CREATE_FOLDER and DELETE pair was consumed and persisted through Kafka.
- Duplicate protection uses the unique `event_id`.
- A temporary account completed: register, default settings read, preference
  update, profile update, password change, deactivation, and rejected login.
- A real 28,591-byte DOCX returned preview kind `word`, the correct Office MIME
  type, a valid ZIP signature, and a short-lived preview ticket.
- Backend tests: 29 passed.
- CLI tests: 3 passed.
- Frontend production build passed and emits DOCX rendering as a separate lazy
  chunk.
