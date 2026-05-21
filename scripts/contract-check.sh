#!/usr/bin/env bash
# Local contract-check mirror of .github/workflows/contract-check.yml (TSK-037).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> BE contract (gradle contractCheck)"
cd "$ROOT/src/backend"
gradle contractCheck --no-daemon

echo "==> FE contract (generate:api + typecheck)"
cd "$ROOT/src/frontend"
npm install
npm run generate:api
npm run typecheck:api

echo "contract-check: OK"
