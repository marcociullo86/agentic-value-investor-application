# OpenAI Assistants Adapter — `.openai/`

Adapter OpenAI Assistants v2 per il pattern `llm-wiki++` v2.13+. Maturity: **partial**.

## Filosofia operativa

OpenAI Assistants API (v2, 2024) supporta:
- **Assistants persistenti** con `instructions`, `tools`, `model`.
- **Thread + Runs** per conversazioni multi-turn.
- **Tool**: `code_interpreter` (sandbox Python), `file_search` (vector store RAG),
  custom function calls (JSON schema).
- **Parallel function calls** in singolo run.

Il pattern `llm-wiki++` è tradotto come:
- Ogni "agent" del PATTERN → un OpenAI **Assistant** (`.openai/assistants/<name>.json` definisce instructions + tools).
- Le "skill" del PATTERN → embedded nelle instructions dell'Assistant o esposte come function tools.
- I "command" → script Python che invocano Assistants via API.

## Limitazioni vs Claude Code

1. **Filesystem sandbox**: `code_interpreter` esegue Python in un sandbox isolato. Per
   leggere `wiki/`, `management/`, ecc. del filesystem locale, serve **upload preventivo**
   via Files API (con un vector store per `file_search`). Per **scritture persistenti**,
   serve **download post-run** degli output.

2. **Multi-agent orchestration**: OpenAI Assistants è single-thread per default. Per
   coordinare più Assistant (es. orchestrator chiama wiki-keeper), serve script
   Python esterno che fa il dispatch (vedi `setup.py` + `run.py`).

3. **Persistenza state**: Threads OpenAI persistono solo le conversazioni, non il
   filesystem. Tutta la persistenza locale (`wiki/log.md`, `memory/episodic/`, ecc.)
   è gestita dal layer Python di orchestrazione.

## Cosa scaffolda v2.13 (partial)

Al bootstrap con adapter `openai`:

```
.openai/
├── setup.py                    # crea tutti gli Assistant via API (richiede OPENAI_API_KEY)
├── run.py                      # wrapper Python che orchestra thread + runs + file sync
├── config.yaml                 # mappature locali ↔ OpenAI IDs
├── assistants/
│   ├── orchestrator.json       # instructions + tools per Orchestrator
│   ├── wiki-keeper.json        # ...
│   ├── ... (uno per agent applicabile alla topology)
├── skills/                     # function tool definitions
│   ├── ingest-protocol.json
│   ├── lint-checks.json
│   └── ...
└── README.md                   # istruzioni operative
```

**Non scaffoldato automaticamente** (richiede intervento utente):
- `OPENAI_API_KEY` settata in env.
- `python setup.py` per creare gli Assistant lato OpenAI.
- Vector store popolato con `wiki/`, `management/`, ecc. per `file_search`.
- Script `run.py` da customizzare per il workflow specifico.

## Come usare l'adapter

```bash
# 1. Setup (una tantum dopo il bootstrap)
export OPENAI_API_KEY=sk-...
cd .openai
python setup.py          # crea Assistants via API, salva IDs in config.yaml

# 2. Sync filesystem locale → vector store OpenAI
python sync.py upload    # uploads wiki/, management/, raw/ a OpenAI Files API

# 3. Run un'operazione
python run.py orchestrator --command run            # /run dashboard
python run.py wiki-keeper --ingest raw/foo.txt      # ingest
python run.py be-dev --tsk TSK-042                  # /dev TSK-042

# 4. Sync ritorno (post-modifica)
python sync.py download  # downloads modifiche dagli Assistant locally
```

Questi script Python sono **stub generati al bootstrap**; l'utente li customizza per il
proprio workflow + costi.

## Costi

OpenAI Assistants v2 pricing (Q2 2025, verifica current):
- Input tokens: ~$2.50/1M (gpt-4o)
- Output tokens: ~$10/1M
- code_interpreter: $0.03/session
- file_search: storage $0.10/GB/day + query costs

Per una factory con uso moderato (10 ingest/giorno, 5 review/giorno) si stima ~$10-30/mese.
Verifica i costi prima di scaffoldare in produzione.

## Alternative più leggere

Se Assistants v2 è overkill, considera:
- **Chat Completions API + tool calls**: stateless ma più semplice. Niente vector store nativo;
  RAG via embedding API + DB locale (es. SQLite + pgvector).
- **Agent SDK** (Claude Agent SDK / Anthropic): se possibile usare Claude invece.
- **Aider con OpenAI backend**: usa `aider --model openai/gpt-4o` e gli adapter Aider.

## Coesistenza con altri adapter

OpenAI Assistants in coesistenza con `.claude/` / `.cursor/` / `.aider/` (R.A1-R.A6):
- Stesso filesystem condiviso (`wiki/`, `management/`, ...).
- Single-committer wiki/ enforced — l'utente non invoca wiki-keeper OpenAI + Claude
  contemporaneamente.
- Sync filesystem ↔ vector store OpenAI prima/dopo ogni run.

## Maturity roadmap

- **v2.13 (current)**: manifest + setup.py stub + assistants/*.json templates. Run reale
  richiede setup manuale.
- **v2.14 (candidato)**: `run.py` orchestrator completo + `sync.py` bidirezionale.
- **v2.15 (candidato)**: Assistants v3 API support, MCP server integration.
