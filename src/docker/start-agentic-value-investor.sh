#!/usr/bin/env bash
# Bootstrap helper for the Podman dev stack + Portainer on macOS/Linux.
#
# Flow:
#   1. Ask whether to rebuild the vi-app image.
#      - If YES: chiede CPU/GPU (scelta RUNTIME del sidecar → override compose,
#        applicato anche senza rebuild) e se ribuildare il sidecar (opzionale),
#        poi: podman build vi-app -> compose down -> [compose build sidecar] ->
#        compose up -d (con override GPU se scelto). Se GPU, verifica CUDA.
#      - If NO : chiede se RICREARE i container per applicare nuove variabili di
#        .env (up -d --force-recreate app: le env sono fissate alla creazione,
#        un restart NON le rilegge). Se no, fa solo `restart` dei container
#        esistenti (bounce, CPU/GPU invariati). Poi Portainer.
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
  # CPU oppure GPU: scelta RUNTIME del sidecar. L'override docker-compose.gpu.yml
  # (devices: nvidia.com/gpu=all) va applicato a down/up -d A PRESCINDERE dal
  # rebuild, altrimenti il container riparte in CPU. GPU richiede, una tantum
  # nella podman machine, nvidia-container-toolkit + spec CDI. Default CPU.
  read -r -p "Sidecar embeddings: CPU o GPU? (cpu/GPU) [default CPU] " gpu_answer
  COMPOSE_ARGS=(-f "${COMPOSE_FILE}")
  use_gpu=0
  if [[ "${gpu_answer}" =~ ^([gG]|gpu|GPU)$ ]]; then
    use_gpu=1
    COMPOSE_ARGS+=(-f "src/docker/docker-compose.gpu.yml")
    echo "Sidecar: GPU (override docker-compose.gpu.yml, CDI nvidia.com/gpu=all)"
  else
    echo "Sidecar: CPU"
  fi

  # Rebuild del sidecar: opzionale e indipendente da CPU/GPU (serve solo se hai
  # cambiato embeddings-sidecar/app.py o il suo Dockerfile). Default NO.
  read -r -p "Vuoi ribuildare il sidecar embeddings? (y/N) " sidecar_answer
  sidecar_answer="${sidecar_answer:-N}"
  rebuild_sidecar=0
  if [[ "${sidecar_answer}" =~ ^([yY]|yes|YES|s|S|si|SI)$ ]]; then
    rebuild_sidecar=1
  fi

  run_cmd "podman build vi-app:latest" \
    podman build -f "${DOCKERFILE}" -t vi-app:latest .

  run_cmd_allow_fail "compose down" \
    "${COMPOSE_BIN[@]}" "${COMPOSE_ARGS[@]}" down

  # Rebuild del solo sidecar: recepisce le modifiche a embeddings-sidecar/app.py
  # (l'ultimo layer e' la COPY di app.py -> rebuild veloce; il download del
  #  modello resta cache-ato nei layer precedenti). Skippato se non richiesto.
  if [[ "${rebuild_sidecar}" -eq 1 ]]; then
    run_cmd "compose build embeddings-sidecar" \
      "${COMPOSE_BIN[@]}" "${COMPOSE_ARGS[@]}" build embeddings-sidecar
  else
    echo "Skip rebuild sidecar embeddings - uso immagine esistente."
  fi

  run_cmd "compose up -d" \
    "${COMPOSE_BIN[@]}" "${COMPOSE_ARGS[@]}" up -d

  # Se GPU: verifica che il sidecar veda davvero CUDA, altrimenti avvisa.
  if [[ "${use_gpu}" -eq 1 ]]; then
    echo "Verifica CUDA nel sidecar..."
    sleep 8
    cuda="$(podman exec vi-embeddings-sidecar python -c 'import torch; print(torch.cuda.is_available())' 2>/dev/null || true)"
    if [[ "${cuda}" == *True* ]]; then
      echo "Sidecar GPU OK: torch.cuda.is_available() = True"
    else
      echo "ATTENZIONE: sidecar NON vede la GPU (cuda_available=${cuda}). Gira in CPU."
      echo "  Controlla: nvidia-ctk cdi list  (deve elencare nvidia.com/gpu=all)"
    fi
  fi
else
  # Niente build. Chiedi se RICREARE i container: necessario per iniettare nel
  # container le NUOVE variabili di .env (le env sono fissate alla CREAZIONE del
  # container; un semplice restart NON le rilegge).
  read -r -p "Ricreare i container per applicare le modifiche a .env? (y/N) " recreate_answer
  recreate_answer="${recreate_answer:-N}"
  if [[ "${recreate_answer}" =~ ^([yY]|yes|YES|s|S|si|SI)$ ]]; then
    # up -d --force-recreate app: ricrea SOLO vi-app (consumer principale di .env)
    # rileggendo env_file, senza build e senza toccare sidecar/postgres → GPU del
    # sidecar preservata. Per env del sidecar/postgres usa il ramo build.
    echo "Ricreo vi-app per applicare .env (no build; sidecar/postgres invariati)…"
    run_cmd "compose up -d --force-recreate app" \
      "${COMPOSE_BIN[@]}" -f "${COMPOSE_FILE}" up -d --force-recreate app
  else
    # Solo bounce dei container esistenti. `podman restart` per NOME invece di
    # `podman-compose restart` (che non sempre risolve tutti i service). NON
    # rilegge .env, preserva la GPU. vi-app per ultimo (riconnessione).
    echo "Skip build — riavvio i container esistenti dello stack."
    for c in vi-postgres vi-embeddings-sidecar vi-adminer vi-app; do
      if podman ps -a --filter "name=^${c}\$" --format '{{.Names}}' | grep -qx "${c}"; then
        run_cmd_allow_fail "restart ${c}" podman restart "${c}"
      else
        echo "  (${c} non presente, skip)"
      fi
    done
  fi
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
