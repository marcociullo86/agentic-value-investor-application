---
type: episodic
created: 2026-05-22 22:50
tags: [sprint5, wave1, ci-green, r1.1]
---
# Sprint 5 Wave 1 — CI verde e closeout kanban

## Stato osservato
- L5: commit `1e15c20` (feat Wave 1) + 6 fix CI fino a `1882767`
- CI `master`: `ci` + `contract-check` success (run 26310494781)
- Sprint 5: 14/21 TSK `done` (Wave 1); 7 TSK `todo` (Wave 2 deploy + TSK-068 human)
- EP-007: `done` (US-021…025)
- EP-009: `in_progress` (US-030 done; US-029 wiki pending)
- EP-008: `defined` (US-026…028 todo; TSK-064 purge done)

## Decisione presa
Allineamento amministrativo post-Wave 1: kanban sprint.md, US/EP status, TSK-060 verify doc-only, `design_&_architecture/overview.md` tabella implementazione R1.1. Gap wiki debito R1.0 (`be-problemdetail-flatten`, `fe-swr-peer-r19`, `fe-static-export-tickers`) implementati in L5 — chiusura gap solo wiki-keeper.

## Prossimo step
- `/dev TSK-061` (docker-compose.prod + nginx)
- Human `TSK-068` (wiki-keeper ingest FMP)
- Dopo deploy staging: TSK-066 smoke cutover

## Riferimenti
- wiki/log.md §2026-05-22 22:50
- management/kanban/sprint.md §Sprint 5
