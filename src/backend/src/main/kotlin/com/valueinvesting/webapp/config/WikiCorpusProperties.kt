package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configurazione del corpus RAG WIKI (TSK-337 / US-103, ADR-030 §2).
 *
 * [corpusPath] è la directory radice (a runtime) sotto cui vivono le pagine
 * markdown wiki da indicizzare. Il [WikiCorpusIndexer] esplora ricorsivamente
 * le sottocartelle dichiarate in [subdirs] (default `concepts`, `syntheses`,
 * coerenti con ADR-030 §2 "Scope indicizzazione") e seleziona solo le pagine il
 * cui frontmatter ha `domain ∈` [domains].
 *
 * GAP NOTO (be-wiki-runtime-corpus-mount, wiki/gaps.md 2026-06-08): il packaging
 * dell'immagine (`src/docker/Dockerfile`) NON copia `wiki/` nel container e il
 * `docker-compose.yml` non monta la cartella; non esiste un pattern pre-esistente
 * che legga `wiki/` a runtime. Il default `/app/wiki` presuppone un mount/COPY a
 * carico di infra (vedi gap). In dev la property può puntare al path repo via
 * env `RAG_WIKI_CORPUS_PATH`.
 *
 * [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §2]
 */
@ConfigurationProperties(prefix = "rag.wiki")
data class WikiCorpusProperties(
    // Directory radice del corpus wiki a runtime. Default = mount infra previsto.
    val corpusPath: String = "/app/wiki",
    // Sottocartelle (relative a corpusPath) da esplorare. ADR-030 §2.
    val subdirs: List<String> = listOf("concepts", "syntheses"),
    // Domini ammessi (frontmatter `domain:`). Solo queste pagine vengono indicizzate.
    val domains: List<String> = listOf("value-investing", "technical-analysis-trading"),
)
