-- V032: seed del sentinel stock "ALL" per la cache dello screener (EP-012).
--
-- UniverseScreenerService cacha il risultato di FMP company-screener in
-- fmp_financial_snapshot usando lo PSEUDO-ticker "ALL" come chiave (non è un
-- titolo reale). Ma fmp_financial_snapshot.ticker ha una FK verso stocks(ticker):
-- al primo run del batch (cache miss) l'INSERT falliva con
--   ERROR: insert ... violates FK "fmp_financial_snapshot_ticker_fkey"
--   Detail: Key (ticker)=(ALL) is not present in table "stocks".
--
-- Seediamo quindi una riga sentinel in stocks. La tabella stocks è un catalogo
-- interno lazy (NON enumerato da endpoint/UI), quindi "ALL" non appare in alcuna
-- lista utente. Idempotente: ON CONFLICT DO NOTHING.
-- [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/universe/UniverseScreenerService.kt §screen]

INSERT INTO stocks (ticker, company_name)
VALUES ('ALL', '(universe screener cache sentinel — not a tradable ticker)')
ON CONFLICT (ticker) DO NOTHING;
