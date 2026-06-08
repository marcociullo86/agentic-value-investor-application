---
rule_id: kotlin.spring.persistence.query_contract_annotation
version: v1
tier: emergent
title: "Query @Query methods that add corpus/tenant filter must document the scope change"
applies_to:
  language: kotlin
  framework: spring
  context: [persistence, repository]
severity_default: low
auto_fixable: false
status: candidate
metadata:
  created_at: "2026-06-08"
  author: "agent:cqrl-tsk337-iter1"
  origin_tsk: TSK-337
---
# Regola (candidate)

## Rationale
Quando una `@Query` nativa su una tabella multi-tenant / multi-corpus riceve un nuovo
predicato di scope (es. `corpus_kind = 'FILING'`), il nome del metodo o il suo KDoc
deve riflettere esplicitamente il filtro. Un metodo chiamato `findSimilar` che ora
ritorna solo chunk `FILING` appare identico prima e dopo la modifica ai chiamanti
futuri, che potrebbero aspettarsi risultati multi-corpus.

## Detection hints
`@Query` native che aggiungono un filtro discriminante (`corpus_kind`, `tenant_id`,
`partition_key`, ecc.) su una tabella che prima non li aveva, senza aggiornare il
nome del metodo né aggiungere un commento che dichiari il filtro implicito.
