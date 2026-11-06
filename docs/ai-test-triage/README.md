# Pontis - AI Test Triage V1 SDD Package

This package is the implementation handoff for Claude.

## Files
- `spec.md` - approved behavior and acceptance criteria.
- `architecture.md` - approved technical architecture.
- `adr/` - accepted architectural decisions.
- `tasks.md` - ordered implementation tasks with tests and Definition of Done.
- `CLAUDE-HANDOFF.md` - implementation instructions and guardrails for Claude.

## How to use
Place this directory in the Pontis repository documentation area (or provide the whole directory to Claude). Ask Claude to start with `CLAUDE-HANDOFF.md` and follow the source-of-truth order defined there.

The package intentionally contains no implementation code.

## Notas para este repositório (acrescentadas na implementação)
- Nome: o produto chama-se Pontis nestes documentos. O código, o `artifactId` (`export-tracking`), o pacote Java e o repositório (`TrackCargo`) mantêm os nomes antigos; a renomeação geral é uma tarefa à parte.
- Estes ADRs são os do AI Test Triage e estão em `adr/` desta pasta. Não confundir com `docs/adr/ADR-002-monolito-modular.md`, que é outro ADR (do projeto).
- Decisões de implementação aprovadas e limitações do ambiente: ver `IMPLEMENTATION-NOTES.md`.
- Configuração: `triage.properties` (documentada linha a linha) e `doc-map.properties`.
