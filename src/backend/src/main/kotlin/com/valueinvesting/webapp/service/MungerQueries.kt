package com.valueinvesting.webapp.service

// [^src: wiki/concepts/munger-inversion-rag.md §"Le 10 Query Munger"]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-105.md §3]
object MungerQueries {

    val ALL: List<String> = listOf(
        "Quali sono i tre rischi più gravi che potrebbero portare al fallimento di questa azienda nei prossimi 5 anni?",
        "Quali fattori esterni (macro, regolatori, competitivi) potrebbero erodere il vantaggio competitivo?",
        "Quali segnali di deterioramento operativo emergono dal 10-Q più recente rispetto al 10-K?",
        "Come potrebbe il management distruggere valore attraverso decisioni di allocazione del capitale?",
        "Quali rischi di concentrazione (clienti, fornitori, geografie) non sono evidenti dai numeri aggregati?",
        "Come potrebbe la disruption tecnologica rendere il business model obsoleto?",
        "Quali passività contingenti o fuori bilancio potrebbero materializzarsi?",
        "Cosa deve andare bene perché il business mantenga la sua attuale valorizzazione?",
        "Quali segnali di deterioramento della qualità degli utili (accruals, one-time items) sono presenti?",
        "Cosa dice la relazione sui rischi (Item 1A) che il management non enfatizza nelle call trimestrali?",
    )
}
