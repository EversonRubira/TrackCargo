# Status — qa-backend-export-tracking

## Última atualização
16/set/2026 — Fase 0 concluída

## Onde paramos
Fase 0 (Scaffold) concluída e validada:
- Spring Boot 4.1.1, Java 21 (target de compilação), rodando em JDK 25 no Codespace
- docker-compose.yml com Postgres 16
- `mvn spring-boot:run` sobe sem erro, conecta no Postgres, Flyway cria
  `flyway_schema_history` vazio (nenhuma migration ainda — esperado)
- Branch `feat/fase0-scaffold`

## Próximo passo
Fase 1 do PLAN.md: Entity + Migration
- `V1__create_pedido_schema.sql` (pedido, checklist_documento, pedido_transicao)
- Entidades JPA + enum `PedidoEstado`
- Pendência da Spec a resolver na implementação do enum: o mapa de
  transições manuais não deve incluir `DOCUMENTACAO_ENVIADA` nem
  `DOCUMENTACAO_ACEITA` como destino alcançável via `/transicionar` —
  esses dois só devem ser atingidos pelo efeito colateral do checklist

## Decisões de stack confirmadas
- Spring Boot 4.1.x (não 3.x — linha 3.x é EOL desde 30/jun/2026)
- groupId com.eversonrubira.exporttracking, artifactId export-tracking
