# ChatGPT Custom GPT Adapter — `.chatgpt/`

Adapter ChatGPT (Custom GPTs + file tools) per il pattern `llm-wiki++` v2.13+.
**Maturity: manifest-only**.

Per workflow seri, considera l'OpenAI Assistants adapter (`adapters/openai/`) che è
più strutturato (vector store, multi-thread, parallel function calls).

## Stato v2.13

Manifest formalizzato; scaffolding **manuale** richiesto.

## Quando usare ChatGPT vs altri adapter

| Scenario | Adapter consigliato |
|---|---|
| Production / team use | Claude Code |
| Multi-agent orchestration | Claude Code o OpenAI Assistants |
| Prototipazione rapida solo / single-user | ChatGPT Custom GPT |
| Filesystem-local heavy | Claude Code / Cursor / Aider |
| Vector store RAG nativo | OpenAI Assistants |

## Scaffolding manuale

Vedi `manifest.yaml.scaffolding_instructions`.

## Tool conversion

Vedi [`adapters/README.md`](../README.md#tool-conversion-table).
