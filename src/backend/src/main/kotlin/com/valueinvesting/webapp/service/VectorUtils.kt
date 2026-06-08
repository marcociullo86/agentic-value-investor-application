package com.valueinvesting.webapp.service

/**
 * Serializza un embedding nel literal testuale pgvector (`[v0,v1,...]`) atteso
 * dai cast `cast(:embedding AS vector)` delle query native su filing_chunks.
 *
 * Estratto come util top-level condiviso (TSK-337 F2): la stessa logica era
 * duplicata in [FilingRagService] e [WikiCorpusIndexer]; un'eventuale divergenza
 * silenziosa del formato produrrebbe vettori malformati in uno dei due corpus.
 */
fun vectorToString(vector: FloatArray): String =
    vector.joinToString(",", prefix = "[", postfix = "]")
