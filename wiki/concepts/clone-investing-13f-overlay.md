---
type: concept
sources: ["raw/agent.py", "raw/09_agent_py_method_analysis.md"]
status: draft
created: 2026-05-23
updated: 2026-05-23
tags: [value-investing, clone-investing, 13f, sec-edgar, pabrai, spier, berkshire, superinvestors, screening, vi-domain]
domain: value-investing
---
# Clone Investing — 13-F Overlay via SEC EDGAR

> La tecnica del "clone investing" (Mohnish Pabrai, Guy Spier) consiste nell'esaminare i 13-F trimestrali depositati dai grandi fondi value (Berkshire, Pershing Square, Akre, Markel, Sequoia) per costruire un universo di titoli pre-approvati da investitori con track record decennale. In agent.py e' implementata gratuitamente via l'API SEC EDGAR, senza richiedere piani FMP premium.

## Contesto

La SEC (Securities and Exchange Commission) richiede a ogni investitore istituzionale con asset > $100M di depositare un modulo 13-F entro 45 giorni dalla fine di ogni trimestre. Il 13-F elenca tutte le posizioni azionarie long al 31 marzo, 30 giugno, 30 settembre e 31 dicembre. L'API SEC EDGAR espone questi dati gratuitamente. [^src: raw/09_agent_py_method_analysis.md §4] [^src: raw/agent.py:157-167]

Razionale del clone investing: i gestori in questa lista hanno dimostrato rendimenti annualizzati > 15% su orizzonte > 10 anni, seguendo i principi Graham/Buffett. Le loro posizioni sono un segnale di business fondamentalmente solidi con moat durevole, gia' sottoposti ad analisi approfondita. [^src: raw/09_agent_py_method_analysis.md §2.4]

[^web: Fundamental Analysis 101: A Complete Beginner's Guide 2026 (Winvesta) — https://www.winvesta.in/blog/investors/fundamental-analysis-101-a-complete-beginners-guide-for-2026]

## Fondi Monitorati in agent.py

La lista dei fondi target e' codificata come mappa CIK → nome fondo. In agent.py v2.6.1 include:
- Berkshire Hathaway (Warren Buffett)
- Pershing Square Capital Management (Bill Ackman)
- Akre Capital Management (Chuck Akre)
- Markel Corporation (Tom Gayner)
- Sequoia Fund

[^src: raw/agent.py:875-987] [^src: raw/09_agent_py_method_analysis.md §1]

## Architettura dell'Integrazione SEC EDGAR

### Endpoint Utilizzati

| Endpoint | Scopo | Caching |
|---|---|---|
| `https://www.sec.gov/files/company_tickers.json` | Mappa completa CIK ↔ ticker (tutti i titoli quotati USA) | TTL 30gg in `./cache/sec_tickers.json` |
| `https://data.sec.gov/submissions/CIK{cik:010d}.json` | Lista submissions per fondo (tutti i filing depositati) | Non cached — API call fresca |
| `https://www.sec.gov/Archives/edgar/data/{cik}/{accession}/index.json` | Index del filing 13-F → trovare XML Information Table | Non cached |

[^src: raw/agent.py:163-167]

### Policy Fair-Access SEC

La SEC richiede header `User-Agent` identificativo per accesso programmatico:

```python
# agent.py:163
SEC_USER_AGENT = "ValueInvestorBot research@valueinvestorbot.com"
SEC_RATE_LIMIT_S = 0.15  # 6-7 req/sec, sotto soglia 10 req/sec
```

Il rate limit da rispettare e' 10 req/sec. Agent.py usa `time.sleep(0.15)` tra le chiamate. Violazioni ripetute portano al blocco IP temporaneo. [^src: raw/agent.py:167]

### Caching Aggressivo

```python
# agent.py:427-491 (sintesi)
SEC_CACHE_TTL_DAYS = 30
# Se SEC irraggiungibile, usa cache scaduta come fallback
# Se cache scaduta irraggiungibile: SEC_EMERGENCY_HOLDINGS (top posizioni Berkshire note)
SEC_EMERGENCY_HOLDINGS = {"AAPL": 3, "KO": 3, "AXP": 2, "BAC": 2, "OXY": 2, "CVX": 1, "MCO": 1}
```

[^src: raw/agent.py:163-193]

## Algoritmo di Normalizzazione `nameOfIssuer` → Ticker

Il modulo 13-F riporta il nome dell'emittente come stringa libera (es. "APPLE INC", "BERKSHIRE HATHAWAY INC CL B", "ALPHABET INC CL A"). L'algoritmo di normalizzazione mappa queste stringhe al ticker esatto con 97.4% di accuratezza (validato su 76 casi reali):

### 4 Step di Fallback

```
Step 1: Match esatto + classe azionaria corrispondente (CL A / CL B)
Step 2: Match esatto + qualsiasi classe (preferisce CL A o ticker senza classe)
Step 3: Match per primi 2 token significativi (issuer key)
Step 4: Fuzzy match ratio >= 0.92 (SEC_FUZZY_THRESHOLD = 0.92, difflib)
```

[^src: raw/agent.py:427-491] [^src: raw/09_agent_py_method_analysis.md §4]

### Pulizia dei Suffissi Noisy

Prima della normalizzazione, vengono rimossi tre categorie di token irrilevanti:

```python
# agent.py:173-186
_CORP_SUFFIXES = ["INCORPORATED", "INC", "CORPORATION", "CORP", "COMPANY", "CO",
                  "LIMITED", "LTD", "LLC", "LP", "PLC", "NV", "SA", "AG", "SE", "AB",
                  "HOLDINGS", "HOLDING", "GROUP", "CLASS"]

_STATE_SUFFIXES = {"DE", "DEL", "DELAWARE", "CA", "CAL", "CALIFORNIA", "NY", ...}

_NOISE_TOKENS = {"NEW", "OLD", "II", "III", "IV"}
```

Esempio: "BERKSHIRE HATHAWAY INC DE CL B" → "BERKSHIRE HATHAWAY CL B" → match su BRK.B. [^src: raw/agent.py:173-193]

## Lookback e Aggiornamento Trimestrale

L'agente recupera sempre l'ultimo trimestre disponibile per ogni fondo (`_ultimo_trimestre_13f`, `agent.py:335-350`). I 13-F hanno un ritardo intrinseco di 45 giorni dal fine trimestre, quindi le posizioni viste sono quelle di 45-135 giorni prima dell'analisi.

**Decisione metodologica discutibile**: lookback fisso all'ultimo trimestre. Fondi come Berkshire modificano raramente le posizioni; fondi piu' attivi (Pershing Square) potrebbero aver gia' chiuso posizioni in 45 giorni. Un lookback variabile per fondo potrebbe migliorare la qualita' del segnale. [^src: raw/09_agent_py_method_analysis.md §6]

## Relazione con l'Universo Screener

Il segnale 13-F e' uno dei 4 segnali aggregati in `node_screener` per costruire l'universo:

1. **Segnale 1 (13-F)**: top holdings da 5+ fondi value (peso: storico)
2. **Segnale 2 (Quant FMP)**: company screener con filtri ROE>15%, D/E<0.5, beta<1.3
3. **Segnale 3 (News LLM)**: news mercato recenti classificate positivamente per business Buffett
4. **Segnale 4 (Settori)**: whitelist settori Buffett (esclude biotech, mining, airlines, gambling, SPAC)

I ticker presenti in piu' segnali hanno precedenza nell'universo finale (max 30 ticker). [^src: raw/09_agent_py_method_analysis.md §1] [^src: raw/agent.py:875-987]

## Spec di Porting per EP-012 (WebApp)

Il porting in Kotlin per EP-012 / US-047 deve implementare:
1. `SecEdgarAdapter.getCompanyTickersMap()` — cache 30gg, TTL configurabile
2. `SecEdgarAdapter.get13fFilings(cik)` — ultima submission trimestrale
3. `CompanyNameNormalizer.normalize(nameOfIssuer)` — 4-step fallback
4. Rate limiter: max 10 req/sec (Resilience4j RateLimiter)
5. Emergency holdings come fallback finale

Vedi gap aperto `wiki-promote-sec-edgar-adapter-spec` per la spec dell'adapter.

## Concetti correlati
[[value-investor-bot-architecture]]
[[superinvestors-graham-doddsville]]
[[sec-filings-analysis]]
[[graham-modern-bot-methodologies]]

## Pagine collegate
[[warren-buffett]]
[[value-investing-rule-engine]]
[[fmp-company-information]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
