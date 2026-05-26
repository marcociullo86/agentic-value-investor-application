#!/usr/bin/env bash
# pseudonymize-user-logs.sh — GDPR right-to-erasure: pseudonymize all log
# entries for a given userId, replacing it with USER_DELETED_<sha256-prefix>.
#
# Deterministic: the same userId always produces the same pseudonym, so
# correlations across files are preserved for audit without re-identification.
#
# RUNBOOK
# -------
# 1. Receive a verified GDPR erasure request (ticket / legal).
# 2. Identify the userId to pseudonymize.
# 3. Stop log ingestion (optional but recommended for consistency).
# 4. Run:
#        ./pseudonymize-user-logs.sh <userId> [log-directory]
#    Default log-directory: /var/log/value-investing
# 5. Verify the script exits 0 and prints "SUCCESS".
# 6. For centralized aggregators (ELK / Loki / CloudWatch):
#    - Use the aggregator's bulk-update or delete-by-query API.
#    - Filter on field "userId":"<userId>", replace with the same pseudonym.
#    - Example (OpenSearch):
#        POST /logs-*/_update_by_query
#        { "query": { "term": { "userId": "<userId>" } },
#          "script": { "source": "ctx._source.userId = '<PSEUDONYM>'" } }
# 7. Resume log ingestion.
# 8. Document the pseudonymization in the GDPR register with timestamp + pseudonym.
#
# Ref: ADR-021 §7 — GDPR retention policy

set -euo pipefail

readonly USAGE="Usage: $0 <userId> [log-directory]"

USER_ID="${1:?$USAGE}"
LOG_DIR="${2:-/var/log/value-investing}"

if [[ ! -d "$LOG_DIR" ]]; then
    echo "ERROR: Log directory '$LOG_DIR' does not exist." >&2
    exit 1
fi

# Portable sha256: try sha256sum (GNU/Linux), fall back to shasum (macOS)
hash_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        printf '%s' "$1" | sha256sum | cut -c1-12
    elif command -v shasum >/dev/null 2>&1; then
        printf '%s' "$1" | shasum -a 256 | cut -c1-12
    else
        echo "ERROR: Neither sha256sum nor shasum found." >&2
        exit 1
    fi
}

# Portable in-place sed (BSD vs GNU)
sed_inplace() {
    local expression="$1"
    local file="$2"
    local tmp="${file}.pseudonymize.tmp"
    sed "$expression" "$file" > "$tmp" && mv "$tmp" "$file"
}

PSEUDONYM="USER_DELETED_$(hash_sha256 "$USER_ID")"

echo "=== GDPR Pseudonymization ==="
echo "userId       : $USER_ID"
echo "pseudonym    : $PSEUDONYM"
echo "log directory: $LOG_DIR"
echo ""

count_matches() {
    local pattern="$1"
    local files
    files=$(grep -rl "$pattern" "$LOG_DIR" 2>/dev/null || true)
    if [[ -z "$files" ]]; then echo 0; else echo "$files" | wc -l | tr -d ' '; fi
}

JSON_QUOTED=$(count_matches "\"userId\":\"$USER_ID\"")
JSON_NUMERIC=$(count_matches "\"userId\":${USER_ID}[^0-9]")
PRETTY_PRINT=$(count_matches "\[userId:${USER_ID}\]")
TOTAL=$((JSON_QUOTED + JSON_NUMERIC + PRETTY_PRINT))

if [[ "$TOTAL" -eq 0 ]]; then
    echo "No log entries found for userId=$USER_ID. Nothing to do."
    exit 0
fi

echo "Affected files: $TOTAL (JSON-quoted=$JSON_QUOTED, JSON-numeric=$JSON_NUMERIC, pretty=$PRETTY_PRINT)"
echo "Proceeding with pseudonymization..."
echo ""

# JSON structured logs — quoted userId ("userId":"<value>")
if [[ "$JSON_QUOTED" -gt 0 ]]; then
    grep -rl "\"userId\":\"$USER_ID\"" "$LOG_DIR" 2>/dev/null | while IFS= read -r file; do
        sed_inplace "s|\"userId\":\"$USER_ID\"|\"userId\":\"$PSEUDONYM\"|g" "$file"
    done
fi

# JSON structured logs — numeric userId ("userId":12345)
if [[ "$JSON_NUMERIC" -gt 0 ]]; then
    grep -rl "\"userId\":${USER_ID}[^0-9]" "$LOG_DIR" 2>/dev/null | while IFS= read -r file; do
        sed_inplace "s|\"userId\":${USER_ID}\([^0-9]\)|\"userId\":\"$PSEUDONYM\"\1|g" "$file"
    done
fi

# Pretty-print dev logs — pattern [userId:NNN]
if [[ "$PRETTY_PRINT" -gt 0 ]]; then
    grep -rl "\[userId:${USER_ID}\]" "$LOG_DIR" 2>/dev/null | while IFS= read -r file; do
        sed_inplace "s|\[userId:${USER_ID}\]|[userId:$PSEUDONYM]|g" "$file"
    done
fi

REMAINING=$(grep -rcE "\"userId\":\"${USER_ID}\"|\"userId\":${USER_ID}[^0-9]|\[userId:${USER_ID}\]" "$LOG_DIR" 2>/dev/null | awk -F: '{s+=$NF}END{print s+0}' || true)

if [[ "$REMAINING" -eq 0 ]]; then
    echo "SUCCESS: All occurrences of userId=$USER_ID replaced with $PSEUDONYM"
    exit 0
else
    echo "WARNING: $REMAINING occurrence(s) remain — manual review required." >&2
    exit 1
fi
