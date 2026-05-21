#!/usr/bin/env bash
# Rigenera .cursor/skills e .cursor/agents da .claude/ (adapter mirror).
# Uso: ./scripts/sync-cursor-adapter.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p .cursor/skills .cursor/agents .cursor/agents/.archive

for f in .claude/skills/*.md; do
  name=$(basename "$f" .md)
  mkdir -p ".cursor/skills/$name"
  cp "$f" ".cursor/skills/$name/SKILL.md"
done

for f in .claude/agents/*.md; do
  bn=$(basename "$f")
  sed -e '/^tools:/d' \
      -e 's/model: claude-haiku-4-5/model: fast/' \
      -e 's/model: claude-sonnet-4-6/model: inherit/' \
      -e 's/model: claude-opus-4-7/model: inherit/' \
      -e 's|\.claude/agents/|.cursor/agents/|g' \
      "$f" > ".cursor/agents/$bn"
done

# readonly subagents
for a in wiki-lint wiki-keeper-worker; do
  f=".cursor/agents/${a}.md"
  if ! grep -q '^readonly:' "$f"; then
    awk '/^model:/{print; print "readonly: true"; next} {print}' "$f" > "${f}.tmp" && mv "${f}.tmp" "$f"
  fi
done

# lint: dual adapter path
sed -i.bak 's|`.cursor/agents/\*\*` (per check 4c topology)|`.cursor/agents/**` e `.claude/agents/**` (per check 4c topology)|' \
  .cursor/agents/wiki-lint.md 2>/dev/null || true
rm -f .cursor/agents/wiki-lint.md.bak

echo "Synced: $(find .cursor/skills -name SKILL.md | wc -l | tr -d ' ') skills, $(ls .cursor/agents/*.md 2>/dev/null | wc -l | tr -d ' ') agents"
