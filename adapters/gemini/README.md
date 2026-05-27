# Gemini Code Assist Adapter — `.gemini/`

Adapter Gemini Code Assist (Custom Gems) per il pattern `llm-wiki++` v2.13+.
**Maturity: manifest-only**.

## Stato v2.13

Manifest formalizzato; scaffolding **manuale** richiesto. Vedi `manifest.yaml`
`scaffolding_instructions` per i passi.

## Mappatura concettuale

| Pattern | Gemini Code Assist |
|---|---|
| Agente | Custom Gem |
| Skill | Sezione Gem instructions o file `@`-referenced |
| Comando | Multi-Gem invocation |
| File read/write | Built-in tools |

## Tool conversion

Vedi [`adapters/README.md`](../README.md#tool-conversion-table).

## Roadmap

- v2.14 (candidato): full scaffolding con template `.gemini/gems/<name>.md` auto-generati.
- v2.15 (candidato): integrazione Gemini CLI per gestione Gems automatica.
