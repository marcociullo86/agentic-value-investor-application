#!/usr/bin/env bash
# /run shell wrapper per Aider adapter — Agentic Factory llm-wiki++ v2.13
# Invoca Aider con orchestrator prompt per dashboard + suggerimento next-step.

set -euo pipefail

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
FACTORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$FACTORY_ROOT"

# Carica orchestrator + state-scan skill (se presente)
ARGS=(--read .aider/prompts/orchestrator.md)
if [[ -f .aider/skills/state-scan.md ]]; then
  ARGS+=(--read .aider/skills/state-scan.md)
fi
if [[ -f .aider/skills/parallel-scheduling.md ]]; then
  ARGS+=(--read .aider/skills/parallel-scheduling.md)
fi

# Read-only mode (orchestrator scrive solo memory/ e log; conferma user-driven)
ARGS+=(--no-auto-commits)

# Lancia con messaggio iniziale
MESSAGE="${1:-/run — mostra dashboard di stato + wave plan + suggerisci next-step}"
ARGS+=(--message "$MESSAGE")

echo "Launching Aider as Orchestrator..."
echo "Factory root: $FACTORY_ROOT"
echo ""
exec aider "${ARGS[@]}"
