package com.valueinvesting.webapp.service

// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-105.md §5,6]
data class MungerInversionReport(
    val ticker: String,
    val livelloRischio: LivelloRischio,
    val rischiPrincipali: List<InversionRisk>,
    val puntiDiForza: List<InversionStrength>,
    val segnaliRecenti10Q: List<InversionSignal>,
    val filingComboHash: String,
    val llmCallsCount: Int,
)

data class InversionRisk(
    val testo: String,
    val chunkIndex: Int,
)

data class InversionStrength(
    val testo: String,
    val chunkIndex: Int,
)

data class InversionSignal(
    val testo: String,
    val chunkIndex: Int,
)
