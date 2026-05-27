---
name: stack-detector
description: Skill condivisa (PATTERN §19.2, v2.12) — riconosce lingua, framework, versione di un albero di codice. Riusabile da code-reviewer (per ogni TSK in review) e da repo-sync (per il documento di specifiche di un repo esistente).
---
# Skill — Stack Detector

Riferimenti: PATTERN §19 (CQRL), §19.2 (Stack Detector), §16 (sync adapters, riuso da
`repo-sync`). Implementa la detection deterministica + heuristic dello stack
tecnologico a partire da file di manifest, config del framework, import e signature.

## Quando viene invocata

| Caller | Input | Scope di scansione | Note |
|---|---|---|---|
| `code-review-protocol` Fase 1 (`code-reviewer`) | TSK in review | File toccati dal TSK + manifest del progetto | Output usato per scegliere `ruleset_id` (passate 1-3) |
| `repo-extraction-protocol` Fase 2 (`repo-sync`) | Path repo | Manifest + entrypoint + sample di sorgenti | Output va in `## Stack rilevato` del `.md` |

## Output schema (single source of truth)

```json
{
  "language": "python | typescript | javascript | go | java | kotlin | rust | ruby | php | csharp | other",
  "framework": "fastapi | django | flask | express | nestjs | nextjs | react | vue | svelte | spring | gin | echo | actix | rails | laravel | dotnet | other | none",
  "framework_version": "string | unknown",
  "secondary_libs": [
    "<name>@<version>",
    "..."
  ],
  "patterns_expected": [
    "async | sync | pydantic_v2 | dependency_injection | hooks | composition_api | actor_model | ..."
  ],
  "ruleset_id": "<language>.<framework>.v<major>",
  "confidence": 0.94,
  "evidence": [
    { "type": "manifest", "file": "pyproject.toml", "weight": 0.5 },
    { "type": "import", "file": "src/main.py", "weight": 0.3, "snippet": "from fastapi import FastAPI" },
    { "type": "config", "file": "uvicorn.json", "weight": 0.14 }
  ]
}
```

`confidence` ∈ `[0..1]`: somma pesata dei `weight` delle evidenze trovate, normalizzata
in `[0..1]` con capping a 1.0. Soglie indicative (configurabile in
`factory.config.yaml.code_quality.thresholds.confidence_min`, default 0.6):

- `≥ 0.9` — high confidence (manifest + import + config tutti coerenti)
- `0.6 ≤ x < 0.9` — medium (manifest chiaro, qualche eterogeneità)
- `0.3 ≤ x < 0.6` — low (degraded mode: solo regole language-level)
- `< 0.3` — very low (segnala in chat, raccomanda input umano per chiarire)

## Algoritmo (3 fasi)

### Fase 1 — Manifest scan (deterministico, peso 0.5 totale)

Glob in priorità decrescente:

| Manifest | Lingua/Framework derivati | Versione da |
|---|---|---|
| `package.json` | `language: javascript` o `typescript` (se `tsconfig.json` presente); framework da `dependencies` (react, vue, next, …) | `dependencies.<framework>` |
| `pyproject.toml` | `language: python`; framework da `[tool.poetry.dependencies]` o `[project.dependencies]` | semver in dipendenze |
| `requirements.txt` | `language: python` (fallback se no `pyproject.toml`); framework da regex sui pacchetti | `==<ver>` o `>=<ver>` |
| `pom.xml` | `language: java`; framework da `<dependency>` (spring-boot-*, jakarta-*, …) | `<version>` |
| `build.gradle{.kts}` | `language: java` o `kotlin`; framework idem | regex su `implementation` |
| `go.mod` | `language: go`; framework da `require` (gin, echo, fiber, …) | semver |
| `Cargo.toml` | `language: rust`; framework da `[dependencies]` (actix, axum, …) | semver |
| `Gemfile` | `language: ruby`; framework da `gem 'rails'`, ecc. | regex |
| `composer.json` | `language: php`; framework da `require` (laravel/framework, symfony/*) | semver |
| `*.csproj`, `*.fsproj` | `language: csharp` o `fsharp`; framework da `<PackageReference>` | semver |

**Output Fase 1**: candidate `(language, framework, framework_version)` con weight 0.5
totale.

### Fase 2 — Config framework (peso 0.2)

Presenza di file di config noti rafforza la confidence (mai contraddice manifest, solo
conferma o aggiunge dettagli):

- `next.config.js`, `next.config.mjs`, `next-env.d.ts` → conferma Next.js
- `vite.config.{js,ts}` → conferma Vite (lib generica, abbinato a React/Vue/Svelte)
- `nest-cli.json` → conferma NestJS
- `manage.py`, `django/` directory → conferma Django
- `uvicorn.json`, `gunicorn_conf.py` → conferma FastAPI/Flask
- `nuxt.config.{js,ts}` → conferma Nuxt
- `angular.json` → conferma Angular
- `tsconfig.json` → conferma TypeScript (vs JavaScript)
- `Dockerfile` (regex su `FROM` per language hint)
- `Makefile`, `Taskfile.yml`, `justfile` (regex su target standard)

**Output Fase 2**: aggiunge evidenze di peso 0.2 totali alla candidate.

### Fase 3 — Sampling import e signature (peso 0.3)

Glob `**/*.<ext>` filtrato a `<= 50 file` campionati (priorità: file in `src/`, `app/`,
`cmd/`, `lib/`; saltare `node_modules/`, `vendor/`, `.git/`, `dist/`, `build/`, `target/`).

Per ciascun file, regex sui primi 50 righe per import statements:

```
python: ^(from|import)\s+
javascript/typescript: ^(import|const \w+\s*=\s*require)
go: ^(import|package)
java: ^(import|package)
rust: ^use\s+
ruby: ^(require|require_relative)\s+
```

Confronta con la candidate di Fase 1: se ≥ 5 file hanno import del framework
candidato, aggiungi weight 0.3. Se l'import diverge dalla candidate (es. manifest dice
`react` ma 0 file importano da React), riduci confidence di 0.2 e segnala in `evidence`
come `type: "import-divergence"`.

`patterns_expected` (output): derivato deterministicamente dalla coppia
`(language, framework, version)`:
- `python, fastapi, >= 0.95` → `["async", "pydantic_v2", "dependency_injection"]`
- `python, fastapi, < 0.95` → `["async", "pydantic_v1", "dependency_injection"]`
- `typescript, react, >= 18` → `["hooks", "concurrent_mode_compatible", "strict_mode"]`
- `typescript, react, < 18` → `["hooks", "legacy_lifecycle_possible"]`
- `go, gin` → `["middleware_chain", "context_propagation", "explicit_error_return"]`
- … (estendibile in `code_quality/rules/_patterns_expected.json` se serve in v2.13)

## Casi limite

- **Monorepo / multi-language**: se Fase 1 trova ≥ 2 manifest di linguaggi diversi
  (es. `package.json` E `go.mod`), produci output `stack_descriptor[]` (array di stack
  descriptor, uno per linguaggio dominante). Il caller decide se invocare review per
  ciascuno. `repo-sync` documenta tutti gli stack in `## Stack rilevato`.
- **Manifest assente** (es. progetto solo `.py` senza `pyproject.toml`/`requirements.txt`):
  Fase 1 fallisce; salta a Fase 3 con peso aumentato (0.5 invece di 0.3) basato sui
  soli import. `confidence` cap a 0.5 (mai oltre senza manifest).
- **Versione "unknown"**: se Fase 1 trova framework ma non versione (es. `pyproject.toml`
  con `fastapi = "*"`), imposta `framework_version: "unknown"`, `confidence` cap a 0.7,
  e usa il `ruleset_id` con versione più recente disponibile come fallback.
- **Framework custom / nessun framework** (es. solo `language: python` senza framework
  identificabile): `framework: "none"`, `ruleset_id: "<language>.none.v1"`. Le passate
  applicano solo regole language-level.
- **Languages non in lista**: `language: "other"`, `framework: "other"`, `confidence: 0.0`,
  modalità degradata o ABORT (decisione del caller).

## Idempotenza e caching

- Stateless: ogni invocazione ricalcola da zero. Niente cache su filesystem.
- Idempotente per repo immutato: stesso filesystem → stesso output (modulo
  randomness del sampling, che è deterministico via ordering alfabetico).

## Non in scope per stack-detector

- Eseguire codice del repo (no `pip install`, `npm install`, `go run`).
- Risolvere conflitti di versione (es. `package.json` dice react@18 ma `package-lock.json`
  ha react@17): segnala in `evidence` come `type: "lockfile-mismatch"` e usa la versione
  di `package.json` (manifest dichiarato).
- Riconoscere "stile" o "qualità" del codice — quello è scope del Code Reviewer (le 3
  passate §19.3).
- Decidere quale regola applicare — quello è scope del code-reviewer dopo aver letto
  `stack_descriptor.ruleset_id`.
