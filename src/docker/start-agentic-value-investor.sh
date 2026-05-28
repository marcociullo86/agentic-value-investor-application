#!/usr/bin/env bash
# Bootstrap helper for the Podman dev stack + Portainer on macOS/Linux.
#
# Flow:
#   1. Ask whether to rebuild the vi-app image.
#      - If YES: podman build -> compose down -> compose up -d, then Portainer.
#      - If NO : skip directly to Portainer (assumes stack is already up).
#   2. Always (re)start vi-portainer via `podman run`.
#
# Run from anywhere:
#   ./src/docker/start-agentic-value-investor.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="src/docker/docker-compose.yml"
DOCKERFILE="src/docker/Dockerfile"
NETWORK="docker_default" # auto-created by compose from folder name

cd "${REPO_ROOT}"

log_step() {
  printf '>> %s\n' "$1"
}

run_cmd() {
  local label="$1"
  shift
  log_step "${label}"
  "$@"
}

run_cmd_allow_fail() {
  local label="$1"
  shift
  log_step "${label}"
  "$@" || true
}

resolve_podman_socket() {
  local remote_socket=""
  local socket=""
  remote_socket="$(podman info --format '{{.Host.RemoteSocket.Path}}' 2>/dev/null || true)"
  if [[ -n "${remote_socket}" ]]; then
    remote_socket="${remote_socket#unix://}"
    if [[ -n "${remote_socket}" ]]; then
      printf '%s' "${remote_socket}"
      return
    fi
  fi

  local candidates=(
    "${HOME}/.local/share/containers/podman/machine/podman.sock"
    "/run/user/1000/podman/podman.sock"
  )

  for candidate in "${candidates[@]}"; do
    if [[ -S "${candidate}" || -L "${candidate}" || -e "${candidate}" ]]; then
      if [[ -L "${candidate}" ]]; then
        socket="$(readlink "${candidate}")"
      else
        socket="${candidate}"
      fi
      break
    fi
  done

  if [[ -z "${socket}" ]]; then
    echo "Errore: socket Podman non trovato. Avvia podman machine prima di eseguire lo script." >&2
    exit 1
  fi

  printf '%s' "${socket}"
}

if podman compose version >/dev/null 2>&1; then
  COMPOSE_BIN=(podman compose)
elif command -v podman-compose >/dev/null 2>&1; then
  COMPOSE_BIN=(podman-compose)
else
  echo "Errore: installa 'podman compose' o 'podman-compose'." >&2
  exit 1
fi

read -r -p "Vuoi buildare l'app prima di avviare lo stack? (y/N) " answer
answer="${answer:-N}"
if [[ "${answer}" =~ ^([yY]|yes|YES|s|S|si|SI)$ ]]; then
  run_cmd "podman build vi-app:latest" \
    podman build -f "${DOCKERFILE}" -t vi-app:latest .

  run_cmd_allow_fail "compose down" \
    "${COMPOSE_BIN[@]}" -f "${COMPOSE_FILE}" down

  run_cmd "compose up -d" \
    "${COMPOSE_BIN[@]}" -f "${COMPOSE_FILE}" up -d
else
  echo "Skip build/restart - using existing running stack."
fi

if podman ps -a --filter 'name=^vi-portainer$' --format '{{.Names}}' | grep -qx 'vi-portainer'; then
  run_cmd "remove existing vi-portainer" podman rm -f vi-portainer >/dev/null
fi

PODMAN_SOCKET="$(resolve_podman_socket)"

if ! podman network exists "${NETWORK}"; then
  run_cmd "create network ${NETWORK}" podman network create "${NETWORK}" >/dev/null
fi

run_cmd "podman run vi-portainer" \
  podman run -d \
    --name vi-portainer \
    --restart unless-stopped \
    --network "${NETWORK}" \
    -p 9000:9000 \
    -p 9443:9443 \
    -v "${PODMAN_SOCKET}:/var/run/docker.sock" \
    -v portainer-data:/data \
    portainer/portainer-ce:latest >/dev/null

echo
echo "Done."
echo "Portainer UI: http://localhost:9000 (HTTPS: https://localhost:9443)"
podman ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
