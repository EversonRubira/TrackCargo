# Status — qa-backend-export-tracking

## Última atualização
16/set/2026 — Fase 1 concluída

## Onde paramos
Fase 1 (Entity + Migration) concluída e validada:
- V1__create_pedido_schema.sql (pedido, checklist_documento, pedido_transicao)
- Entidades JPA: Pedido, ChecklistDocumento, PedidoTransicao
- Enum PedidoEstado com mapa de transições manuais — resolve a pendência
  da Spec: DOCUMENTACAO_ENVIADA e DOCUMENTACAO_ACEITA não são destino
  alcançável via transição manual, só pelo checklist (Fase 3)
- mvn spring-boot:run: Flyway aplica V1, Hibernate valida sem erro
- Branch feat/fase1-entity-migration (ou a que você usar)

## Próximo passo
Fase 2 do PLAN.md: Repository
- PedidoRepository, ChecklistDocumentoRepository, PedidoTransicaoRepository
- Query customizada: buscar pedido por numero_invoice
- Confirmação: teste @DataJpaTest cobrindo o find por invoice e a
  constraint de unicidade

## Decisões de stack confirmadas
- Spring Boot 4.1.x (não 3.x — linha 3.x é EOL desde 30/jun/2026)
- groupId com.eversonrubira.exporttracking, artifactId export-tracking
