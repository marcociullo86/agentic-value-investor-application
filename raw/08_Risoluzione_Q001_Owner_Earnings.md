# Risoluzione Quesito Tecnico Q_001: Formula puntuale degli Owner Earnings per il DCF

**Contesto:** Il documento FSD iniziale cita "Free Cash Flow o Owner Earnings" senza specificare la composizione esatta per il calcolo del DCF (Discounted Cash Flow). Poiché le API di Financial Modeling Prep (FMP) non espongono direttamente la "Maintenance CapEx", è necessario definire un metodo di calcolo algoritmico basato sui principi di Warren Buffett.

## 1. La Definizione Ufficiale di Warren Buffett
*(Dalla Lettera agli azionisti Berkshire Hathaway del 1986)*

`Owner Earnings = Net Income + D&A (Depreciation & Amortization) +/- Altre voci non monetarie (Non-Cash Charges) - Maintenance CapEx`

*Nota: A differenza del Free Cash Flow standard, Buffett storicamente non sottrae la variazione del Capitale Circolante Netto se il business non richiede iniezioni continue di liquidità per mantenere i volumi correnti. Per le aziende ad alta intensità di capitale, tuttavia, va sottratta l'aggiunta al capitale circolante.*

## 2. Risoluzione del Blocco (API FMP e Maintenance CapEx)
Poiché nessuna API finanziaria separa nativamente la *Total CapEx* in *Maintenance CapEx* (mantenimento) e *Growth CapEx* (crescita), il "Value Investing Rule Engine" deve implementare un algoritmo di stima. Ecco i tre metodi da implementare:

### Metodo 1: Il Modello di Bruce Greenwald (Raccomandato per il Rule Engine)
Questo è il metodo accademico più solido per estrapolare la CapEx di mantenimento usando i dati standard di bilancio.
1. Calcola il rapporto storico tra immobili/impianti e vendite: `PPE_Ratio = Gross Property Plant & Equipment / Revenue`
2. Calcola la spesa per la crescita: `Growth CapEx = PPE_Ratio * (Revenue_Anno_Corrente - Revenue_Anno_Precedente)`
3. Deriva il mantenimento: `Maintenance CapEx = Total CapEx - Growth CapEx`
*(Regola di fallback: Se le vendite diminuiscono, assumi Growth CapEx = 0 e Maintenance CapEx = Total CapEx).*

### Metodo 2: Proxy Conservativa dell'Ammortamento (Semplificato)
Spesso la spesa necessaria per mantenere gli asset equivale al loro deprezzamento contabile.
`Maintenance CapEx ≈ Depreciation & Amortization (D&A)`
L'Owner Earnings diventa quindi quasi uguale all'Utile Netto (Net Income). Funziona bene per aziende mature con crescita stabile, ma sottostima il fabbisogno di capitale in scenari inflattivi.

### Metodo 3: L'Approccio "Peggior Scenario" di Buffett
Se un'azienda non dimostra di generare rendimenti superiori (ROIC elevato) sui capitali reinvestiti, Buffett considera *tutta* la spesa in conto capitale come spesa di mantenimento.
`Owner Earnings = Free Cash Flow tradizionale (Operating Cash Flow - Total CapEx)`

## 3. Implementazione Pratica (Aggiornamento US-012)
Per sbloccare il requisito (US-012), il Rule Engine (sviluppato in Kotlin) dovrà essere configurato per utilizzare il **Metodo 1 (Greenwald)** come calcolo primario degli Owner Earnings per il modello DCF.
Verrà inoltre aggiunto un flag nel database (sovrascrivibile dall'utente/analista nella UI) per forzare il **Metodo 3** sui settori ad altissima intensità di capitale (es. Utilities, Telecomunicazioni) dove la distinzione tra crescita e mantenimento è sfocata e il rischio di sovrastimare i flussi di cassa è alto.
