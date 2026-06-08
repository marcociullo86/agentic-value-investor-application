---
rule_id: kotlin.spring.design.no_duplicate_utility_fn
version: v1
tier: emergent
title: "Utility functions shared by multiple services must be extracted"
applies_to:
  language: kotlin
  framework: spring
  context: [service]
severity_default: medium
auto_fixable: false
status: candidate
metadata:
  created_at: "2026-06-08"
  author: "agent:cqrl-tsk337-iter1"
  origin_tsk: TSK-337
---
# Regola (candidate)

## Rationale
Quando una funzione di utilità (es. conversione vettore FloatArray→String per pgvector)
viene duplicata in due o più `@Service`, qualsiasi modifica al formato (es. precisione,
delimitatore) deve essere applicata in N punti con rischio di divergenza silente.
La funzione deve vivere in un unico punto condiviso: companion object nel repository,
estensione top-level nel package service, o oggetto singleton dedicato.

## Detection hints
Funzioni `private fun` con corpo identico o semanticamente equivalente presenti in
due o più classi `@Service` / `@Component` nello stesso package o sotto-package.
