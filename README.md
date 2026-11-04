# File Home

File Home is a private cloud file manager built with Spring Boot 4, Vue 3,
MySQL, MinIO, Redis, and Nginx. It also includes `fhterm`, an authenticated
command-line terminal that runs directly in `cmd.exe`, PowerShell, Git Bash, and
Linux shells.

The current release includes revocable browser sessions, CSRF protection, avatar
management, versioned metadata caching, and a measured single-node concurrency
profile. Large files use resumable, checksummed MinIO multipart uploads; see
`docs/multipart-upload.md`.

Phase 4 adds Kafka-backed asynchronous audit, account settings and deactivation,
adaptive retry backoff, and secure read-only DOCX preview. See
`docs/phase-4-kafka-settings-word.md`.

## Windows Quick Start

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\filehome.ps1 start
```

Control the local services with:

```powershell
.\scripts\filehome.ps1 status
.\scripts\filehome.ps1 restart
.\scripts\filehome.ps1 stop
```

Open the command-line terminal from `cmd.exe` or PowerShell:

```bat
.\trueterminal\windows\fhterm.cmd
```

From Git Bash or Linux:

```bash
cd trueterminal
./linux/fhterm
```

The existing `scripts\start-filehome.ps1` and `scripts\stop-filehome.ps1`
scripts remain available for direct use.

### Storage location

Each installation chooses its own local object-storage directory. Set
`FILEHOME_WINDOWS_STORAGE` before starting the services, or put the path in the
ignored file `.secrets\storage.path`. The default is `data\storage`.

```powershell
$env:FILEHOME_WINDOWS_STORAGE = "D:\your-storage-directory"
powershell -ExecutionPolicy Bypass -File .\scripts\filehome.ps1 start
```

## Linux Deployment

### Requirements

- Linux x86_64 or ARM64
- Docker Engine 24 or newer
- Docker Compose v2
- At least 4 GB RAM recommended

### One-command start

```bash
chmod +x scripts/filehome.sh scripts/start-linux.sh scripts/stop-linux.sh
./scripts/filehome.sh start
```

On first start the script creates `.env`, generates random database, Redis,
JWT, share-token, and MinIO secrets, builds all images, waits for the backend
health check, and starts:

- web application on port `80`
- backend health endpoint on `127.0.0.1:8080`
- MinIO console on `127.0.0.1:9001`
- MySQL, Redis, and MinIO internal services on a private Docker network

Open `http://SERVER_IP/`. The MinIO console is intentionally bound to loopback;
use an SSH tunnel when remote console access is required.

### Common commands

```bash
./scripts/filehome.sh status
./scripts/filehome.sh logs backend
./scripts/filehome.sh restart
./scripts/filehome.sh stop
```

Stopping preserves the `mysql_data`, `redis_data`, `minio_data`, and
`backend_logs` Docker volumes.

### Configuration

The deployment reads `.env`. To change ports or use a public domain:

```bash
nano .env
./scripts/filehome.sh restart
```

Set `FILEHOME_SHARE_BASE_URL` to the externally reachable origin, for example:

```text
FILEHOME_SHARE_BASE_URL=https://files.example.com
```

For production, terminate TLS at Nginx or an upstream load balancer, restrict
port `8080` and the Docker socket, add off-host backups, and use Docker secrets
or a secret manager instead of a plain `.env` file. Set
`FILEHOME_COOKIE_SECURE=true` when the public origin uses HTTPS.

### Data locations

- MySQL: Docker volume `mysql_data`
- Redis append-only data: Docker volume `redis_data`
- MinIO objects: Docker volume `minio_data`
- Backend logs: Docker volume `backend_logs`

## Architecture

```text
Browser
  -> Nginx / Vue static application
  -> Spring Boot REST API
  -> MySQL metadata
  -> Redis cache and rate limits
  -> Kafka asynchronous audit events
  -> MinIO object contents
```

The backend binds to loopback by default for local development. Docker Compose
overrides the bind address to `0.0.0.0` only inside the private container
network.
