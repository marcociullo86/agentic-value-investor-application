---
type: concept
sources: ["raw/FMP_Docs_1_Auth_and_Search.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, auth, api-key, security]
---
# Autenticazione FMP API

> Ogni richiesta all'API FMP deve includere una API key valida, passabile come header HTTP o come parametro di query URL.

## Dettaglio

FMP utilizza un modello di autenticazione basato su API key statica. Non è previsto un flusso OAuth2 o JWT per l'accesso standard. La chiave va inclusa in uno dei due modi: [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

| Metodo | Formato |
|--------|---------|
| Header HTTP | `apikey: YOUR_API_KEY` |
| Query parameter | `?apikey=YOUR_API_KEY` (append all'URL) |

Entrambi i metodi sono equivalenti per l'autorizzazione. Il metodo query param è il più comune nelle integrazioni client-side; il metodo header è preferibile per sicurezza in ambienti server-side per evitare l'esposizione della chiave nei log HTTP. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

## Concetti correlati
[[fmp-api]]
[[fmp-search]]

## Pagine collegate
[[fmp-docs-1-auth-and-search]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
