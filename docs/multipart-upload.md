# Multipart Large-File Upload

## Design

File Home uses MinIO S3 multipart upload. The browser and `fhterm` never receive
MinIO credentials or the MinIO upload ID. Spring Boot coordinates the session
and streams each part to MinIO with backpressure.

Default policy:

| Setting | Default |
| --- | --- |
| Multipart threshold in clients | 8 MiB |
| Part size | 16 MiB |
| Concurrent parts | 4 |
| Maximum parts | 10,000 |
| Session lifetime | 48 hours |
| Active sessions per user | 4 |
| Server maximum file size | 20 GiB |

All values except the client threshold and concurrency can be overridden with
`FILEHOME_UPLOAD_*` environment variables.

## Integrity and Security

1. The upload session is authenticated, owner-bound, quota-reserved, and
   limited by session count.
2. File names are sanitized, content types are bounded, and users cannot write
   outside their own MinIO prefix.
3. Every non-final part must have the server-issued exact size.
4. The client sends `X-Chunk-SHA256` for every part.
5. The server computes SHA-256 over the same bytes while streaming them to
   MinIO and rejects a mismatch before recording the part.
6. MinIO returns the part ETag; the browser cannot forge it.
7. A Redis lock allows only one writer for the same session part across backend
   instances. Different parts still upload concurrently.
8. Completion verifies continuous part numbers, part sizes, total part count,
   final object size, and target-folder ownership.
9. Existing partial parts are re-hashed when a file is reselected. A file that
   changed but has the same name and size will not be silently resumed.

The local development endpoint is HTTP. Internet-facing deployments must use
TLS end to end so request bodies and session cookies are encrypted in transit.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/uploads` | Create an upload session |
| `GET` | `/api/v1/uploads` | List active sessions for resume |
| `GET` | `/api/v1/uploads/{id}` | Read status and confirmed parts |
| `PUT` | `/api/v1/uploads/{id}/parts/{partNumber}` | Stream one raw part |
| `POST` | `/api/v1/uploads/{id}/complete` | Complete and create the file entry |
| `DELETE` | `/api/v1/uploads/{id}` | Abort and release the reservation |

Raw part requests use:

```text
Content-Type: application/octet-stream
X-Chunk-SHA256: 64 lowercase or uppercase hexadecimal characters
```

Cookie-authenticated writes also require `X-CSRF-Token`.

## Resume

The client fingerprints `name + size + lastModified + parentId`. It queries
active sessions, verifies the SHA-256 of every part already on the server, and
only then skips those parts. Failed retries for the same part are idempotent
because the server overwrites that part and replaces its database ETag.

Completed sessions are retained for seven days for audit/recovery and then
deleted by the cleanup job. Aborted and expired sessions release quota
immediately.

If the backend restarts while a browser upload is open, the client keeps the
part slots and retries network failures for roughly 45 seconds with exponential
backoff. The upload tray reports the current retry attempt. If the outage lasts
longer than that, the upload is marked failed but the server session and all
confirmed parts remain.

To resume after a long outage, refresh or reopen the page and select the same
file from the same account and parent folder. File Home re-hashes every part
already recorded by the server before continuing. If the local file was
replaced or modified, the fingerprint/hash check rejects the old session and a
new session is created instead.

## Local Verification

`tools/multipart-upload-smoke.mjs` performs a real end-to-end test:

1. Log in and create an upload session.
2. Deliberately submit a bad part checksum and verify HTTP 400.
3. Upload the first part with the correct checksum.
4. Read the session and verify the confirmed part is resumable.
5. Upload remaining parts concurrently and complete the object.
6. Download the final file and compare the full SHA-256.
7. Delete the test file.

A 128 MiB file with nine parts completed the verified upload stage in
1.299-3.103 seconds across local runs, about 40.0-51.3 MiB/s on the single-node
Windows setup. This includes SHA-256 computation, HTTP transfer, MinIO
multipart writes, database updates, and final object assembly. It is a
functional throughput check, not a WAN or production capacity benchmark.

`fhterm upload` uses the same protocol for files at or above 8 MiB.
