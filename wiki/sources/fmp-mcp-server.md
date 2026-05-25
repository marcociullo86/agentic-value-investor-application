---
id: fmp-mcp-server
type: source
title: FMP MCP Server — Annuncio e guida integrazione
status: draft
created: 2026-05-25
sources:
  - raw/fmp_mcp-server.txt
tags: [fmp, mcp, model-context-protocol, llm, agent]
---
# Source: FMP MCP Server

**Documento sorgente:** `raw/fmp_mcp-server.txt`
**Data ingest:** 2026-05-25
**Tipo:** Annuncio ufficiale FMP + guida step-by-step

## Sintesi del documento

Annuncio di Financial Modeling Prep (FMP) del proprio **MCP Server** (Model Context Protocol). Il documento descrive:

1. Cos'è MCP (standard aperto Anthropic, "USB-C per l'AI")
2. Vantaggi dell'integrazione MCP vs REST classico (zero glue code, token efficiency, discoverability)
3. Connection URL: `https://financialmodelingprep.com/mcp?apikey=<FMP_API_KEY>`
4. Guide di connessione per: Claude Desktop/Online, Cloudflare Workers AI, Python (fastmcp)
5. Casi d'uso: "Junior Analyst" chatbot, automated due diligence, live investment research

## Pagine wiki generate

- [[fmp-mcp-integration]] — concept dedicato: cos'è, vantaggi, relazione con REST adapter, considerazione architetturale

## Informazioni chiave estratte

- **Connection URL**: `https://financialmodelingprep.com/mcp?apikey=<FMP_API_KEY>`
- **Tutti gli endpoint REST FMP** sono esposti come MCP tools (nessuna selezione — scope completo)
- **Billing**: ogni richiesta MCP conta verso i limiti del piano API esistente
- **Prerequisiti**: API key FMP + client MCP compatibile (Claude Desktop, Cursor, script Python)
- **Esempio Python**: libreria `fastmcp`, metodi `client.list_tools()` + `client.call_tool("quote", {"symbol": "AAPL"})`
