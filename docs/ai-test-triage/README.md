# Pontis - AI Test Triage V1

Pacote SDD (spec, arquitetura, ADRs, tarefas) e a documentação da implementação da V1. A V1 está implementada em `src/main/java/.../triage`, com testes em `src/test`.

## Files
- `spec.md` - approved behavior and acceptance criteria.
- `architecture.md` - approved technical architecture.
- `adr/` - accepted architectural decisions.
- `tasks.md` - ordered implementation tasks with tests and Definition of Done.
- `CLAUDE-HANDOFF.md` - implementation instructions and guardrails for Claude.
- `IMPLEMENTATION-NOTES.md` - decisões de implementação e limitações do ambiente.
- `VERIFICATION-PACK.md` - critérios de aceitação mapeados para a evidência, limitações conhecidas da V1.
- `evals/` - dataset de 10 casos congelado (`DATASET-V1.md`) e baseline real (`baseline-v1/BASELINE-REPORT.md`).
- `evidence/` - evidência da execução controlada no CI.

## How to use
Para retomar o trabalho, começar por `CLAUDE-HANDOFF.md` e seguir a ordem de fontes de verdade definida lá. Para perceber o estado da V1, ler `VERIFICATION-PACK.md` e `evals/baseline-v1/BASELINE-REPORT.md`.

## Estado da V1
Baseline real: automático estrito 7/10 (alvo 8/10 não atingido), revisão humana 10/10, segurança PASS. Limitações conhecidas no `VERIFICATION-PACK.md`. Melhorias (RAG, clustering, scorer, prompt, remediação) ficam para uma V2 separada.

## Notas para este repositório (acrescentadas na implementação)
- Nome: o produto chama-se Pontis nestes documentos. O código, o `artifactId` (`export-tracking`), o pacote Java e o repositório (`TrackCargo`) mantêm os nomes antigos; a renomeação geral é uma tarefa à parte.
- Estes ADRs são os do AI Test Triage e estão em `adr/` desta pasta. Não confundir com `docs/adr/ADR-002-monolito-modular.md`, que é outro ADR (do projeto).
- Decisões de implementação aprovadas e limitações do ambiente: ver `IMPLEMENTATION-NOTES.md`.
- Configuração: `triage.properties` (documentada linha a linha) e `doc-map.properties`.
