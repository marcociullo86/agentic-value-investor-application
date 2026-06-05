---
id: fmp-mcp-integration
type: concept
title: FMP MCP Server Integration
status: draft
created: 2026-05-25
sources:
  - raw/fmp_mcp-server.txt
tags: [fmp, mcp, model-context-protocol, llm, agent, integration, platform-domain]
domain: platform
---
# FMP MCP Server Integration

## Cos'è il Model Context Protocol (MCP)

Il **Model Context Protocol (MCP)** è uno standard aperto introdotto da Anthropic che fornisce un modo universale e standardizzato per connettere applicazioni AI ("Client") a sorgenti di dati esterne ("Server"). Prima di MCP, ogni integrazione tra un LLM e una sorgente dati richiedeva codice "glue" personalizzato; MCP definisce un'interfaccia comune che elimina questo overhead.

Analogia: MCP è la "porta USB-C dell'intelligenza artificiale" — una volta che una sorgente dati "parla MCP", qualsiasi agente AI compatibile può usarla senza configurazione aggiuntiva. [^src: raw/fmp_mcp-server.txt]

## FMP MCP Server

**Financial Modeling Prep (FMP)** ha rilasciato un MCP Server che espone tutti i propri endpoint REST (70.000+ data point su 70.000+ titoli) come **MCP tools** pronti all'uso da parte di agenti AI.

### Connection URL

```
https://financialmodelingprep.com/mcp?apikey=<FMP_API_KEY>
```

La chiave API è la stessa usata per gli endpoint REST standard. Ogni richiesta effettuata via MCP conta verso i limiti API esistenti del piano — nessun account separato né billing aggiuntivo. [^src: raw/fmp_mcp-server.txt]

### Compatibilità client

| Client | Modalità di connessione |
|--------|------------------------|
| Claude Desktop / Claude Online | Settings → Connectors → Add custom connector → incolla URL |
| Cloudflare Workers AI Playground | MCP Servers sidebar → Server URL |
| Agente Python custom (`fastmcp`) | `Client("https://financialmodelingprep.com/mcp?apikey=...")` |
| Cursor IDE | MCP server configuration |

[^src: raw/fmp_mcp-server.txt]

## Vantaggi rispetto all'integrazione REST diretta

| Aspetto | REST adapter attuale (`FmpAdapterRestClient`) | MCP Server |
|---------|----------------------------------------------|------------|
| **Zero glue code** | Richiede `FmpAdapterRestClient`, DTO, `@JsonProperty` mapping, Resilience4j chain | Il protocollo gestisce fetch, parsing JSON, deserializzazione automaticamente |
| **Token efficiency** | La documentazione API deve essere inclusa nel contesto LLM oppure hard-coded nell'adapter | Il MCP server espone le definizioni dei tool in modo efficiente — il contesto LLM si concentra sull'analisi |
| **Discoverable tools** | Il set di endpoint è fisso, definito a compile-time | L'agente AI scopre dinamicamente i tool disponibili via `client.list_tools()` |
| **Accuratezza dati** | Il backend Kotlin chiama FMP direttamente, zero hallucination | L'LLM chiama il tool esatto quando serve — dati real-time, zero hallucination |
| **Manutenzione** | Ogni nuovo endpoint FMP richiede: ADR/TSK, nuovo metodo adapter, DTO, test | Nessuna modifica al backend — il server MCP è aggiornato da FMP |

[^src: raw/fmp_mcp-server.txt]

## Relazione con l'architettura attuale

L'architettura corrente (ADR-004) usa un **REST adapter** Kotlin (`FmpAdapterRestClient`) con:
- 5 endpoint primari: `income-statement`, `balance-sheet-statement`, `cash-flow-statement`, `key-metrics`, `profile`
- 1 endpoint dividendi: `dividends`
- Resilience4j chain: `RateLimiter → CircuitBreaker → Retry`
- Cache JSONB centralizzata (`fmp_financial_snapshot`)

Il **FMP MCP Server** rappresenta un canale alternativo per lo stesso dato — non in conflitto con l'adapter REST, ma potenzialmente **complementare** per i flussi LLM-driven (EP-011 Deep Analysis, EP-012 Universe Screener).

Flusso attuale (backend Kotlin):
```
LLM (Anthropic) ← AnthropicRestClient ← DeepAnalysisService
                                              ↓
                              FmpAdapterRestClient (REST)
                                              ↓
                         https://financialmodelingprep.com/stable/
```

Flusso alternativo MCP (agente LLM nativo):
```
LLM (Claude) ← tool call → FMP MCP Server → https://financialmodelingprep.com/mcp
```

## Casi d'uso nell'applicazione value investor

1. **LLM agent integration (EP-011 Deep Analysis)**: il `DeepAnalysisService` potrebbe usare MCP invece di chiamare `FmpAdapterRestClient` e passare il payload al modello Anthropic — il modello userebbe direttamente il MCP tool "quando necessario" nel ragionamento.

2. **Prototipo Python (`agent.py` v2.6.1)**: l'agente LangGraph attuale chiama endpoint FMP via REST con `requests`; la migrazione al MCP client Python (`fastmcp`) eliminerebbe tutto il codice di fetch/parsing in `node_estrai_dati`.

3. **"Junior Analyst" chatbot**: interfaccia chat naturale dove l'utente chiede "confronta il P/E di Apple e Microsoft negli ultimi 5 anni" e l'agente risolve autonomamente i tool call MCP.

4. **Automated due diligence (EP-012)**: il batch notturno dello screener potrebbe delegare al MCP la raccolta dati per i 30 top-picks, riducendo il codice di orchestrazione in `UniverseScreenerService`. [^src: raw/fmp_mcp-server.txt]

## Considerazione architetturale: MCP vs REST adapter

L'adozione del FMP MCP Server come canale primario (al posto di `FmpAdapterRestClient`) è una decisione architetturale non ancora formalizzata. I trade-off principali:

**Pro MCP**:
- Eliminazione di `FmpAdapterRestClient` + DTO layer + mapping `@JsonProperty`
- Copertura automatica di tutti i 263 endpoint stable (non solo i 6 attualmente implementati)
- Aggiornamenti endpoint FMP senza rilascio backend
- Riduzione codice boilerplate e test di integrazione WireMock

**Contro MCP**:
- Dipendenza da endpoint MCP esterno (latenza aggiuntiva, availability FMP MCP ≠ availability REST)
- Cache JSONB centralizzata (`fmp_financial_snapshot`) non applicabile direttamente — richiede layer cache separato
- Resilience4j chain (RateLimiter, CircuitBreaker, Retry) deve essere reimplementato lato client MCP
- Meno controllo sul formato dei dati e sulla gestione degli errori HTTP (401/403/429/5xx)
- Il modello di pricing e rate limiting del canale MCP potrebbe differire dal REST

Questa valutazione è segnalata al lead-architect tramite gap `arch-fmp-mcp-vs-rest-adapter` (aperto contestualmente).

## Pagine correlate

- [[fmp-api]] — entity FMP: provider dati, piano API, base URL stable
- [[fmp-api-quickstart]] — runbook operativo REST adapter attuale
- [[value-investor-bot-architecture]] — orchestrazione agente LangGraph + node_estrai_dati
- [[analysis-api-pipeline]] — endpoint `/api/analysis/{ticker}` e `/deep`
- [[arctic-embed-l-v2]] — modello embedding EP-011 (sidecar Python, ADR-018)
