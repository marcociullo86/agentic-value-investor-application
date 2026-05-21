# Cursor adapter — llm-wiki++ v2.8

Implementazione Cursor del contratto [`PATTERN.md`](../PATTERN.md).

## Layout

```
.cursor/
├── agents/          # Subagent (Task tool + /name)
├── commands/        # Slash commands in chat Agent
├── skills/          # Skill progetto (<name>/SKILL.md)
└── rules/           # Regole always-apply
```

## Sincronizzazione con `.claude/`

- **Skill**: copiate da `.claude/skills/*.md` → `.cursor/skills/<name>/SKILL.md`. Se modifichi una procedura canonica, aggiorna **entrambe** le cartelle (o rigenera con lo script in `scripts/sync-cursor-adapter.sh` se presente).
- **Agent**: derivati da `.claude/agents/` con frontmatter Cursor (`model: fast|inherit`, `readonly` dove serve). Campo `tools:` rimosso (specifico Claude Code).

## Precedenza

Cursor documenta: in conflitto di nome, `.cursor/agents/` vince su `.claude/agents/`.
