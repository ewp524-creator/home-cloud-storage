#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

compose() {
    docker compose --env-file "$ROOT/.env" -f "$ROOT/compose.yaml" "$@"
}

secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 32
    else
        od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
    fi
}

replace_value() {
    local old="$1"
    local new="$2"
    sed -i "s|${old}|${new}|g" "$ROOT/.env"
}

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        echo "Docker is required. Install Docker Engine first." >&2
        exit 1
    fi
    if ! docker compose version >/dev/null 2>&1; then
        echo "Docker Compose v2 is required (docker compose)." >&2
        exit 1
    fi
    if ! docker info >/dev/null 2>&1; then
        echo "Cannot access Docker. Start Docker and check the current user's permissions." >&2
        exit 1
    fi
}

ensure_env() {
    if [[ ! -f "$ROOT/.env" ]]; then
        cp "$ROOT/.env.example" "$ROOT/.env"
        chmod 600 "$ROOT/.env"
        echo "Created .env with generated local secrets."
    fi
    replace_value "change-me-db" "$(secret)"
    replace_value "change-me-root" "$(secret)"
    replace_value "change-me-redis" "$(secret)"
    replace_value "change-me-jwt" "$(secret)"
    replace_value "change-me-share" "$(secret)"
    replace_value "change-me-minio" "$(secret)"
}

wait_for_backend() {
    echo "Waiting for backend health check..."
    for _ in $(seq 1 60); do
        if compose exec -T backend curl -fsS \
            http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
            echo "Backend is ready."
            return 0
        fi
        sleep 2
    done
    echo "Backend did not become healthy. Run: ./scripts/filehome.sh logs backend" >&2
    return 1
}

show_urls() {
    local http_port
    http_port="$(grep -E '^FILEHOME_HTTP_PORT=' "$ROOT/.env" | cut -d= -f2)"
    http_port="${http_port:-80}"
    echo
    echo "File Home is running:"
    echo "  Web:          http://localhost:${http_port}/"
    echo "  Backend API:  http://127.0.0.1:8080/api/v1/health"
    echo "  MinIO console http://127.0.0.1:9001/"
    echo
    compose ps
}

start_services() {
    require_docker
    ensure_env
    compose config --quiet
    compose up -d --build
    wait_for_backend
    show_urls
}

stop_services() {
    require_docker
    compose down
    echo "File Home stopped. Persistent volumes were kept."
}

show_status() {
    require_docker
    compose ps
    if compose exec -T backend curl -fsS \
        http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
        echo "Backend: UP"
    else
        echo "Backend: DOWN"
    fi
}

case "${1:-start}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        stop_services
        start_services
        ;;
    status)
        show_status
        ;;
    logs)
        shift
        compose logs --tail=200 -f "$@"
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs [service]}" >&2
        exit 2
        ;;
esac
