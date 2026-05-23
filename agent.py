"""
╔══════════════════════════════════════════════════════════════════════════════╗
║         VALUE INVESTOR BOT — TEAM BUFFETT  v2.6.1                          ║
║         Sistema Multi-Agente basato su LangGraph                           ║
║         Filosofia: Warren Buffett + Charlie Munger                         ║
║                                                                            ║
║  Flusso:                                                                   ║
║    node_screener (4 segnali) → [LOOP per ogni ticker]:                     ║
║      node_estrai_dati → node_leggi_10k → node_news_sentiment →             ║
║      node_check_price_action → node_calcola_valore → munger_decision →     ║
║      verdetto → [prossimo ticker o report]                                 ║
║    node_genera_report → HTML nel browser                                   ║
║                                                                            ║
║  v2.6.1 — Fix breaking changes API:                                         ║
║    • Embedding model: embedding-001 → gemini-embedding-001 (GA Feb 2026)   ║
║    • Opus 4.7: rimosso temperature (API rifiuta con 400, è REMOVED)        ║
║    • Silenziato warning XMLParsedAsHTMLWarning (10-K XBRL)                 ║
║                                                                            ║
║  v2.6 — Modalità IBRIDA ticker manuali + screener:                         ║
║    Costanti in cima al file:                                                ║
║       TICKER_MANUALI = []          ticker da analizzare sempre (priorità)  ║
║       INCLUDI_SCREENER = True      se True unisce manuali + screener       ║
║    I ticker manuali bypassano il filtro settoriale (Trust mode).           ║
║                                                                            ║
║  v2.5 — Segnale 1 (13-F) GRATIS via SEC EDGAR:                              ║
║    Sostituito FMP /institutional-ownership/extract (piano Ultimate $149/mo)║
║    con API ufficiale SEC EDGAR (data.sec.gov) gratuita.                    ║
║    Tutto il piano FMP Starter ($22/mo) ora è SUFFICIENTE per il bot.       ║
║    Helper aggiunti:                                                        ║
║       _get_sec_company_tickers_map()  cache 30gg ./cache/sec_tickers.json  ║
║       _normalize_company_name(name)   97.4% accuratezza in backtest        ║
║       _match_holding_to_ticker(name)  4-step fallback (exact->fuzzy 0.92)  ║
║       _fetch_latest_13f_holdings(cik) parser XML information table         ║
║                                                                            ║
║  v2.4 — Migrazione FMP API da /api/v3/ a /stable/                          ║
║                                                                            ║
║  v2.3 — Strategia LLM ibrida ottimale:                                     ║
║    • Claude Opus 4.7 sui nodi finanziari profondi (leggi_10k, news)        ║
║    • Gemini sui compiti leggeri (screener, embeddings FAISS)               ║
║                                                                            ║
║  v2.2 — Difese anti "value trap" e cattura "panic buy"                     ║
║  v2.1 — Screener "Think like Buffett" (13-F + Quant + News + Settori)      ║
╚══════════════════════════════════════════════════════════════════════════════╝
"""

import os
import re
import json
import logging
import difflib
import datetime
import tempfile
import webbrowser
from pathlib import Path
from typing import TypedDict

import requests
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import BSHTMLLoader
from langchain_community.vectorstores import FAISS
from langchain_google_genai import GoogleGenerativeAIEmbeddings, ChatGoogleGenerativeAI
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_anthropic import ChatAnthropic
from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import StateGraph, END

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

# Silenzia warning BeautifulSoup quando 10-K SEC arriva in formato XBRL/XML
# (capita su molti filing moderni). Il parser lxml gestisce comunque entrambi.
try:
    import warnings
    from bs4 import XMLParsedAsHTMLWarning
    warnings.filterwarnings("ignore", category=XMLParsedAsHTMLWarning)
except ImportError:
    pass

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

# ══════════════════════════════════════════════════════════════════════════════
#  MODALITÀ DI SELEZIONE TICKER (v2.6 — modalità ibrida manuali + screener)
# ══════════════════════════════════════════════════════════════════════════════
# Numero MAX di ticker che lo screener può proporre per run.
# I ticker manuali NON contano in questo limite — vengono aggiunti in cima.
UNIVERSO_FINALE_MAX_TICKET_NUMBER = 30

# Ticker MANUALI da analizzare sempre (lista vuota = solo screener).
# Esempi:
#   TICKER_MANUALI = []                   # default: solo screener
#   TICKER_MANUALI = ["IBKR"]             # un solo ticker manuale
#   TICKER_MANUALI = ["IBKR", "NVR", "TPL"]  # mix manuali + screener
#
# I ticker manuali:
#   • appaiono SEMPRE in testa alla lista di analisi
#   • bypassano il filtro settoriale _verifica_settore (Trust mode)
#   • subiscono comunque tutte le analisi profonde (10-K, news, DCF, Munger)
TICKER_MANUALI = ["NFLX"]

# Se True, ai ticker manuali si aggiungono anche quelli scoperti dallo screener.
# Se False, lo screener viene saltato del tutto (solo TICKER_MANUALI vengono analizzati).
# Ignorato se TICKER_MANUALI è vuoto (in quel caso lo screener gira comunque).
INCLUDI_SCREENER = False

CPU_OR_CUDA='cpu'

# ══════════════════════════════════════════════════════════════════════════════
#  MODALITÀ EMBEDDING (Locale vs Cloud)
# ══════════════════════════════════════════════════════════════════════════════
# 0 = Locale (HuggingFace, gratis, quasi istantaneo, richiede 'pip install sentence-transformers')
# 1 = Cloud  (Google Gemini, soggetto a rate limits e blocchi temporali lunghi)
MODALITA_EMBEDDING = 0


# ══════════════════════════════════════════════════════════════════════════════
#  COSTANTI CONFIGURABILI — Soglie Buffett-style
# ══════════════════════════════════════════════════════════════════════════════

# Drawdown minimo dal 52-week high per considerare un "panic discount".
# Riferimenti storici: AmEx 1963 -50%, KO 1988 -25%, Wells Fargo 1990 -55%.
# Default 35%: equilibrio tra cattura occasioni e rigore Buffett.
PANIC_DRAWDOWN_THRESHOLD = 35.0  # percentuale

# Soglia drawdown per attivare WARNING di possibile value trap
# (drawdown grande + segnali negativi → da analizzare con sospetto)
WARNING_DRAWDOWN_THRESHOLD = 25.0  # percentuale

# Soglia margine di sicurezza standard Buffett (Graham insegnava 30-50%)
MARGINE_SICUREZZA_STANDARD = 30.0

# Numero di news degli ultimi N giorni da analizzare per sentiment
NEWS_LOOKBACK_DAYS = 90
NEWS_MAX_ITEMS = 30

# ══════════════════════════════════════════════════════════════════════════════
#  MODELLI LLM v2.3 — Strategia ibrida ottimizzata
#  ───────────────────────────────────────────────────────────────────────────
#  Claude Opus 4.7 per i nodi di analisi finanziaria profonda:
#    - Finance Agent v1.1: leader (64.4%)
#    - FinanceBench: leader (82.7%)
#    - Allucinazioni: minor tasso vs Gemini 3 (36% vs 50%)
#
#  Gemini per i compiti leggeri:
#    - Screener news scout (alto volume, qualità sufficiente)
#    - Embeddings RAG (Anthropic non offre embeddings)
# ══════════════════════════════════════════════════════════════════════════════
MODEL_DEEP_ANALYSIS = "claude-opus-4-7"        # Nodi: leggi_10k, news_sentiment
MODEL_LIGHT_TASKS   = "gemini-2.5-flash"        # Nodi: screener (veloce ed economico, GA 2026)
MODEL_EMBEDDINGS    = "models/gemini-embedding-001"   # RAG vector store FAISS (GA dal Feb 2026)


# ══════════════════════════════════════════════════════════════════════════════
#  SEC EDGAR — Configurazione per Segnale 1 (13-F holdings) gratuito
#  ───────────────────────────────────────────────────────────────────────────
#  La SEC fornisce dati 13-F GRATIS via la sua API ufficiale data.sec.gov.
#  Sostituisce l'endpoint FMP /institutional-ownership/extract (piano Ultimate).
#  Header User-Agent obbligatorio (policy SEC fair-access).
# ══════════════════════════════════════════════════════════════════════════════
SEC_USER_AGENT      = "ValueInvestorBot research@valueinvestorbot.com"
SEC_TICKERS_URL     = "https://www.sec.gov/files/company_tickers.json"
SEC_SUBMISSIONS_URL = "https://data.sec.gov/submissions/CIK{cik:010d}.json"
SEC_ARCHIVES_BASE   = "https://www.sec.gov/Archives/edgar/data"
SEC_RATE_LIMIT_S    = 0.15  # 6-7 req/sec, sotto la soglia SEC di 10 req/sec
SEC_CACHE_DIR       = Path("./cache")
SEC_TICKERS_CACHE   = SEC_CACHE_DIR / "sec_tickers.json"
SEC_CACHE_TTL_DAYS  = 30
SEC_FUZZY_THRESHOLD = 0.92  # soglia fuzzy match conservativa

# Suffissi geografici/state codes USA da rimuovere durante la normalizzazione
_STATE_SUFFIXES = {
    "DE", "DEL", "DELAWARE", "CA", "CAL", "CALIFORNIA", "NY", "MA", "MASS",
    "MN", "MINN", "PA", "OH", "TX", "FL", "IL", "WA", "NV", "NJ", "MD", "GA",
    "MO", "OR", "VA", "WV", "WI", "AZ", "TN", "CO", "MI", "IN", "KY", "NC",
    "SC", "KS", "OK", "AR", "UT", "ID", "MT", "ND", "SD", "NE", "ME", "VT",
    "NH", "RI", "CT", "AK", "HI", "PR", "DC", "CAN",
}
_NOISE_TOKENS = {"NEW", "OLD", "II", "III", "IV"}
_CORP_SUFFIXES = [
    "INCORPORATED", "INC", "CORPORATION", "CORP", "COMPANY", "CO",
    "LIMITED", "LTD", "LLC", "LP", "PLC", "NV", "SA", "AG", "SE", "AB",
    "HOLDINGS", "HOLDING", "GROUP", "CLASS",
]

# Holdings di emergenza: ultima ancora se SEC è irraggiungibile
# Top posizioni Berkshire pubbliche note (KO, AAPL, AXP, BAC, OXY, CVX, MCO)
SEC_EMERGENCY_HOLDINGS = {
    "AAPL": 3, "KO": 3, "AXP": 2, "BAC": 2,
    "OXY": 2, "CVX": 1, "MCO": 1,
}


# ══════════════════════════════════════════════════════════════════════════════
#  UNIVERSO BUFFETT — Riferimento storico (NON più usato come lista primaria)
#  L'universo dinamico è ora generato da node_screener combinando 4 segnali.
#  Questa lista resta come documentazione delle posizioni storiche tipiche.
# ══════════════════════════════════════════════════════════════════════════════

BUFFETT_UNIVERSE = [
    # Consumer Staples — brand eterni, pricing power inattaccabile
    "KO", "PG", "MCD", "MDLZ", "CL",
    # Financials — banche e reti di pagamento ben gestite
    "AXP", "V", "MA", "JPM", "BAC",
    # Technology con moat reale (post-2016 Buffett)
    "AAPL", "MSFT", "GOOGL",
    # Healthcare stabile — no biotech speculativo
    "JNJ", "ABT", "MCK",
    # Industriali & infrastrutture — "toll roads" dell'economia
    "UNP", "CAT", "DE", "FDX",
    # Energy selezionato — posizioni recenti Berkshire
    "OXY", "CVX",
    # Consumer Discretionary con moat di brand
    "NKE", "COST", "TGT",
    # Insurance & Diversified
    "CB", "ALL",
    # Utilities stabili con dividendi crescenti
    "NSC", "SO", "NEE",
]


# ══════════════════════════════════════════════════════════════════════════════
#  STATO CONDIVISO
# ══════════════════════════════════════════════════════════════════════════════

class AgentState(TypedDict):
    fmp_api_key: str
    capitale_totale_eur: float

    # Loop
    ticker_da_analizzare: list
    ticker_corrente: str
    risultati: list

    # Stato ticker corrente — quantitativo
    metriche_qualita: dict
    owner_earnings_attuali: float
    valore_intrinseco: float
    margine_di_sicurezza: float
    passa_test_qualita: bool
    analisi_qualitativa_testo: str
    rischio_estremo_pdf: bool
    verdetto_corrente: str
    prezzo_corrente: float

    # v2.2 — Price action (Modifica 1: anti value trap)
    price_action: dict              # drawdown_12m, distanza_52w_high, volatility
    panic_discount: bool            # prezzo crollato >X% MA fondamentali intatti
    deterioration_warning: bool     # prezzo crollato + fondamentali in calo

    # v2.2 — News sentiment (Modifica 3: distinguere panico da danno strutturale)
    news_sentiment: str             # "structural_damage" | "temporary_panic" | "neutral"
    news_summary: str               # giudizio narrativo di Buffett sulle news

    log_globale: list
    report_path: str


# ══════════════════════════════════════════════════════════════════════════════
#  HELPER FMP
# ══════════════════════════════════════════════════════════════════════════════

def fmp_get(endpoint: str, api_key: str, params: dict = None):
    """
    Wrapper unificato per le API FMP.
    v2.4: usa l'endpoint base stable https://financialmodelingprep.com/...
    Gli endpoint vecchi /v3/ sono stati ritirati dal piano di sottoscrizione
    e ritornano 403 Forbidden. Tutti i path passano da /v3/X/{ticker}
    a /stable/X?symbol={ticker}.
    """
    base = "https://financialmodelingprep.com"
    p = dict(params or {})
    p["apikey"] = api_key
    try:
        r = requests.get(f"{base}{endpoint}", params=p, timeout=25)
        if r.status_code != 200:
            params_safe = {k: v for k, v in p.items() if k != "apikey"}
            logger.warning(
                f"FMP {endpoint} → HTTP {r.status_code} | "
                f"params={params_safe} | body={r.text[:200]}"
            )
        r.raise_for_status()
        data = r.json()
        if isinstance(data, dict) and data.get("Error Message"):
            raise ValueError(data["Error Message"])
        return data
    except Exception as e:
        logger.warning(f"FMP {endpoint}: {e}")
        return []


# ══════════════════════════════════════════════════════════════════════════════
#  NODO 0: SCREENER INTELLIGENTE — "THINK LIKE BUFFETT"
#  ───────────────────────────────────────────────────────────────────────
#  Replica il processo mentale di Buffett combinando 4 segnali reali:
#    1. 13-F Holdings → cosa comprano i grandi value investor (Berkshire e altri)
#    2. Screener quantitativo FMP → pre-filtro Buffett sui fondamentali
#    3. News + Gemini Filter → identifica aziende menzionate con caratteristiche
#       "Buffett-style" (moat, brand, pricing power) anche da notizie negative
#    4. Settori Buffett-compatibili → filtra hard biotech, mining, crypto
#  L'universo finale è dinamico, prodotto ogni run, ordinato per "rank Buffett".
# ══════════════════════════════════════════════════════════════════════════════

# Settori che Buffett considera nel "cerchio di competenza"
SETTORI_BUFFETT_OK = {
    "Consumer Defensive", "Consumer Cyclical", "Financial Services",
    "Industrials", "Communication Services", "Healthcare",
    "Energy", "Utilities", "Basic Materials", "Technology",
}

# Sotto-industrie da escludere (anche se il settore è "ok")
SOTTOINDUSTRIE_BLACKLIST = {
    "Biotechnology", "Pharmaceutical Retailers", "Drug Manufacturers - Specialty & Generic",
    "Gold", "Silver", "Other Precious Metals & Mining", "Uranium",
    "Software - Application", "Semiconductor Equipment & Materials",
    "Aerospace & Defense",  # politicamente complessi
    "Tobacco",  # rischio regolamentare alto
    "Gambling", "Resorts & Casinos",
    "Airlines",  # Buffett li ha venduti tutti nel 2020
}

# Fondi 13-F da seguire (CIK SEC degli investitori value più rispettati)
# Berkshire Hathaway + altri value investor di lungo periodo
INVESTITORI_VALUE_DA_SEGUIRE = [
    ("Berkshire Hathaway",  "0001067983"),  # Warren Buffett
    ("Pershing Square",     "0001336528"),  # Bill Ackman
    ("Akre Capital",        "0001112520"),  # Chuck Akre (value moat)
    ("Markel Group",        "0001096343"),  # Tom Gayner (Buffett-like)
    ("Ruane Cunniff",       "0000728014"),  # Sequoia Fund (storico Buffett) - CIK corretto v2.5.1
]


def _ultimo_trimestre_13f() -> tuple[int, int]:
    """
    Calcola l'ultimo trimestre 13-F probabilmente disponibile.
    I 13-F vengono depositati ~45 giorni dopo fine trimestre, quindi
    usiamo sempre il trimestre precedente a quello corrente con margine.
    """
    now = datetime.datetime.now()
    # Trimestre corrente
    q_corrente = (now.month - 1) // 3 + 1
    anno = now.year
    # Vai indietro di 1 trimestre per essere sicuri che sia depositato
    q_target = q_corrente - 1
    if q_target == 0:
        q_target = 4
        anno -= 1
    return anno, q_target


# ══════════════════════════════════════════════════════════════════════════════
#  HELPER SEC EDGAR — Segnale 1 GRATUITO (sostituisce FMP $149/mese)
# ══════════════════════════════════════════════════════════════════════════════
def _sec_get(url: str, accept_json: bool = True, timeout: int = 30):
    """
    Wrapper unificato per chiamate a SEC EDGAR.
    Header User-Agent obbligatorio (SEC fair-access policy).
    Ritorna parsed JSON oppure raw text se accept_json=False.
    """
    headers = {
        "User-Agent": SEC_USER_AGENT,
        "Accept": "application/json" if accept_json else "*/*",
        "Accept-Encoding": "gzip, deflate",
        "Host": url.split("/")[2],
    }
    r = requests.get(url, headers=headers, timeout=timeout)
    r.raise_for_status()
    return r.json() if accept_json else r.text


def _normalize_company_name(name: str) -> tuple[str, str | None]:
    """
    Normalizza un nome aziendale per il matching SEC.
    Algoritmo validato in backtest (97.4% accuratezza su 76 casi reali).

    Ritorna (nome_normalizzato, classe_azionaria).
    classe = "A", "B", "C" se presente CL X / Class X, None altrimenti.

    Es:
        "MOODY'S CORP /DE/"         -> ("MOODYS", None)
        "BERKSHIRE HATHAWAY INC CL B" -> ("BERKSHIRE HATHAWAY", "B")
        "Alphabet Inc."              -> ("ALPHABET", None)
        "MARRIOTT INTERNATIONAL INC NEW" -> ("MARRIOTT INTERNATIONAL", None)
    """
    if not name:
        return "", None
    s = name.upper().strip()

    # 1. Rimuovi apostrofi (incl. typografici)
    s = s.replace("'", "").replace("`", "").replace("\u2019", "")

    # 2. Estrai classe azionaria PRIMA di rimuovere altre cose
    classe = None
    cl_match = re.search(r"\b(?:CL|CLASS)\s+([ABC])\b", s)
    if cl_match:
        classe = cl_match.group(1)
        s = re.sub(r"\b(?:CL|CLASS)\s+[ABC]\b", "", s).strip()

    # 3. Rimuovi pattern /STATE/ (es. /DE/, /NEW/, /MA/)
    s = re.sub(r"/[A-Z]+/?", "", s)

    # 4. Normalizza & -> AND, - -> spazio, punteggiatura
    s = s.replace("&", "AND").replace("-", " ")
    s = re.sub(r"[.,]", "", s)

    # 5. Rimuovi suffissi corporate e geografici iterativamente
    changed = True
    while changed:
        changed = False
        s = re.sub(r"\s+", " ", s).strip()
        tokens = s.split()
        if not tokens:
            break
        last = tokens[-1]
        if last in _CORP_SUFFIXES or last in _STATE_SUFFIXES or last in _NOISE_TOKENS:
            tokens.pop()
            s = " ".join(tokens)
            changed = True

    # 6. Pulizia finale
    s = re.sub(r"\s+", " ", s).strip()
    return s, classe


def _get_sec_company_tickers_map(logs: list) -> dict:
    """
    Scarica (o legge dalla cache) il file SEC company_tickers.json.
    Costruisce mappa { nome_normalizzato: [(ticker, classe), ...] }.

    Strategia cache:
    - File locale in SEC_TICKERS_CACHE valido per SEC_CACHE_TTL_DAYS
    - Se cache valida -> usa quella
    - Altrimenti -> scarica e salva
    - Fallback: se download fallisce, usa cache scaduta come ancora
    """
    SEC_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    raw_data = None

    # ── Step 1: cache check ────────────────────────────────────────────
    if SEC_TICKERS_CACHE.exists():
        age_days = (datetime.datetime.now().timestamp() -
                    SEC_TICKERS_CACHE.stat().st_mtime) / 86400
        if age_days < SEC_CACHE_TTL_DAYS:
            try:
                with open(SEC_TICKERS_CACHE, encoding="utf-8") as f:
                    raw_data = json.load(f)
                logs.append(f"[SCREENER] ✓ SEC tickers cache hit ({len(raw_data)} aziende, {age_days:.0f}gg)")
            except Exception as e:
                logger.warning(f"SEC cache corrotta: {e}")
                raw_data = None

    # ── Step 2: download se cache invalida ─────────────────────────────
    if raw_data is None:
        try:
            raw_data = _sec_get(SEC_TICKERS_URL, accept_json=True)
            with open(SEC_TICKERS_CACHE, "w", encoding="utf-8") as f:
                json.dump(raw_data, f)
            logs.append(f"[SCREENER] ✓ SEC tickers downloaded ({len(raw_data)} aziende)")
        except Exception as e:
            # Fallback: usa cache scaduta se esiste
            logs.append(f"[SCREENER] ⚠️ SEC download failed: {e}")
            if SEC_TICKERS_CACHE.exists():
                try:
                    with open(SEC_TICKERS_CACHE, encoding="utf-8") as f:
                        raw_data = json.load(f)
                    logs.append(f"[SCREENER] ✓ Fallback cache scaduta ({len(raw_data)} aziende)")
                except Exception:
                    return {}
            else:
                return {}

    # ── Step 3: costruisci mappa normalizzata ──────────────────────────
    mapping = {}
    for _, v in raw_data.items():
        title = v.get("title", "")
        ticker = v.get("ticker", "")
        if not title or not ticker:
            continue
        norm, classe = _normalize_company_name(title)
        if not norm:
            continue
        # Inferisci classe da ticker tipo BRK-B
        if classe is None and "-" in ticker:
            parts = ticker.split("-")
            if len(parts) == 2 and parts[1] in ("A", "B", "C"):
                classe = parts[1]
        mapping.setdefault(norm, []).append((ticker, classe))

    return mapping


def _match_holding_to_ticker(holding_name: str, ticker_map: dict) -> str | None:
    """
    Converte un nameOfIssuer del 13-F in ticker.
    Strategia in 4 step (validata 97.4% accuratezza nel backtest):
      1. Match esatto + classe corrispondente
      2. Match esatto + qualsiasi classe (preferisce A o nessuna)
      3. Match per primi 2 token (issuer key)
      4. Fuzzy match ratio >= SEC_FUZZY_THRESHOLD (default 0.92)

    Ritorna ticker oppure None se non trova match affidabile.
    """
    target, target_class = _normalize_company_name(holding_name)
    if not target:
        return None

    def _pick(candidates):
        """Da lista [(ticker, classe), ...] seleziona il migliore."""
        if target_class:
            for ticker, cls in candidates:
                if cls == target_class:
                    return ticker
        for ticker, cls in candidates:
            if cls is None or cls == "A":
                return ticker
        return candidates[0][0]

    # Step 1+2: match esatto
    if target in ticker_map:
        return _pick(ticker_map[target])

    # Step 3: primi 2 token uguali
    target_tokens = target.split()
    if len(target_tokens) >= 2:
        prefix_key = " ".join(target_tokens[:2])
        for k, candidates in ticker_map.items():
            k_tokens = k.split()
            if len(k_tokens) >= 2 and " ".join(k_tokens[:2]) == prefix_key:
                return _pick(candidates)

    # Step 4: fuzzy match con pre-filter (almeno 1 token in comune)
    best_score = 0.0
    best_candidates = None
    tt_set = set(target_tokens)
    for k, candidates in ticker_map.items():
        if not (tt_set & set(k.split())):
            continue
        score = difflib.SequenceMatcher(None, target, k).ratio()
        if score > best_score:
            best_score = score
            best_candidates = candidates
    if best_score >= SEC_FUZZY_THRESHOLD and best_candidates:
        return _pick(best_candidates)

    return None


def _fetch_latest_13f_holdings(cik: str, ticker_map: dict, logs: list, nome: str) -> list[tuple[str, float]]:
    """
    Scarica dal SEC EDGAR le posizioni del 13-F più recente per un CIK.
    Ritorna lista di (ticker, market_value) ordinata per valore decrescente.

    Pipeline:
    1. data.sec.gov/submissions/CIK{cik}.json -> lista filings
    2. Filtra 13F-HR, prende il più recente
    3. Scarica l'Information Table XML dal folder Archives
    4. Parsa XML -> estrae nameOfIssuer + value per ogni posizione
    5. Converte nameOfIssuer -> ticker via _match_holding_to_ticker
    """
    import time
    from lxml import etree

    cik_int = int(cik)

    # ── Step 1: lista submissions del fund ────────────────────────────
    try:
        time.sleep(SEC_RATE_LIMIT_S)
        subs = _sec_get(SEC_SUBMISSIONS_URL.format(cik=cik_int))
    except Exception as e:
        logs.append(f"[SCREENER] ⚠️ 13-F {nome}: submissions fail — {e}")
        return []

    recent = subs.get("filings", {}).get("recent", {})
    forms = recent.get("form", [])
    accessions = recent.get("accessionNumber", [])
    primaries = recent.get("primaryDocument", [])
    if not forms:
        return []

    # ── Step 2: trova il 13F-HR più recente ───────────────────────────
    accession = None
    primary = None
    for i, form in enumerate(forms):
        if form == "13F-HR":
            accession = accessions[i]
            primary = primaries[i] if i < len(primaries) else None
            break
    if not accession:
        logs.append(f"[SCREENER] ⚠️ 13-F {nome}: nessun 13F-HR trovato")
        return []

    accession_clean = accession.replace("-", "")

    # ── Step 3: scarica l'index del filing per trovare l'Information Table XML
    # I 13-F hanno il primary_doc.xml (info filer) + un secondo XML con le posizioni
    try:
        time.sleep(SEC_RATE_LIMIT_S)
        index_url = f"{SEC_ARCHIVES_BASE}/{cik_int}/{accession_clean}/"
        idx_json = _sec_get(f"{index_url}index.json")
    except Exception as e:
        logs.append(f"[SCREENER] ⚠️ 13-F {nome}: index fail — {e}")
        return []

    # Trova il file XML con le posizioni (di solito *_informationtable.xml o simile)
    info_table_url = None
    for item in idx_json.get("directory", {}).get("item", []):
        fname = item.get("name", "").lower()
        if fname.endswith(".xml") and ("informationtable" in fname or "infotable" in fname or "form13f" in fname):
            info_table_url = f"{index_url}{item['name']}"
            break
    # Fallback: prendi qualsiasi XML che non sia primary_doc.xml
    if not info_table_url:
        for item in idx_json.get("directory", {}).get("item", []):
            fname = item.get("name", "").lower()
            if fname.endswith(".xml") and "primary_doc" not in fname:
                info_table_url = f"{index_url}{item['name']}"
                break

    if not info_table_url:
        logs.append(f"[SCREENER] ⚠️ 13-F {nome}: information table non trovata")
        return []

    # ── Step 4: scarica e parsa l'XML ─────────────────────────────────
    try:
        time.sleep(SEC_RATE_LIMIT_S)
        xml_text = _sec_get(info_table_url, accept_json=False)
    except Exception as e:
        logs.append(f"[SCREENER] ⚠️ 13-F {nome}: XML fail — {e}")
        return []

    # Parsa XML (l'XML usa namespace, gestisco entrambi i casi)
    try:
        root = etree.fromstring(xml_text.encode("utf-8"))
    except Exception as e:
        logs.append(f"[SCREENER] ⚠️ 13-F {nome}: XML parse error — {e}")
        return []

    # Namespace: di solito {http://www.sec.gov/edgar/document/thirteenf/informationtable}
    # ma a volte è senza namespace. Cerco con local-name() per robustezza.
    posizioni = []
    for info_table in root.iter():
        tag = etree.QName(info_table).localname
        if tag != "infoTable":
            continue
        name = None
        value = 0.0
        for child in info_table.iter():
            ctag = etree.QName(child).localname
            if ctag == "nameOfIssuer" and child.text:
                name = child.text.strip()
            elif ctag == "value" and child.text:
                try:
                    value = float(child.text.strip())
                except ValueError:
                    pass
        if name and value > 0:
            posizioni.append((name, value))

    if not posizioni:
        logs.append(f"[SCREENER] ⚠️ 13-F {nome}: 0 posizioni estratte dall'XML")
        return []

    # ── Step 5: ordina per valore decrescente e converti a ticker ─────
    posizioni.sort(key=lambda x: x[1], reverse=True)
    top_20 = posizioni[:20]

    risultati = []
    miss_count = 0
    for name, value in top_20:
        ticker = _match_holding_to_ticker(name, ticker_map)
        if ticker:
            risultati.append((ticker, value))
        else:
            miss_count += 1

    if miss_count > 0:
        logs.append(f"[SCREENER]   {nome}: {miss_count}/{len(top_20)} holdings non matchati (alias commerciali o subsidiary)")

    return risultati


def _segnale_1_holdings_value(api_key: str, logs: list) -> dict:
    """
    Segnale 1: 13-F Institutional Holdings.
    v2.5: usa SEC EDGAR GRATUITO invece di FMP (che richiede piano Ultimate $149/mo).

    Pipeline per ogni fondo value:
      1. data.sec.gov/submissions/CIK{cik}.json -> trova ultimo 13F-HR
      2. /Archives/edgar/data/{cik}/{acc}/ -> trova information table XML
      3. Parsa XML -> top 20 posizioni
      4. Converte nameOfIssuer -> ticker via SEC company_tickers.json (cached)

    Robustezza a 3 livelli:
      A. Cache locale per la mappa CIK->ticker (refresh 30 giorni)
      B. Skip singolo fondo che fallisce, continua con gli altri
      C. Lista emergenza Berkshire pubblica se SEC totalmente irraggiungibile

    Note:
      - L'argomento api_key è mantenuto per retrocompatibilità ma NON USATO
        (SEC EDGAR non richiede API key).
      - Rate limiting interno (0.15s/req) sotto la policy SEC (10 req/sec).

    Ritorna {ticker: score} dove score = numero di fondi che lo detengono.
    """
    # ── Step 1: scarica mappa CIK->ticker (o usa cache) ────────────────
    ticker_map = _get_sec_company_tickers_map(logs)
    if not ticker_map:
        # Fallback C: SEC totalmente irraggiungibile -> usa lista emergenza
        logs.append("[SCREENER] ⚠️ SEC unreachable, using emergency Berkshire holdings list")
        return dict(SEC_EMERGENCY_HOLDINGS)

    # ── Step 2: per ogni fondo, scarica e parsa il 13-F più recente ───
    holdings_count = {}
    fund_ok = 0
    for nome, cik in INVESTITORI_VALUE_DA_SEGUIRE:
        try:
            positions = _fetch_latest_13f_holdings(cik, ticker_map, logs, nome)
            if positions:
                for ticker, _value in positions:
                    holdings_count[ticker] = holdings_count.get(ticker, 0) + 1
                logs.append(f"[SCREENER] ✓ 13-F {nome}: {len(positions)} top holdings")
                fund_ok += 1
        except Exception as e:
            logs.append(f"[SCREENER] ⚠️ 13-F {nome} non disponibile: {e}")

    # ── Step 3: se TUTTI i fund falliscono, usa emergenza ──────────────
    if fund_ok == 0:
        logs.append("[SCREENER] ⚠️ Nessun 13-F scaricato, using emergency holdings list")
        return dict(SEC_EMERGENCY_HOLDINGS)

    logs.append(f"[SCREENER] ✓ 13-F totali: {fund_ok}/{len(INVESTITORI_VALUE_DA_SEGUIRE)} fund OK | {len(holdings_count)} ticker unici")
    return holdings_count


def _segnale_2_quant_screener(api_key: str, logs: list) -> list:
    """
    Segnale 2: Screener quantitativo FMP con criteri pre-filtro Buffett.
    Filtra per ROE >15%, D/E <0.5, marketCap >5B, settori OK.
    """
    candidati = []
    try:
        params = {
            "marketCapMoreThan": 5_000_000_000,
            "returnOnEquityMoreThan": 0.15,       # ROE > 15%
            "debtToEquityLessThan": 0.5,
            "betaLessThan": 1.3,
            "volumeMoreThan": 300_000,
            "exchange": "NYSE,NASDAQ",
            "country": "US",
            "limit": 100,
        }
        data = fmp_get("/stable/company-screener", api_key, params)
        for s in (data or []):
            t        = s.get("symbol", "")
            settore  = s.get("sector", "")
            industry = s.get("industry", "")
            if not t:
                continue
            # Filtri di settore Buffett
            if settore and settore not in SETTORI_BUFFETT_OK:
                continue
            if industry in SOTTOINDUSTRIE_BLACKLIST:
                continue
            candidati.append({
                "ticker":    t,
                "sector":    settore,
                "industry":  industry,
                "marketCap": s.get("marketCap", 0),
                "roe":       (s.get("returnOnEquity", 0) or 0) * 100,
            })
        logs.append(f"[SCREENER] ✓ Quant screener: {len(candidati)} candidati post-filtro settoriale")
    except Exception as e:
        logs.append(f"[SCREENER] ⚠️ Quant screener FMP fallito: {e}")
    return candidati


def _segnale_3_news_buffett_filter(api_key: str, google_key: str, logs: list) -> list:
    """
    Segnale 3: News degli ultimi 7 giorni filtrate da Gemini.
    Cerca aziende menzionate con caratteristiche Buffett (moat, brand, pricing
    power, dividendi crescenti). Buffett: "Compra quando c'è sangue per le
    strade", quindi diamo peso anche a news negative su grandi aziende.
    """
    tickers_news = set()
    try:
        # Scarica news generali del mercato — ultimi 7 giorni
        # v2.4: endpoint stable per news generali del mercato
        news = fmp_get("/stable/news/stock-latest", api_key, {"page": 0, "limit": 200})
        if not news:
            logs.append("[SCREENER] ⚠️ Nessuna news disponibile")
            return []

        # Costruisci un riassunto compatto delle headline (titolo + ticker)
        headlines = []
        for n in news[:150]:
            t       = n.get("symbol", "")
            title   = (n.get("title", "") or "").strip()
            site    = n.get("site", "") or n.get("publisher", "")
            if t and title:
                headlines.append(f"[{t}] {title}  ({site})")

        if not headlines:
            return []

        # Prompt Gemini in modalità Buffett scout
        contesto = "\n".join(headlines[:120])

        llm = ChatGoogleGenerativeAI(
            model=MODEL_LIGHT_TASKS,
            google_api_key=google_key,
            temperature=0.0,
        )

        sys_p = """Sei Warren Buffett che scorri le notizie del Wall Street Journal.
Cerchi aziende interessanti da analizzare DA SOLO (non da comprare ora).
Selezioni un ticker SE E SOLO SE almeno uno dei seguenti criteri è vero:
- L'azienda ha brand iconici o pricing power riconoscibile (es. KO, NKE, AAPL)
- È una "toll road" dell'economia (ferrovie, pagamenti, utility)
- Ha appena subito una caduta di prezzo per ragioni temporanee/emotive
- Annuncio di buyback massiccio o aumento dividendo (segnale di forza)
- Risultati trimestrali ignorati dal mercato nonostante siano buoni
- È una posizione storica di Berkshire o altri value investor noti

ESCLUDI categoricamente:
- Biotech, pharma speculativa, mining oro/argento, crypto, aerospace, tabacco
- IPO, SPAC, aziende non profittevoli, meme stock
- Aziende sconosciute o di piccola capitalizzazione

Rispondi ESCLUSIVAMENTE con una lista di ticker separati da virgola, MAX 15.
Esempio: KO, AAPL, JNJ, V, MA
Nessun commento, solo i ticker."""

        usr_p = f"Notizie ultimi giorni:\n\n{contesto[:8000]}\n\nQuali ticker meritano un'analisi approfondita?"

        resp = llm.invoke([SystemMessage(content=sys_p), HumanMessage(content=usr_p)])
        raw  = resp.content.strip()

        # Parsing robusto: estrai solo i ticker validi
        for token in raw.replace("\n", ",").split(","):
            t = token.strip().upper().replace("$", "").replace(".", "")
            if t and 1 <= len(t) <= 5 and t.isalpha():
                tickers_news.add(t)
        logs.append(f"[SCREENER] ✓ News + Buffett filter: {len(tickers_news)} ticker scoperti via news")
    except Exception as e:
        logs.append(f"[SCREENER] ⚠️ News filter fallito: {e}")
    return list(tickers_news)


def _verifica_settore(ticker: str, api_key: str) -> bool:
    """Controlla che il ticker appartenga a un settore Buffett-OK."""
    try:
        # v2.4: endpoint stable, symbol come query param
        prof = fmp_get("/stable/profile", api_key, {"symbol": ticker})
        if not prof or not isinstance(prof, list):
            return False
        p = prof[0]
        sector   = p.get("sector", "")
        industry = p.get("industry", "")
        if sector and sector not in SETTORI_BUFFETT_OK:
            return False
        if industry in SOTTOINDUSTRIE_BLACKLIST:
            return False
        # Esclude penny stocks e micro cap (Buffett: solo large cap)
        # stable usa "marketCap", legacy usava "mktCap" — supportiamo entrambi
        mc = p.get("marketCap", 0) or p.get("mktCap", 0) or 0
        if mc < 3_000_000_000:
            return False
        return True
    except Exception:
        return False


def node_screener(state: AgentState) -> AgentState:
    """
    Screener intelligente multi-segnale.
    Costruisce dinamicamente l'universo da analizzare ogni run.

    v2.6 — Modalità ibrida:
      • TICKER_MANUALI vuoto + INCLUDI_SCREENER=True  → solo screener (default)
      • TICKER_MANUALI valorizzato + INCLUDI_SCREENER=True  → manuali + screener
      • TICKER_MANUALI valorizzato + INCLUDI_SCREENER=False → solo manuali (skip screener)
    """
    logs = list(state.get("log_globale", []))
    api_key    = state.get("fmp_api_key") or os.environ.get("FMP_API_KEY", "")
    google_key = os.environ.get("GOOGLE_API_KEY", "")

    logs.append(f"[SCREENER] ▶ Avvio screener intelligente — {datetime.datetime.now().strftime('%d/%m/%Y %H:%M')}")

    # ── Normalizza TICKER_MANUALI (uppercase, strip, dedupe, preserva ordine) ──
    ticker_manuali = []
    seen = set()
    for t in (TICKER_MANUALI or []):
        tn = (t or "").strip().upper()
        if tn and tn not in seen:
            ticker_manuali.append(tn)
            seen.add(tn)

    # ── Modalità "solo manuali": skip screener ──
    if ticker_manuali and not INCLUDI_SCREENER:
        logs.append(f"[SCREENER] 🎯 Modalità SOLO MANUALI ({len(ticker_manuali)} ticker): {ticker_manuali}")
        logs.append("[SCREENER] Screener saltato (INCLUDI_SCREENER=False)")
        logs.append(f"[SCREENER] Ticker da analizzare: {ticker_manuali}")
        return {
            **state,
            "ticker_da_analizzare": ticker_manuali,
            "ticker_corrente": "",
            "risultati": [],
            "log_globale": logs,
        }

    # ── Modalità "ibrida" o "solo screener": esegui i 4 segnali ──
    if ticker_manuali:
        logs.append(f"[SCREENER] 🎯 Modalità IBRIDA — manuali: {ticker_manuali}")
    logs.append("[SCREENER] Strategia: 'Think like Buffett' su 4 segnali combinati")

    # ── Segnale 1: 13-F holdings di Berkshire + altri value investor ──
    logs.append("[SCREENER] ─ Segnale 1/4: Lettura 13-F di Berkshire + value funds…")
    holdings = _segnale_1_holdings_value(api_key, logs)

    # ── Segnale 2: Screener quantitativo FMP ──────────────────────────
    logs.append("[SCREENER] ─ Segnale 2/4: Screener quantitativo (ROE>15, D/E<0.5)…")
    quant_candidates = _segnale_2_quant_screener(api_key, logs)
    quant_tickers = {c["ticker"] for c in quant_candidates}

    # ── Segnale 3: News + Gemini filter "Buffett scout" ───────────────
    logs.append("[SCREENER] ─ Segnale 3/4: Lettura news con filtro Gemini-Buffett…")
    news_tickers = set(_segnale_3_news_buffett_filter(api_key, google_key, logs))

    # ── Segnale 4: Unione + scoring ───────────────────────────────────
    logs.append("[SCREENER] ─ Segnale 4/4: Aggregazione e scoring finale…")

    scores = {}  # ticker → punteggio cumulativo

    # 13-F holdings → +3 per ogni fondo che lo detiene (peso massimo)
    for t, count in holdings.items():
        scores[t] = scores.get(t, 0) + (count * 3)

    # Quant screener → +2 per ogni titolo che passa i filtri
    for t in quant_tickers:
        scores[t] = scores.get(t, 0) + 2

    # News scout → +1 per ogni menzione rilevante
    for t in news_tickers:
        scores[t] = scores.get(t, 0) + 1

    if not scores:
        logs.append("[SCREENER] ⚠️ Nessun segnale ha prodotto risultati — uso fallback minimale")
        # Fallback: top 10 holdings Berkshire di fatto noti
        fallback = ["AAPL", "KO", "AXP", "BAC", "OXY", "MCO", "CVX", "KHC", "MA", "V"]
        for t in fallback:
            scores[t] = 1

    # ── Filtro finale: verifica settore per ogni candidato ───────────
    logs.append(f"[SCREENER] Candidati grezzi: {len(scores)}")
    universo_screener = []
    # Escludi dai candidati screener i ticker già presenti nei manuali (evita duplicati)
    candidati = [(t, s) for t, s in sorted(scores.items(), key=lambda x: x[1], reverse=True)
                 if t not in seen]
    for t, score in candidati:
        if len(universo_screener) >= UNIVERSO_FINALE_MAX_TICKET_NUMBER:
            break
        if _verifica_settore(t, api_key):
            universo_screener.append(t)

    # ── Costruzione universo finale: MANUALI in testa + SCREENER dopo ──
    # Trust mode: i ticker manuali bypassano _verifica_settore
    universo_finale = ticker_manuali + universo_screener

    # ── Logging conclusivo ───────────────────────────────────────────
    top5 = [(t, scores.get(t, 0)) for t in universo_screener[:5]]
    if ticker_manuali:
        logs.append(f"[SCREENER] 🎯 Manuali (trust mode, bypass filtro settori): {ticker_manuali}")
    logs.append(f"[SCREENER] ✅ Universo finale: {len(universo_finale)} ticker selezionati "
                f"({len(ticker_manuali)} manuali + {len(universo_screener)} screener)")
    if top5:
        logs.append(f"[SCREENER] 🏆 Top screener per ranking Buffett: {top5}")
    logs.append(f"[SCREENER] Ticker da analizzare: {universo_finale}")

    return {
        **state,
        "ticker_da_analizzare": universo_finale,
        "ticker_corrente": "",
        "risultati": [],
        "log_globale": logs,
    }


# ══════════════════════════════════════════════════════════════════════════════
#  NODO LOOP: Prossimo ticker
# ══════════════════════════════════════════════════════════════════════════════

def node_prossimo_ticker(state: AgentState) -> AgentState:
    """Estrae il prossimo ticker e resetta lo stato corrente."""
    coda = list(state.get("ticker_da_analizzare", []))
    ticker = coda.pop(0)
    logs = list(state.get("log_globale", []))
    analizzati = len(state.get("risultati", []))
    logs.append(f"\n{'─'*50}")
    logs.append(f"[LOOP]  {ticker} | Analizzati: {analizzati} | Rimasti: {len(coda)}")
    return {
        **state,
        "ticker_corrente": ticker,
        "ticker_da_analizzare": coda,
        "metriche_qualita": {},
        "owner_earnings_attuali": 0.0,
        "valore_intrinseco": 0.0,
        "margine_di_sicurezza": -100.0,
        "passa_test_qualita": False,
        "analisi_qualitativa_testo": "",
        "rischio_estremo_pdf": False,
        "verdetto_corrente": "",
        "prezzo_corrente": 0.0,
        # v2.2 — reset nuovi campi
        "price_action": {},
        "panic_discount": False,
        "deterioration_warning": False,
        "news_sentiment": "neutral",
        "news_summary": "",
        "log_globale": logs,
    }


# ══════════════════════════════════════════════════════════════════════════════
#  NODO 1: ESTRAI DATI QUANTITATIVI
# ══════════════════════════════════════════════════════════════════════════════

def node_estrai_dati(state: AgentState) -> AgentState:
    """
    Calcola: ROE medio 5y, Debt/Equity, Owner Earnings, Gross/Net Margin,
    Revenue CAGR, EPS CAGR, Current Ratio.
    Tutto secondo la filosofia quantitativa di Buffett.
    """
    ticker  = state["ticker_corrente"]
    api_key = state.get("fmp_api_key") or os.environ.get("FMP_API_KEY", "")
    logs    = list(state.get("log_globale", []))

    try:
        # v2.4: endpoint stable, symbol come query param
        income   = fmp_get("/stable/income-statement",        api_key, {"symbol": ticker, "limit": 5, "period": "annual"})
        balance  = fmp_get("/stable/balance-sheet-statement", api_key, {"symbol": ticker, "limit": 5, "period": "annual"})
        cashflow = fmp_get("/stable/cash-flow-statement",     api_key, {"symbol": ticker, "limit": 5, "period": "annual"})

        if not income or not balance or not cashflow:
            raise ValueError("Dati incompleti")

        # ROE medio 5 anni — firma del moat
        roe_list = []
        for inc, bal in zip(income, balance):
            ni = inc.get("netIncome", 0) or 0
            eq = bal.get("totalStockholdersEquity", 1) or 1
            if eq != 0:
                roe_list.append(ni / eq * 100)
        roe_medio = sum(roe_list) / len(roe_list) if roe_list else 0.0

        # Debt/Equity — solidità patrimoniale
        lb  = balance[0]
        de  = (lb.get("totalDebt", 0) or 0) / (lb.get("totalStockholdersEquity", 1) or 1)

        # Owner Earnings = Net Income + D&A - CapEx (Berkshire 1986)
        li  = income[0]
        lc  = cashflow[0]
        ni  = li.get("netIncome", 0) or 0
        da  = lc.get("depreciationAndAmortization", 0) or 0
        cap = abs(lc.get("capitalExpenditure", 0) or 0)
        oe  = ni + da - cap

        # Gross margin medio
        gm_list = [((i.get("grossProfit", 0) or 0) / max(i.get("revenue", 1) or 1, 1)) * 100 for i in income]
        gm_medio = sum(gm_list) / len(gm_list) if gm_list else 0.0

        # Net margin medio
        nm_list = [((i.get("netIncome", 0) or 0) / max(i.get("revenue", 1) or 1, 1)) * 100 for i in income]
        nm_medio = sum(nm_list) / len(nm_list) if nm_list else 0.0

        # Revenue CAGR
        if len(income) >= 2:
            r0 = income[0].get("revenue", 1) or 1
            rn = income[-1].get("revenue", 1) or 1
            rev_cagr = ((r0 / rn) ** (1 / (len(income)-1)) - 1) * 100 if rn > 0 else 0.0
        else:
            rev_cagr = 0.0

        # EPS CAGR
        eps_l = [i.get("eps", 0) or 0 for i in income]
        if len(eps_l) >= 2 and eps_l[-1]:
            eps_cagr = ((eps_l[0] / eps_l[-1]) ** (1 / (len(eps_l)-1)) - 1) * 100
        else:
            eps_cagr = 0.0

        # Current ratio
        ca = lb.get("totalCurrentAssets", 0) or 0
        cl = lb.get("totalCurrentLiabilities", 1) or 1
        cr = ca / cl if cl else 0.0

        metriche = {
            "roe_medio_5y": round(roe_medio, 2),
            "roe_per_anno": [round(r, 2) for r in roe_list],
            "debt_equity": round(de, 2),
            "owner_earnings_usd": oe,
            "gross_margin_medio": round(gm_medio, 2),
            "net_margin_medio": round(nm_medio, 2),
            "revenue_cagr_pct": round(rev_cagr, 2),
            "eps_cagr_pct": round(eps_cagr, 2),
            "current_ratio": round(cr, 2),
            "net_income": ni,
            "depreciation": da,
            "capex": cap,
        }
        logs.append(f"[DATI]  {ticker} → ROE:{roe_medio:.1f}% D/E:{de:.2f} OE:${oe:,.0f} GM:{gm_medio:.1f}%")
        return {**state, "metriche_qualita": metriche, "owner_earnings_attuali": float(oe), "log_globale": logs}

    except Exception as e:
        logs.append(f"[DATI]  {ticker} ⚠️ {e}")
        return {**state, "metriche_qualita": {"errore": str(e)}, "owner_earnings_attuali": 0.0, "log_globale": logs}


# ══════════════════════════════════════════════════════════════════════════════
#  NODO 2: ANALISI 10-K + 10-Q — CHARLIE MUNGER RAG
#  v2.2: legge SIA il 10-K annuale (visione strategica) SIA l'ultimo 10-Q
#  trimestrale (cattura problemi emergenti negli ultimi 3 mesi).
# ══════════════════════════════════════════════════════════════════════════════

def _download_sec_filing(ticker: str, filing_type: str, api_key: str, suffix: str = "") -> str | None:
    """
    Helper: trova il filing più recente del tipo richiesto, lo scarica
    dalla SEC e lo salva in una cartella temporanea cross-platform.
    Restituisce il path locale al file scaricato, oppure None se fallisce.

    Logging diagnostico esplicito su ogni step di possibile fallimento:
    - step 1: ricerca URL via FMP
    - step 2: download HTTP dalla SEC
    - step 3: scrittura su filesystem
    """
    # ── Step 1: trova URL del filing via FMP ────────────────────────────
    # v2.4: il nuovo endpoint richiede 'from'/'to' (range date).
    # Cerchiamo nei 2 anni precedenti per essere sicuri di trovare l'ultimo
    # 10-K (annuale) e l'ultimo 10-Q (trimestrale).
    try:
        oggi = datetime.datetime.now()
        due_anni_fa = oggi - datetime.timedelta(days=730)
        filings = fmp_get(
            "/stable/sec-filings-search/symbol",
            api_key,
            {
                "symbol":   ticker,
                "from":     due_anni_fa.strftime("%Y-%m-%d"),
                "to":       oggi.strftime("%Y-%m-%d"),
                "page":     0,
                "limit":    100,
            },
        )
        if not filings:
            logger.warning(f"[{ticker}] {filing_type}: FMP non ha restituito filing")
            return None
    except Exception as e:
        logger.warning(f"[{ticker}] {filing_type}: errore chiamata FMP — {e}")
        return None

    # I filing ricevuti coprono tutti i tipi (10-K, 10-Q, 8-K, ecc.)
    # Devo filtrare per il tipo richiesto e prendere il più recente
    url = None
    filings_filtrati = [
        f for f in filings
        if (f.get("formType") or f.get("type") or "").upper() == filing_type.upper()
    ]
    # Ordina per data (acceptedDate o filingDate decrescente)
    filings_filtrati.sort(
        key=lambda x: x.get("acceptedDate") or x.get("filingDate") or "",
        reverse=True,
    )
    for f in filings_filtrati:
        url = f.get("finalLink") or f.get("link") or f.get("linkToFilingDetails")
        if url:
            break

    if not url:
        logger.warning(
            f"[{ticker}] {filing_type}: nessun URL trovato nei {len(filings)} filing ricevuti"
        )
        return None

    # ── Step 2: download HTML dalla SEC ─────────────────────────────────
    headers = {
        "User-Agent": "ValueInvestorBot research@valueinvestorbot.com",
        "Accept-Encoding": "gzip, deflate",
        "Host": "www.sec.gov",
    }
    try:
        resp = requests.get(url, headers=headers, timeout=60)
        resp.raise_for_status()
        if not resp.content:
            logger.warning(f"[{ticker}] {filing_type}: download SEC restituisce body vuoto")
            return None
    except requests.HTTPError as e:
        logger.warning(
            f"[{ticker}] {filing_type}: SEC HTTP {e.response.status_code if e.response else '?'} — {url[:80]}"
        )
        return None
    except requests.RequestException as e:
        logger.warning(f"[{ticker}] {filing_type}: errore network SEC — {e}")
        return None

    # ── Step 3: scrittura su tempdir cross-platform ─────────────────────
    # Su Windows è %TEMP% (es. C:\Users\<u>\AppData\Local\Temp)
    # Su Linux/Mac è /tmp/
    try:
        tmp_dir = Path(tempfile.gettempdir()) / "value_investor_bot"
        tmp_dir.mkdir(parents=True, exist_ok=True)
        filename = f"{ticker}_{filing_type.replace('-', '').lower()}{suffix}.html"
        tmp_path = tmp_dir / filename
        with open(tmp_path, "wb") as fh:
            fh.write(resp.content)
        size_kb = tmp_path.stat().st_size / 1024
        logger.info(f"[{ticker}] {filing_type} → salvato in {tmp_path} ({size_kb:.0f} KB)")
        return str(tmp_path)
    except OSError as e:
        logger.warning(f"[{ticker}] {filing_type}: errore scrittura file — {e}")
        return None


def _build_faiss_with_retry(chunks: list, embeddings, ticker: str, logs: list,
                             max_retries: int = 5, batch_size: int = 20,
                             pause_between_batches_s: float = 0.5):
    """
    Costruisce FAISS vector store con throttling AGGRESSIVO + retry su 429.

    PROBLEMA: il free tier di gemini-embedding-001 NON è 100 req/min totali,
    ma una sliding window che valuta i picchi di velocità. 5 batch in 4s
    vengono rifiutati anche se siamo sotto le 100 req/min totali.

    SOLUZIONE: 1 batch ogni 8 secondi = 7.5 batch/min = ~150 chunks/min.
    Lento ma affidabile sul free tier.

    Se vuoi velocità reale, abilita il paid tier su:
    https://aistudio.google.com/billing
    e cambia pause_between_batches_s a 0.5.

    Strategia:
    1. Suddivide chunks in batch piccoli (default 20 invece di 60)
    2. Pausa LUNGA tra batch (8s default) per stare nettamente sotto il limite
    3. Retry con backoff esponenziale aggressivo per 429 residui:
       30s → 60s → 90s → 120s → 150s
    """
    import time
    import re as _re

    if not chunks:
        raise ValueError("Nessun chunk da embeddare")

    # Suddividi in batch piccoli
    batches = [chunks[i:i + batch_size] for i in range(0, len(chunks), batch_size)]
    n_batches = len(batches)
    eta_s = n_batches * pause_between_batches_s
    logs.append(f"[10-K]  {ticker} → embedding {len(chunks)} chunks in {n_batches} batch (ETA ~{eta_s:.0f}s)")

    vs = None
    for i, batch in enumerate(batches, 1):
        attempt = 0
        success = False
        while attempt < max_retries:
            try:
                if vs is None:
                    vs = FAISS.from_documents(batch, embeddings)
                else:
                    vs.add_documents(batch)
                success = True
                break
            except Exception as e:
                msg = str(e)
                if "429" in msg or "RESOURCE_EXHAUSTED" in msg or "quota" in msg.lower():
                    # Backoff esponenziale aggressivo: 30s, 60s, 90s, 120s, 150s
                    wait_s = 30.0 * (attempt + 1)
                    # Se Google suggerisce un wait più lungo, usalo
                    m = _re.search(r"retry in (\d+(?:\.\d+)?)s", msg, _re.IGNORECASE)
                    if not m:
                        m = _re.search(r"retryDelay['\"]?:\s*['\"]?(\d+)s", msg)
                    if m:
                        wait_s = max(wait_s, float(m.group(1)) + 5.0)
                    attempt += 1
                    logs.append(f"[10-K]  {ticker} → batch {i}/{n_batches} 429, retry in {wait_s:.0f}s (tentativo {attempt}/{max_retries})")
                    time.sleep(wait_s)
                else:
                    raise

        if not success:
            # Esaurito max_retries — log soft failure ma continua con i batch già OK
            logs.append(f"[10-K]  {ticker} → batch {i}/{n_batches} skip dopo {max_retries} tentativi, proseguo con i {(i-1)*batch_size} chunks già indicizzati")
            if vs is None:
                # Nessun batch è andato a buon fine: errore vero
                raise RuntimeError(f"Tutti i tentativi di embedding falliti per {ticker}")
            # Altrimenti: vs ha già almeno il primo batch, possiamo procedere parzialmente
            break

        # Pausa preventiva tra batch (eccetto l'ultimo)
        if i < n_batches and pause_between_batches_s > 0:
            time.sleep(pause_between_batches_s)

    logs.append(f"[10-K]  {ticker} → vector store FAISS pronto")
    return vs


def node_leggi_report_10k(state: AgentState) -> AgentState:
    """
    v2.2 — Analisi qualitativa SEC su 10-K + ultimo 10-Q.
    Combina i chunks dei due documenti in un unico vector store FAISS.
    Munger valuta cause legali, debiti, rischi competitivi, e in più
    cattura segnali di deterioramento emersi nell'ultimo trimestre.
    """
    ticker     = state["ticker_corrente"]
    api_key    = state.get("fmp_api_key") or os.environ.get("FMP_API_KEY", "")
    google_key = os.environ.get("GOOGLE_API_KEY", "")
    logs       = list(state.get("log_globale", []))
    analisi    = "Analisi qualitativa non disponibile."
    rischio    = False

    try:
        # ── Step 1: scarica 10-K (annuale) e 10-Q (trimestrale) ────────
        path_10k = _download_sec_filing(ticker, "10-K", api_key)
        path_10q = _download_sec_filing(ticker, "10-Q", api_key)

        if not path_10k and not path_10q:
            raise ValueError("Nessun filing 10-K né 10-Q disponibile")

        # ── Step 2: parsing e chunking di entrambi ─────────────────────
        # v2.6.2: chunk_size più grosso per ridurre numero di chunks e
        # rispettare il rate limit del free tier Gemini (100 req/min).
        # 3000 char ≈ 750 tokens, ben sotto i 2048 del modello embedding.
        all_chunks = []
        splitter = RecursiveCharacterTextSplitter(chunk_size=2000, chunk_overlap=200)

        if path_10k:
            docs_k = BSHTMLLoader(path_10k, bs_kwargs={"features": "lxml"}).load()
            chunks_k = splitter.split_documents(docs_k)
            # Marca i chunk del 10-K nel metadato
            for c in chunks_k:
                c.metadata["source_type"] = "10-K (annuale)"
            all_chunks.extend(chunks_k)
            logs.append(f"[10-K]  {ticker} → 10-K caricato ({len(chunks_k)} chunks)")

        if path_10q:
            docs_q = BSHTMLLoader(path_10q, bs_kwargs={"features": "lxml"}).load()
            chunks_q = splitter.split_documents(docs_q)
            for c in chunks_q:
                c.metadata["source_type"] = "10-Q (trimestrale)"
            all_chunks.extend(chunks_q)
            logs.append(f"[10-Q]  {ticker} → 10-Q caricato ({len(chunks_q)} chunks)")

        if not all_chunks:
            raise ValueError("Nessun chunk estratto dai filing")

        # ── Step 3: vector store FAISS unificato (embeddings: Gemini o Locale) ──
        if MODALITA_EMBEDDING == 0:
            # Locale: Modello Large ad altissime prestazioni (competitivo con Google/OpenAI)
            logs.append(f"[10-K]  {ticker} → Inizializzazione modello embedding locale BGE-Large...")
            
            # Parametri ottimali per BGE-Large
            model_kwargs = {'device': CPU_OR_CUDA} # Usa la CPU (o 'cuda' se hai una GPU Nvidia configurata)
            encode_kwargs = {'normalize_embeddings': True} # Fondamentale per calcolare correttamente la Cosine Similarity
            
            emb = HuggingFaceEmbeddings(
                model_name="BAAI/bge-large-en-v1.5",
                model_kwargs=model_kwargs,
                encode_kwargs=encode_kwargs
            )
            # Batch da 100 per non saturare la RAM della CPU in un colpo solo
            vs = _build_faiss_with_retry(all_chunks, emb, ticker, logs, batch_size=100, pause_between_batches_s=0.0)
        else:
            # Cloud: retry automatico con exponential backoff per 429
            emb = GoogleGenerativeAIEmbeddings(model=MODEL_EMBEDDINGS, google_api_key=google_key)
            vs = _build_faiss_with_retry(all_chunks, emb, ticker, logs)

        # v2.6.5: retrieval BILANCIATO tra 10-K e 10-Q.
        # Problema risolto: i 10-Q SEC sono in formato iXBRL (Workiva) che produce
        # chunks frammentati. In un retriever unico, i chunks 10-K (HTML pulito)
        # vincono sempre la similarity search, e Munger non vede mai il 10-Q.
        # Soluzione: 2 retriever filtrati per source_type, garantiamo presenza di entrambi.
        ret_10k = vs.as_retriever(
            search_kwargs={
                "k": 3,
                "filter": {"source_type": "10-K (annuale)"},
            }
        )
        ret_10q = vs.as_retriever(
            search_kwargs={
                "k": 3,
                "filter": {"source_type": "10-Q (trimestrale)"},
            }
        )
        # Fallback retriever generico (se uno dei due non ha chunks)
        ret_any = vs.as_retriever(search_kwargs={"k": 6})

        # ── Step 4: query Munger-style (inversione) ────────────────────
        queries = [
            "risks factors competitive threats disruption",
            "legal proceedings lawsuits litigation SEC investigation",
            "debt obligations covenant default liquidity",
            "customer concentration supplier dependency",
            "going concern financial distress",
            "management fraud governance key person",
            "technology obsolescence AI competition",
            "regulatory government penalty fine",
            "guidance lowered missed estimates revenue decline",  # v2.2: emergent issues
            "subsequent events material adverse change",          # v2.2: post-10K events
        ]
        paragrafi = []
        chunks_10k_count = 0
        chunks_10q_count = 0
        for q in queries:
            # v2.6.5: cerca in entrambe le fonti separatamente per garantire bilanciamento
            docs_da_processare = []
            try:
                docs_k = ret_10k.invoke(q)
                docs_da_processare.extend(docs_k)
            except Exception:
                pass
            try:
                docs_q = ret_10q.invoke(q)
                docs_da_processare.extend(docs_q)
            except Exception:
                pass
            # Se entrambi i retriever filtered hanno fallito, fallback al generico
            if not docs_da_processare:
                try:
                    docs_da_processare = ret_any.invoke(q)
                except Exception:
                    pass

            for doc in docs_da_processare:
                c = doc.page_content.strip()
                if c and c not in [p.split("] ", 1)[-1] if "] " in p else p for p in paragrafi]:
                    src = doc.metadata.get("source_type", "")
                    paragrafi.append(f"[{src}] {c}")
                    if "10-K" in src:
                        chunks_10k_count += 1
                    elif "10-Q" in src:
                        chunks_10q_count += 1

        logs.append(f"[SEC]   {ticker} → retrieval: {chunks_10k_count} chunks 10-K + {chunks_10q_count} chunks 10-Q")

        # Aumentato a 20 (era 14) per accomodare contributi da entrambe le fonti
        contesto = "\n\n---\n\n".join(paragrafi[:20])
        m = state.get("metriche_qualita", {})

        # ── Step 5: chiama Claude Opus 4.7 come Charlie Munger ─────────
        # v2.3: passaggio a Opus 4.7 per analisi finanziaria profonda
        # Benchmark: FinanceBench 82.7% | Finance Agent v1.1 leader | min hallucinations
        anthropic_key = os.environ.get("ANTHROPIC_API_KEY", "")
        if not anthropic_key:
            raise ValueError("ANTHROPIC_API_KEY mancante nel .env — necessaria per Opus 4.7")

        # NOTA Opus 4.7: temperature/top_p/top_k sono rimossi dall'API.
        # Settarli causa 400 error. Il modello usa Adaptive Thinking internamente.
        llm = ChatAnthropic(
            model=MODEL_DEEP_ANALYSIS,
            anthropic_api_key=anthropic_key,
            max_tokens=2000,
        )

        sys_p = """Sei Charlie Munger. Applichi il principio di INVERSIONE: prima cerca cosa può distruggere l'investimento.
Sei brutalmente onesto. Analizzi DUE fonti: il 10-K annuale (visione strategica) e l'ultimo 10-Q (segnali emergenti).
Presta particolare attenzione ai segnali del 10-Q: guidance ridotta, ricavi in calo, subsequent events negativi.

Rispondi SEMPRE con questo formato esatto:

RISCHIO_ESTREMO: [SI/NO]
MOTIVAZIONE: [3-4 frasi dirette, nessuna diplomazia]
RISCHI_IDENTIFICATI:
- [rischio]
PUNTI_DI_FORZA:
- [forza]
SEGNALI_RECENTI_10Q:
- [eventuali segnali emersi nell'ultimo trimestre, oppure "nessun segnale di rilievo"]

RISCHIO_ESTREMO: SI solo se: cause legali esistenziali, debito minaccia la sopravvivenza, obsolescenza totale entro 5 anni, un cliente >50% ricavi, segnali di frode, oppure deterioramento severo confermato dal 10-Q."""

        usr_p = f"""Analizza i filing SEC di {ticker}.
Dati noti → ROE 5y:{m.get('roe_medio_5y','N/D')}% | D/E:{m.get('debt_equity','N/D')} | GM:{m.get('gross_margin_medio','N/D')}%

ESTRATTI FILING (10-K e 10-Q):
{contesto[:8000]}"""

        rr = llm.invoke([SystemMessage(content=sys_p), HumanMessage(content=usr_p)])
        analisi = rr.content
        rischio = "RISCHIO_ESTREMO: SI" in analisi.upper()
        logs.append(f"[SEC]   {ticker} → {'⚠️ RISCHIO ESTREMO' if rischio else '✅ OK qualitativo'} (Opus 4.7)")

    except Exception as e:
        # v2.2: errore esplicito nell'analisi (visibile poi nel report HTML)
        analisi = f"Analisi qualitativa non disponibile.\n\nMotivo tecnico: {type(e).__name__}: {e}"
        logs.append(f"[SEC]   {ticker} ⚠️ {type(e).__name__}: {e}")

    return {**state, "analisi_qualitativa_testo": analisi, "rischio_estremo_pdf": rischio, "log_globale": logs}


# ══════════════════════════════════════════════════════════════════════════════
#  NODO 2.5: NEWS SENTIMENT — Buffett vs Value Trap
#  v2.2: Modifica 3. Legge le news degli ultimi 90 giorni e chiede a Gemini
#  (in modalità Buffett) se i problemi sono STRUTTURALI o CICLICI/TEMPORANEI.
#  È il filtro che distingue un'occasione (AmEx 1963) da una value trap.
# ══════════════════════════════════════════════════════════════════════════════

def node_news_sentiment(state: AgentState) -> AgentState:
    """
    Analizza le news recenti per classificare un eventuale calo in:
      - "temporary_panic"     → opportunità Buffett (compra il sangue)
      - "structural_damage"   → value trap (evita anche a sconto)
      - "neutral"             → nessun catalizzatore particolare nelle news
    """
    ticker     = state["ticker_corrente"]
    api_key    = state.get("fmp_api_key") or os.environ.get("FMP_API_KEY", "")
    google_key = os.environ.get("GOOGLE_API_KEY", "")
    logs       = list(state.get("log_globale", []))

    sentiment = "neutral"
    summary   = "Nessuna analisi news disponibile."

    try:
        # v2.4: endpoint stable, parametro 'symbols' invece di 'tickers'
        news_raw = fmp_get(
            "/stable/news/stock",
            api_key,
            {"symbols": ticker, "page": 0, "limit": NEWS_MAX_ITEMS},
        )

        if not news_raw:
            logs.append(f"[NEWS]  {ticker} → nessuna news disponibile")
            return {
                **state,
                "news_sentiment": "neutral",
                "news_summary":   "Nessuna news rilevante negli ultimi 90 giorni.",
                "log_globale":    logs,
            }

        # Filtra per data (FMP restituisce ISO date in 'publishedDate')
        cutoff = datetime.datetime.now() - datetime.timedelta(days=NEWS_LOOKBACK_DAYS)
        news_recenti = []
        for n in news_raw:
            try:
                pub_date_str = n.get("publishedDate", "")
                pub_date = datetime.datetime.fromisoformat(pub_date_str.replace("Z", ""))
                if pub_date >= cutoff:
                    title = (n.get("title", "") or "").strip()
                    text  = (n.get("text", "") or "").strip()[:300]
                    site  = n.get("site", "")
                    news_recenti.append(f"[{pub_date.strftime('%Y-%m-%d')}] [{site}] {title}\n  {text}")
            except Exception:
                continue

        if not news_recenti:
            logs.append(f"[NEWS]  {ticker} → nessuna news negli ultimi {NEWS_LOOKBACK_DAYS} giorni")
            return {
                **state,
                "news_sentiment": "neutral",
                "news_summary":   "Nessuna news degli ultimi 90 giorni.",
                "log_globale":    logs,
            }

        contesto_news = "\n\n".join(news_recenti[:25])

        # ── Prompt Buffett: distingue panico da deterioramento strutturale ──
        # v2.3: Claude Opus 4.7 — ragionamento qualitativo sfumato, min hallucinations
        anthropic_key = os.environ.get("ANTHROPIC_API_KEY", "")
        if not anthropic_key:
            raise ValueError("ANTHROPIC_API_KEY mancante nel .env — necessaria per Opus 4.7")

        # NOTA Opus 4.7: temperature/top_p/top_k sono rimossi dall'API (vedi commento sopra).
        llm = ChatAnthropic(
            model=MODEL_DEEP_ANALYSIS,
            anthropic_api_key=anthropic_key,
            max_tokens=1500,
        )

        sys_p = """Sei Warren Buffett. Analizzi le news recenti su un'azienda per rispondere a UNA SOLA domanda:
"Il prezzo basso/in calo è dovuto a PANICO TEMPORANEO (opportunità Buffett-style come AmEx 1963, KO 1988, WFC 1990)
oppure a DETERIORAMENTO STRUTTURALE (perdita di moat, danno reputazionale permanente, obsolescenza, frode)?"

PANIC TEMPORANEO = il moat è intatto, il problema è ciclico/macro/emotivo/risolvibile in 1-2 anni:
  - Recessione di settore generale
  - Panico macroeconomico (tassi, inflazione, geopolitica)
  - Trimestrale debole per cause one-off (cambi valutari, weather, fornitore unico)
  - Notizia negativa transitoria già prezzata in modo esagerato
  - Settore "noioso" temporaneamente fuori moda

DETERIORAMENTO STRUTTURALE = il moat è compromesso o l'azienda è in pericolo:
  - Perdita di market share strutturale verso concorrenti
  - Tecnologia core in obsolescenza (es. Kodak vs digitale, Blockbuster vs Netflix)
  - Cause legali esistenziali, indagini regolatorie attive
  - Frode contabile, dimissioni CEO/CFO sospette
  - Calo prolungato di ricavi/margini su più trimestri consecutivi
  - Dipendenza da un solo cliente o prodotto in crisi

Rispondi SEMPRE con questo formato esatto:

CLASSIFICAZIONE: [TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL]
MOTIVAZIONE: [3-4 frasi che spiegano il giudizio]
SEGNALI_CHIAVE:
- [segnale 1]
- [segnale 2]"""

        usr_p = f"""News degli ultimi {NEWS_LOOKBACK_DAYS} giorni su {ticker}:

{contesto_news[:7000]}

Qual è la tua classificazione?"""

        resp = llm.invoke([SystemMessage(content=sys_p), HumanMessage(content=usr_p)])
        summary = resp.content

        # Parsing della classificazione
        up = summary.upper()
        if "CLASSIFICAZIONE: TEMPORARY_PANIC" in up or "TEMPORARY_PANIC" in up.split("CLASSIFICAZIONE:")[-1][:50]:
            sentiment = "temporary_panic"
        elif "CLASSIFICAZIONE: STRUCTURAL_DAMAGE" in up or "STRUCTURAL_DAMAGE" in up.split("CLASSIFICAZIONE:")[-1][:50]:
            sentiment = "structural_damage"
        else:
            sentiment = "neutral"

        emoji = {"temporary_panic": "🔥", "structural_damage": "⚠️", "neutral": "—"}[sentiment]
        logs.append(f"[NEWS]  {ticker} → {emoji} {sentiment.upper()} (Opus 4.7)")

    except Exception as e:
        logs.append(f"[NEWS]  {ticker} ⚠️ {e}")

    return {
        **state,
        "news_sentiment": sentiment,
        "news_summary":   summary,
        "log_globale":    logs,
    }


# ══════════════════════════════════════════════════════════════════════════════
#  NODO 2.7: PRICE ACTION — Drawdown + flag panic/deterioration
#  v2.2: Modifica 1. Analizza l'andamento prezzo a 12 mesi per identificare:
#    • panic_discount: drawdown >35% MA fondamentali ancora intatti
#    • deterioration_warning: drawdown >25% + ricavi/utili in calo
# ══════════════════════════════════════════════════════════════════════════════

def node_check_price_action(state: AgentState) -> AgentState:
    """
    Analizza lo storico prezzi a 12 mesi e combina con i fondamentali
    per produrre flag operativi. È il nodo che distingue "panic buy" da
    "value trap".
    """
    ticker  = state["ticker_corrente"]
    api_key = state.get("fmp_api_key") or os.environ.get("FMP_API_KEY", "")
    logs    = list(state.get("log_globale", []))
    m       = state.get("metriche_qualita", {})

    panic     = False
    warning   = False
    pa_dict   = {}

    try:
        # ── Scarica 1 anno di prezzi storici ───────────────────────────
        # v2.4: endpoint stable historical-price-eod/full
        end   = datetime.datetime.now()
        start = end - datetime.timedelta(days=380)
        hist = fmp_get(
            "/stable/historical-price-eod/full",
            api_key,
            {
                "symbol": ticker,
                "from":   start.strftime("%Y-%m-%d"),
                "to":     end.strftime("%Y-%m-%d"),
            },
        )

        # v2.4: il nuovo endpoint può restituire:
        #   - direttamente una lista piatta [{date, close, ...}, ...]
        #   - oppure l'oggetto legacy {symbol, historical: [...]}
        if isinstance(hist, dict):
            historical = hist.get("historical", []) or []
        elif isinstance(hist, list):
            historical = hist
        else:
            historical = []

        if not historical:
            raise ValueError("Storico prezzi non disponibile")

        # Prezzi: dal più recente al più vecchio
        closes = [h.get("close", 0) for h in historical if h.get("close")]
        if not closes:
            raise ValueError("Nessun close price valido")

        prezzo_oggi    = closes[0]
        max_52w        = max(closes)
        min_52w        = min(closes)

        # Drawdown corrente dal massimo 52-week
        drawdown_dal_max = (max_52w - prezzo_oggi) / max_52w * 100 if max_52w > 0 else 0.0

        # Distanza dal massimo 52-week (può essere 0 se siamo al top)
        distanza_52w_high = drawdown_dal_max

        # Performance 12 mesi (prezzo oggi vs 12 mesi fa)
        prezzo_12m_fa = closes[-1] if len(closes) > 200 else closes[-1]
        perf_12m = (prezzo_oggi - prezzo_12m_fa) / prezzo_12m_fa * 100 if prezzo_12m_fa > 0 else 0.0

        # Volatilità annualizzata (std dei rendimenti giornalieri * sqrt(252))
        rendimenti = []
        for i in range(len(closes) - 1):
            if closes[i+1] > 0:
                rendimenti.append((closes[i] - closes[i+1]) / closes[i+1])
        if rendimenti:
            mean_r  = sum(rendimenti) / len(rendimenti)
            var_r   = sum((r - mean_r) ** 2 for r in rendimenti) / len(rendimenti)
            vol_ann = (var_r ** 0.5) * (252 ** 0.5) * 100
        else:
            vol_ann = 0.0

        pa_dict = {
            "drawdown_dal_max_52w": round(drawdown_dal_max, 1),
            "distanza_52w_high":    round(distanza_52w_high, 1),
            "performance_12m":      round(perf_12m, 1),
            "volatilita_annua":     round(vol_ann, 1),
            "prezzo_oggi":          round(prezzo_oggi, 2),
            "max_52w":              round(max_52w, 2),
            "min_52w":              round(min_52w, 2),
        }

        # ── Logica panic_discount vs deterioration_warning ────────────
        # Segnali di deterioramento dai fondamentali:
        rev_cagr  = m.get("revenue_cagr_pct", 0.0)
        eps_cagr  = m.get("eps_cagr_pct", 0.0)
        roe       = m.get("roe_medio_5y", 0.0)
        de        = m.get("debt_equity", 999.0)

        fondamentali_solidi   = (roe > 15.0) and (de < 0.5) and (rev_cagr > 0)
        fondamentali_in_calo  = (rev_cagr < 0) or (eps_cagr < -10)

        news_sent = state.get("news_sentiment", "neutral")

        # PANIC DISCOUNT: crollo significativo, ma fondamentali ok
        # e news non indicano danno strutturale
        if (drawdown_dal_max >= PANIC_DRAWDOWN_THRESHOLD
            and fondamentali_solidi
            and news_sent != "structural_damage"):
            panic = True

        # DETERIORATION WARNING: crollo + segnali strutturali negativi
        if (drawdown_dal_max >= WARNING_DRAWDOWN_THRESHOLD
            and (fondamentali_in_calo or news_sent == "structural_damage")):
            warning = True

        flag_emoji = ""
        if panic:   flag_emoji += " 🔥 PANIC_BUY"
        if warning: flag_emoji += " ⚠️ WARNING_TRAP"

        logs.append(
            f"[PRICE] {ticker} → DD:{drawdown_dal_max:.1f}% | 12m:{perf_12m:+.1f}% | "
            f"Vol:{vol_ann:.1f}%{flag_emoji}"
        )

    except Exception as e:
        logs.append(f"[PRICE] {ticker} ⚠️ {e}")

    return {
        **state,
        "price_action":          pa_dict,
        "panic_discount":        panic,
        "deterioration_warning": warning,
        "log_globale":           logs,
    }


# ══════════════════════════════════════════════════════════════════════════════
#  NODO 3: DCF BUFFETT
# ══════════════════════════════════════════════════════════════════════════════

def node_calcola_valore_buffett(state: AgentState) -> AgentState:
    """
    DCF a due stadi con Owner Earnings.
    Fase 1: 5% crescita per 10 anni. Fase 2: 2% terminal growth.
    Discount rate: 4.5% (Treasury-based, metodo Buffett).
    Test qualità: ROE > 15% AND D/E < 0.5.
    """
    ticker   = state["ticker_corrente"]
    api_key  = state.get("fmp_api_key") or os.environ.get("FMP_API_KEY", "")
    logs     = list(state.get("log_globale", []))
    m        = state.get("metriche_qualita", {})
    oe       = state.get("owner_earnings_attuali", 0.0)

    roe  = m.get("roe_medio_5y", 0.0)
    de   = m.get("debt_equity", 999.0)
    passa = (roe > 15.0) and (de < 0.5)

    # DCF parametri conservativi
    g1, g2, r, anni = 0.05, 0.02, 0.045, 10
    pv1 = sum(oe * ((1 + g1) ** n) / ((1 + r) ** n) for n in range(1, anni + 1))
    tv  = oe * ((1 + g1) ** anni) * (1 + g2) / (r - g2)
    pv2 = tv / ((1 + r) ** anni)
    val_tot = pv1 + pv2

    val_az, prezzo, margine = 0.0, 0.0, -100.0
    try:
        # v2.4: endpoint stable, symbol come query param
        q = fmp_get("/stable/quote", api_key, {"symbol": ticker})
        q = q[0] if isinstance(q, list) and q else {}
        prezzo = q.get("price", 0.0) or 0.0
        shares = q.get("sharesOutstanding", 0) or 0
        if shares <= 0:
            mc = q.get("marketCap", 0) or 0
            shares = mc / prezzo if prezzo > 0 else 1
        if shares > 0:
            val_az = val_tot / shares
        if val_az > 0:
            margine = (val_az - prezzo) / val_az * 100
        m["prezzo_corrente"]   = round(prezzo, 2)
        m["valore_per_azione"] = round(val_az, 2)
        m["pv_fase1"]          = round(pv1, 0)
        m["pv_terminal"]       = round(pv2, 0)
        logs.append(f"[DCF]   {ticker} → P:${prezzo:.2f} IV:${val_az:.2f} MoS:{margine:.1f}% Q:{'✅' if passa else '❌'}")
    except Exception as e:
        logs.append(f"[DCF]   {ticker} ⚠️ {e}")

    return {**state, "metriche_qualita": m, "valore_intrinseco": float(val_az),
            "margine_di_sicurezza": float(margine), "passa_test_qualita": bool(passa),
            "prezzo_corrente": float(prezzo), "log_globale": logs}


# ══════════════════════════════════════════════════════════════════════════════
#  NODI VERDETTO
# ══════════════════════════════════════════════════════════════════════════════

def _calcola_position_size(margine: float, capitale: float, verdetto: str, panic: bool = False) -> float:
    """
    Position sizing conservativo per portafoglio <5000€.
    Massimo 7 posizioni. Scala con il margine di sicurezza.
    Riserva sempre 15% liquidità.
    v2.2: PANIC_BUY → allocazione maggiorata (Buffett: "Quando piove oro, prendi un secchio").
    """
    if verdetto not in ("APPROVATO", "APPROVATO_PANIC_BUY"):
        return 0.0
    base = (capitale * 0.85) / 7
    if margine > 50:   mult = 1.3
    elif margine > 40: mult = 1.1
    elif margine > 30: mult = 0.9
    else:              mult = 0.7
    # Bonus panic buy: Buffett comprava di più quando trovava queste occasioni
    if panic:
        mult *= 1.2
    return round(max(300.0, min(900.0, base * mult)), 2)


def _salva(state: AgentState, verdetto: str) -> AgentState:
    m        = state.get("metriche_qualita", {})
    capitale = state.get("capitale_totale_eur", 4500.0)
    margine  = state.get("margine_di_sicurezza", -100.0)
    panic    = state.get("panic_discount", False)
    pos      = _calcola_position_size(margine, capitale, verdetto, panic)

    risultato = {
        "ticker":               state["ticker_corrente"],
        "verdetto":             verdetto,
        "margine_di_sicurezza": round(margine, 1),
        "valore_intrinseco":    round(state.get("valore_intrinseco", 0.0), 2),
        "prezzo_corrente":      round(state.get("prezzo_corrente", 0.0), 2),
        "roe_medio":            m.get("roe_medio_5y", 0.0),
        "debt_equity":          m.get("debt_equity", 0.0),
        "gross_margin":         m.get("gross_margin_medio", 0.0),
        "net_margin":           m.get("net_margin_medio", 0.0),
        "rev_cagr":             m.get("revenue_cagr_pct", 0.0),
        "owner_earnings":       state.get("owner_earnings_attuali", 0.0),
        "analisi_munger":       state.get("analisi_qualitativa_testo", ""),
        "rischio_estremo":      state.get("rischio_estremo_pdf", False),
        "passa_test_qualita":   state.get("passa_test_qualita", False),
        "position_size_eur":    pos,
        # v2.2: nuovi campi
        "price_action":          state.get("price_action", {}),
        "panic_discount":        state.get("panic_discount", False),
        "deterioration_warning": state.get("deterioration_warning", False),
        "news_sentiment":        state.get("news_sentiment", "neutral"),
        "news_summary":          state.get("news_summary", ""),
    }

    risultati = list(state.get("risultati", []))
    risultati.append(risultato)
    logs = list(state.get("log_globale", []))
    dd = state.get("price_action", {}).get("drawdown_dal_max_52w", 0)
    logs.append(f"[VERDICT] {state['ticker_corrente']} → {verdetto} MoS:{margine:.1f}% DD:{dd:.1f}% Alloc:€{pos:.0f}")
    return {**state, "risultati": risultati, "verdetto_corrente": verdetto, "log_globale": logs}


def node_approvato(state):                return _salva(state, "APPROVATO")
def node_approvato_panic_buy(state):      return _salva(state, "APPROVATO_PANIC_BUY")      # v2.2
def node_watchlist(state):                return _salva(state, "WATCHLIST")
def node_bocciato_numerico(state):        return _salva(state, "BOCCIATO_NUMERICO")
def node_bocciato_qualitativo(state):     return _salva(state, "BOCCIATO_QUALITATIVO")
def node_bocciato_value_trap(state):      return _salva(state, "BOCCIATO_VALUE_TRAP")     # v2.2


# ══════════════════════════════════════════════════════════════════════════════
#  ROUTING CONDIZIONALE v2.2
#  Cascata decisionale completa con anti-value-trap e cattura panic-buy
# ══════════════════════════════════════════════════════════════════════════════

def munger_decision(state: AgentState) -> str:
    """
    Cascata Buffett-Munger v2.2:
      1. Rischio estremo Munger (10-K/10-Q)     → BOCCIATO_QUALITATIVO
      2. Deterioration warning + danno strutt   → BOCCIATO_VALUE_TRAP   ← v2.2
      3. Test quantitativi falliti              → BOCCIATO_NUMERICO
      4. Panic discount + panico temporaneo     → APPROVATO_PANIC_BUY   ← v2.2 (top priority tra i buy)
      5. Margine di sicurezza > soglia          → APPROVATO
      6. Default                                → WATCHLIST
    """
    # 1. Veto di Munger su rischi qualitativi estremi
    if state.get("rischio_estremo_pdf"):
        return "bocciato_qualitativo"

    # 2. Value trap detection (v2.2): drawdown grande + segnali strutturali negativi
    if (state.get("deterioration_warning", False)
        and state.get("news_sentiment") == "structural_damage"):
        return "bocciato_value_trap"

    # 3. Test quantitativi Buffett (ROE>15%, D/E<0.5)
    if not state.get("passa_test_qualita"):
        return "bocciato_numerico"

    # 4. PANIC BUY (v2.2): occasione Buffett-style
    #    Aziende solide colpite da panico temporaneo, con margine già positivo
    if (state.get("panic_discount", False)
        and state.get("news_sentiment") in ("temporary_panic", "neutral")
        and state.get("margine_di_sicurezza", 0) > 10.0):
        return "approvato_panic_buy"

    # 5. Approvazione standard: margine di sicurezza ampio
    if state.get("margine_di_sicurezza", 0) > MARGINE_SICUREZZA_STANDARD:
        return "approvato"

    # 6. Watchlist: buon business, prezzo non abbastanza scontato
    return "watchlist"


def check_loop(state: AgentState) -> str:
    return "prossimo_ticker" if state.get("ticker_da_analizzare") else "genera_report"


# ══════════════════════════════════════════════════════════════════════════════
#  NODO FINALE: GENERA REPORT HTML
# ══════════════════════════════════════════════════════════════════════════════

def node_genera_report_html(state: AgentState) -> AgentState:
    """
    Produce un report HTML professionale con:
    - Dashboard: 5 KPI principali
    - Portafoglio suggerito con position sizing
    - Tabella completa ordinata per margine di sicurezza
    - Dettaglio Munger espandibile per ogni titolo
    - Design dark finanziario con citazioni Buffett
    """
    risultati = state.get("risultati", [])
    capitale  = state.get("capitale_totale_eur", 4500.0)
    data_oggi = datetime.datetime.now().strftime("%d %B %Y — %H:%M")

    # v2.2: nuovi verdetti nell'ordine di priorità
    ordine = {
        "APPROVATO_PANIC_BUY":  0,  # top: occasioni Buffett-style
        "APPROVATO":            1,
        "WATCHLIST":            2,
        "BOCCIATO_VALUE_TRAP":  3,
        "BOCCIATO_NUMERICO":    4,
        "BOCCIATO_QUALITATIVO": 5,
    }
    rs = sorted(risultati, key=lambda r: (ordine.get(r["verdetto"], 9), -r["margine_di_sicurezza"]))

    # Aggregazioni per le card riepilogative
    panic_buys      = [r for r in rs if r["verdetto"] == "APPROVATO_PANIC_BUY"]
    approvati_std   = [r for r in rs if r["verdetto"] == "APPROVATO"]
    approvati       = panic_buys + approvati_std
    watchlist       = [r for r in rs if r["verdetto"] == "WATCHLIST"]
    boc_n           = [r for r in rs if r["verdetto"] == "BOCCIATO_NUMERICO"]
    boc_q           = [r for r in rs if r["verdetto"] == "BOCCIATO_QUALITATIVO"]
    value_traps     = [r for r in rs if r["verdetto"] == "BOCCIATO_VALUE_TRAP"]
    tot_alloc       = sum(r["position_size_eur"] for r in approvati)
    liquidita       = capitale - tot_alloc

    def badge(v):
        b = {
            "APPROVATO_PANIC_BUY":  '<span class="badge bp">🔥 PANIC BUY</span>',
            "APPROVATO":            '<span class="badge bg">🟢 COMPRA ORA</span>',
            "WATCHLIST":            '<span class="badge bw">👀 WATCHLIST</span>',
            "BOCCIATO_NUMERICO":    '<span class="badge bn">❌ BOCCIATO</span>',
            "BOCCIATO_QUALITATIVO": '<span class="badge bq">🚫 RISCHIO</span>',
            "BOCCIATO_VALUE_TRAP":  '<span class="badge bt">⚠️ VALUE TRAP</span>',
        }
        return b.get(v, v)

    def mos_color(v):
        if v > 30: return "#22c55e"
        if v > 10: return "#f59e0b"
        if v > 0:  return "#f97316"
        return "#ef4444"

    def dd_color(v):
        # Drawdown: rosso scuro se forte calo, verde se vicino ai massimi
        if v >= 35: return "#ef4444"
        if v >= 20: return "#f59e0b"
        return "#8b949e"

    righe = ""
    for r in rs:
        mc       = mos_color(r["margine_di_sicurezza"])
        al       = f"<strong>€{r['position_size_eur']:.0f}</strong>" if r["position_size_eur"] > 0 else "—"
        munger_esc = r["analisi_munger"].replace("<", "&lt;").replace(">", "&gt;")
        news_esc   = r.get("news_summary", "").replace("<", "&lt;").replace(">", "&gt;")
        pa         = r.get("price_action", {}) or {}
        dd         = pa.get("drawdown_dal_max_52w", 0.0)
        dc         = dd_color(dd)
        perf12     = pa.get("performance_12m", 0.0)

        # Icona sentiment news
        ns = r.get("news_sentiment", "neutral")
        ns_icon = {"temporary_panic": "🔥", "structural_damage": "⚠️", "neutral": "—"}.get(ns, "—")

        righe += f"""
<tr>
  <td class="tc">{r['ticker']}</td>
  <td>{badge(r['verdetto'])}</td>
  <td style="color:{mc};font-weight:700">{r['margine_di_sicurezza']:.1f}%</td>
  <td style="color:{dc};font-weight:600">-{dd:.1f}%</td>
  <td>${r['prezzo_corrente']:.2f}</td>
  <td>${r['valore_intrinseco']:.2f}</td>
  <td>{r['roe_medio']:.1f}%</td>
  <td>{r['debt_equity']:.2f}</td>
  <td>{r['gross_margin']:.1f}%</td>
  <td>{r['rev_cagr']:.1f}%</td>
  <td title="{ns}">{ns_icon}</td>
  <td class="ac">{al}</td>
  <td><button class="db" onclick="tog('{r['ticker']}',this)">▼</button></td>
</tr>
<tr id="d-{r['ticker']}" style="display:none">
  <td colspan="13">
    <div class="dbox">
      <h4>📊 Price Action — {r['ticker']}</h4>
      <pre>Drawdown da 52w high: -{dd:.1f}% | Perf. 12 mesi: {perf12:+.1f}% | Volatilità annua: {pa.get('volatilita_annua', 0):.1f}%
Prezzo oggi: ${pa.get('prezzo_oggi', 0):.2f} | Max 52w: ${pa.get('max_52w', 0):.2f} | Min 52w: ${pa.get('min_52w', 0):.2f}</pre>
      <h4>📰 News Sentiment — Buffett scout (ultimi 90 giorni)</h4>
      <pre>{news_esc[:1200] if news_esc else 'Nessuna analisi news disponibile.'}</pre>
      <h4>📋 Analisi Charlie Munger — 10-K + 10-Q</h4>
      <pre>{munger_esc[:1500]}</pre>
    </div>
  </td>
</tr>"""

    # Cards portafoglio: in cima i panic buy (priorità Buffett)
    cards = ""
    if approvati:
        for r in approvati:
            is_panic = r["verdetto"] == "APPROVATO_PANIC_BUY"
            cls      = "pc panic" if is_panic else "pc"
            icon     = "🔥 " if is_panic else ""
            cards += f"""<div class="{cls}"><div class="pt">{icon}{r['ticker']}</div>
<div class="pa">€{r['position_size_eur']:.0f}</div>
<div class="pm">MoS {r['margine_di_sicurezza']:.1f}%</div></div>"""
        cards += f"""<div class="pc cash"><div class="pt">💵 LIQUIDITÀ</div>
<div class="pa">€{liquidita:.0f}</div><div class="pm">riserva 15%</div></div>"""
    else:
        cards = '<p style="color:#8b949e;font-style:italic;padding:12px">Nessun titolo supera tutti i criteri oggi — <strong>la pazienza è la virtù dell\'investitore.</strong></p>'

    html = f"""<!DOCTYPE html>
<html lang="it">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Team Buffett — {data_oggi}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;700;900&family=Source+Sans+3:wght@300;400;600&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
<style>
*{{margin:0;padding:0;box-sizing:border-box}}
:root{{--bg:#0d1117;--s:#161b22;--s2:#21262d;--br:#30363d;--tx:#e6edf3;--mu:#8b949e;
       --gd:#d4a017;--gl:#f0c040;--gr:#22c55e;--rd:#ef4444;--or:#f97316;--yw:#f59e0b;--bl:#3b82f6}}
body{{background:var(--bg);color:var(--tx);font-family:'Source Sans 3',sans-serif;font-size:15px;line-height:1.6}}

.hd{{background:linear-gradient(135deg,#0d1117,#1a1400,#0d1117);border-bottom:2px solid var(--gd);padding:36px 48px 28px;position:relative}}
.hd::after{{content:'"Rule No.1: Never lose money. Rule No.2: Never forget Rule No.1." — Warren Buffett';
  position:absolute;bottom:10px;right:48px;font-family:'Playfair Display',serif;font-style:italic;
  font-size:11px;color:var(--gd);opacity:0.45}}
.hd h1{{font-family:'Playfair Display',serif;font-size:34px;font-weight:900;color:var(--gl)}}
.hd h1 span{{color:var(--mu);font-weight:400;font-size:18px}}
.sub{{color:var(--mu);font-size:12px;margin-top:5px;font-family:'JetBrains Mono',monospace}}

.wrap{{max-width:1440px;margin:0 auto;padding:28px 48px}}

.sb{{display:grid;grid-template-columns:repeat(6,1fr);gap:12px;margin:24px 0 28px}}
.sc{{background:var(--s);border:1px solid var(--br);border-radius:10px;padding:18px;text-align:center}}
.sc .n{{font-family:'Playfair Display',serif;font-size:30px;font-weight:700;line-height:1}}
.sc .l{{font-size:11px;color:var(--mu);margin-top:4px;text-transform:uppercase;letter-spacing:1px}}
.sg .n{{color:var(--gr)}} .sy .n{{color:var(--yw)}} .sr .n{{color:var(--rd)}} .sd .n{{color:var(--gl)}} .sb2 .n{{color:var(--bl)}}

.stit{{font-family:'Playfair Display',serif;font-size:19px;color:var(--gl);margin-bottom:14px;
  padding-bottom:8px;border-bottom:1px solid var(--br)}}

.pg{{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:36px}}
.pc{{background:var(--s);border:1px solid var(--gr);border-radius:10px;padding:14px 20px;text-align:center;min-width:120px}}
.pc.cash{{border-color:var(--gd)}}
.pt{{font-family:'JetBrains Mono',monospace;font-size:14px;font-weight:600}}
.pa{{font-family:'Playfair Display',serif;font-size:22px;font-weight:700;color:var(--gr)}}
.pc.cash .pa{{color:var(--gl)}}
.pm{{font-size:11px;color:var(--mu);margin-top:2px}}

.tw{{overflow-x:auto}}
table{{width:100%;border-collapse:collapse;font-size:13px;margin-bottom:40px}}
thead tr{{background:var(--s2);border-bottom:2px solid var(--gd)}}
th{{padding:10px 12px;text-align:left;font-family:'JetBrains Mono',monospace;font-size:10px;
    text-transform:uppercase;letter-spacing:1px;color:var(--mu);white-space:nowrap}}
td{{padding:10px 12px;border-bottom:1px solid var(--br);vertical-align:middle}}
tr:hover td{{background:rgba(255,255,255,0.02)}}
.tc{{font-family:'JetBrains Mono',monospace;font-weight:600;font-size:14px}}
.ac{{font-family:'JetBrains Mono',monospace;color:var(--gr)}}

.badge{{padding:3px 9px;border-radius:20px;font-size:11px;font-weight:600;white-space:nowrap}}
.bg{{background:rgba(34,197,94,.15);color:var(--gr);border:1px solid var(--gr)}}
.bw{{background:rgba(245,158,11,.15);color:var(--yw);border:1px solid var(--yw)}}
.bn{{background:rgba(239,68,68,.10);color:var(--rd);border:1px solid var(--rd)}}
.bq{{background:rgba(239,68,68,.20);color:#ff6b6b;border:1px solid #ff6b6b}}
/* v2.2: nuovi badges per panic buy e value trap */
.bp{{background:linear-gradient(135deg,rgba(212,160,23,.25),rgba(34,197,94,.20));color:var(--gl);border:1px solid var(--gd);
     box-shadow:0 0 8px rgba(212,160,23,.25)}}
.bt{{background:rgba(249,115,22,.18);color:var(--or);border:1px solid var(--or)}}

/* v2.2: stats card panic e value-trap */
.sp .n{{color:var(--gd)}}
.st .n{{color:var(--or)}}

/* v2.2: card portafoglio panic (highlight dorato) */
.pc.panic{{border-color:var(--gd);background:linear-gradient(180deg,rgba(212,160,23,.10),var(--s));
          box-shadow:0 0 12px rgba(212,160,23,.20)}}
.pc.panic .pa{{color:var(--gl)}}

.db{{background:var(--s2);border:1px solid var(--br);color:var(--mu);padding:3px 10px;
  border-radius:6px;cursor:pointer;font-size:11px;transition:all .2s}}
.db:hover{{border-color:var(--gd);color:var(--gd)}}
.dbox{{background:var(--s2);border-left:3px solid var(--gd);padding:18px 22px;margin:2px 0}}
.dbox h4{{font-family:'Playfair Display',serif;color:var(--gl);margin-bottom:10px;font-size:15px}}
.dbox pre{{font-family:'JetBrains Mono',monospace;font-size:11px;color:var(--mu);white-space:pre-wrap;line-height:1.7}}

.qb{{background:var(--s);border-left:4px solid var(--gd);padding:14px 22px;margin:0 0 28px;border-radius:0 8px 8px 0}}
.qb p{{font-family:'Playfair Display',serif;font-style:italic;color:var(--gl);font-size:15px}}
.qb cite{{font-size:11px;color:var(--mu);display:block;margin-top:4px}}

.ft{{border-top:1px solid var(--br);padding:20px 48px;color:var(--mu);font-size:11px;display:flex;justify-content:space-between;align-items:center}}
</style>
</head>
<body>

<div class="hd">
  <h1>Team Buffett <span>— Value Investor Bot</span></h1>
  <div class="sub">📅 {data_oggi} &nbsp;|&nbsp; 🔍 {len(risultati)} titoli analizzati &nbsp;|&nbsp; 💼 Capitale disponibile: €{capitale:,.0f}</div>
</div>

<div class="wrap">

<div class="sb">
  <div class="sc sp"><div class="n">{len(panic_buys)}</div><div class="l">🔥 Panic Buy</div></div>
  <div class="sc sg"><div class="n">{len(approvati_std)}</div><div class="l">🟢 Compra Ora</div></div>
  <div class="sc sy"><div class="n">{len(watchlist)}</div><div class="l">👀 Watchlist</div></div>
  <div class="sc st"><div class="n">{len(value_traps)}</div><div class="l">⚠️ Value Trap</div></div>
  <div class="sc sr"><div class="n">{len(boc_n) + len(boc_q)}</div><div class="l">❌ Bocciati</div></div>
  <div class="sc sd"><div class="n">€{tot_alloc:,.0f}</div><div class="l">💰 Allocato</div></div>
</div>

<div class="qb">
  <p>"Price is what you pay. Value is what you get. Be fearful when others are greedy,<br>and greedy when others are fearful."</p>
  <cite>— Warren Buffett</cite>
</div>

<h2 class="stit">💼 Portafoglio Suggerito (€{capitale:,.0f} disponibili)</h2>
<div class="pg">{cards}</div>

<h2 class="stit">📊 Analisi Completa — {len(risultati)} Titoli</h2>
<div class="tw">
<table>
<thead><tr>
  <th>Ticker</th><th>Verdetto</th><th>MoS %</th><th>Drawdown 12M</th>
  <th>Prezzo</th><th>Val.Intr.</th>
  <th>ROE 5y</th><th>D/E</th><th>Gross M.</th><th>Rev.CAGR</th>
  <th>News</th><th>Alloc.€</th><th></th>
</tr></thead>
<tbody>{righe}</tbody>
</table>
</div>

</div>

<div class="ft">
  <span>⚠️ Solo a scopo informativo/educativo. Non costituisce consulenza finanziaria. Verifica sempre i dati prima di investire.</span>
  <span>Team Buffett Bot v2.2 — LangGraph + Gemini 1.5 Pro</span>
</div>

<script>
function tog(id, btn) {{
  var r = document.getElementById('d-'+id);
  if(r.style.display==='none'){{ r.style.display='table-row'; btn.textContent='▲'; }}
  else {{ r.style.display='none'; btn.textContent='▼'; }}
}}
</script>
</body></html>"""

    out_dir = Path("reports")
    out_dir.mkdir(exist_ok=True)
    ts   = datetime.datetime.now().strftime("%Y%m%d_%H%M")
    path = str(out_dir / f"buffett_report_{ts}.html")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(html)

    logs = list(state.get("log_globale", []))
    logs.append(f"[REPORT] ✅ Salvato: {path}")
    logs.append(f"[REPORT] APPROVATI: {[r['ticker'] for r in approvati]}")
    logs.append(f"[REPORT] Allocato: €{tot_alloc:.0f} / €{capitale:.0f} | Liquidità: €{liquidita:.0f}")

    try:
        webbrowser.open(f"file://{Path(path).resolve()}")
        logs.append("[REPORT] Aperto nel browser.")
    except Exception:
        pass

    return {**state, "report_path": path, "log_globale": logs}


# ══════════════════════════════════════════════════════════════════════════════
#  COSTRUZIONE GRAFO
# ══════════════════════════════════════════════════════════════════════════════

def build_graph() -> StateGraph:
    b = StateGraph(AgentState)

    # ── Nodi ────────────────────────────────────────────────────────────
    b.add_node("screener",                node_screener)
    b.add_node("prossimo_ticker",         node_prossimo_ticker)
    b.add_node("estrai_dati",             node_estrai_dati)
    b.add_node("leggi_10k",               node_leggi_report_10k)
    b.add_node("news_sentiment",          node_news_sentiment)       # v2.2
    b.add_node("check_price_action",      node_check_price_action)   # v2.2
    b.add_node("calcola_valore",          node_calcola_valore_buffett)
    b.add_node("approvato",               node_approvato)
    b.add_node("approvato_panic_buy",     node_approvato_panic_buy)  # v2.2
    b.add_node("watchlist",               node_watchlist)
    b.add_node("bocciato_numerico",       node_bocciato_numerico)
    b.add_node("bocciato_qualitativo",    node_bocciato_qualitativo)
    b.add_node("bocciato_value_trap",     node_bocciato_value_trap)  # v2.2
    b.add_node("genera_report",           node_genera_report_html)

    # ── Entry point ─────────────────────────────────────────────────────
    b.set_entry_point("screener")

    # ── Screener → loop o report ────────────────────────────────────────
    b.add_conditional_edges("screener", check_loop, {
        "prossimo_ticker": "prossimo_ticker",
        "genera_report":   "genera_report",
    })

    # ── Pipeline lineare di analisi ─────────────────────────────────────
    # v2.2: news_sentiment PRIMA di check_price_action, perché quest'ultimo
    # usa il sentiment per decidere panic_discount vs deterioration_warning
    b.add_edge("prossimo_ticker",   "estrai_dati")
    b.add_edge("estrai_dati",       "leggi_10k")
    b.add_edge("leggi_10k",         "news_sentiment")        # v2.2
    b.add_edge("news_sentiment",    "check_price_action")    # v2.2
    b.add_edge("check_price_action","calcola_valore")        # v2.2
    # OLD: leggi_10k → calcola_valore (sostituito)

    # ── Decisione finale (cascata Buffett-Munger v2.2) ──────────────────
    b.add_conditional_edges("calcola_valore", munger_decision, {
        "bocciato_qualitativo": "bocciato_qualitativo",
        "bocciato_value_trap":  "bocciato_value_trap",
        "bocciato_numerico":    "bocciato_numerico",
        "approvato_panic_buy":  "approvato_panic_buy",
        "approvato":            "approvato",
        "watchlist":            "watchlist",
    })

    # ── Dopo ogni verdetto → prossimo ticker o report ───────────────────
    for nv in ["approvato", "approvato_panic_buy", "watchlist",
               "bocciato_numerico", "bocciato_qualitativo", "bocciato_value_trap"]:
        b.add_conditional_edges(nv, check_loop, {
            "prossimo_ticker": "prossimo_ticker",
            "genera_report":   "genera_report",
        })
    b.add_edge("genera_report", END)

    return b.compile()


app = build_graph()


# ══════════════════════════════════════════════════════════════════════════════
#  ENTRY POINT LOCALE
# ══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("\n" + "═"*58)
    print("  VALUE INVESTOR BOT — TEAM BUFFETT  v2.6")
    print("  Warren Buffett + Charlie Munger Philosophy")
    print("="*58)
    if TICKER_MANUALI:
        mode = "IBRIDA (manuali + screener)" if INCLUDI_SCREENER else "SOLO MANUALI"
        print(f"  Modalità ticker: {mode}")
        print(f"  Ticker manuali:  {TICKER_MANUALI}")
    else:
        print("  Modalità ticker: SOLO SCREENER (auto-pilota)")
    print(f"  Limite screener: {UNIVERSO_FINALE_MAX_TICKET_NUMBER} ticker/run")
    print("  Difese: Anti Value-Trap + Cattura Panic-Buy")
    print(f"  Drawdown soglia panic-buy: {PANIC_DRAWDOWN_THRESHOLD}%")
    print(f"  Analisi profonda: Claude {MODEL_DEEP_ANALYSIS}")
    print(f"  Compiti leggeri:  {MODEL_LIGHT_TASKS}")
    print(f"  Modalità Embedding: {'LOCALE (HuggingFace, Veloce)' if MODALITA_EMBEDDING == 0 else 'CLOUD (Gemini, Lento)'}")
    print(f"  Segnale 1 (13-F): SEC EDGAR (gratuito)")
    print(f"  Resto API:        FMP stable")
    print(f"  Capitale: €4,500")
    print("  Avvio...\n")

    initial: AgentState = {
        "fmp_api_key":              os.environ.get("FMP_API_KEY", ""),
        "capitale_totale_eur":      4500.0,
        "ticker_da_analizzare":     [],
        "ticker_corrente":          "",
        "risultati":                [],
        "metriche_qualita":         {},
        "owner_earnings_attuali":   0.0,
        "valore_intrinseco":        0.0,
        "margine_di_sicurezza":     -100.0,
        "passa_test_qualita":       False,
        "analisi_qualitativa_testo": "",
        "rischio_estremo_pdf":      False,
        "verdetto_corrente":        "",
        "prezzo_corrente":          0.0,
        # v2.2 — nuovi campi
        "price_action":             {},
        "panic_discount":           False,
        "deterioration_warning":    False,
        "news_sentiment":           "neutral",
        "news_summary":             "",
        "log_globale":              [],
        "report_path":              "",
    }

    for step in app.stream(initial, {"recursion_limit": 1000}):
        nodo  = list(step.keys())[0]
        stato = step[nodo]
        logs  = stato.get("log_globale", [])
        if logs:
            print(logs[-1])

    print("\n✅ Fatto. Report HTML aperto nel browser.")