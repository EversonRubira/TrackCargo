# Status — qa-backend-export-tracking

## Última atualização
16/set/2026 — Fase 2 concluída

## Onde paramos
Fase 2 (Repository) concluída e validada:
- PedidoRepository, ChecklistDocumentoRepository, PedidoTransicaoRepository
  (Spring Data JPA)
- Query customizada: PedidoRepository.findByNumeroInvoice
- PedidoRepositoryTest (@DataJpaTest + @AutoConfigureTestDatabase(Replace.NONE),
  roda contra o Postgres do docker-compose): cobre o find por invoice e a
  constraint de unicidade (uk_pedido_numero_invoice)
- Nota de stack: Spring Boot 4.1.x modularizou as anotações de teste por
  tecnologia. DataJpaTest e AutoConfigureTestDatabase vêm de módulos
  separados (spring-boot-data-jpa-test / spring-boot-jdbc-test), puxados
  pela dependência spring-boot-starter-data-jpa-test — sem ela o build
  falha com "package does not exist" mesmo com os imports corretos
- Branch feat/fase1-entity-migration (ou a que você usar)

## Próximo passo
Fase 3 do PLAN.md: Service + regras de domínio
- PedidoService: criar pedido (+ checklist zerado + transição inicial),
  transicionar estado (validando + gravando histórico)
- ChecklistService: marcar enviado/aceito, disparar transição automática
  quando aplicável
- Exceções de domínio: TransicaoInvalidaException,
  PedidoNaoEncontradoException, DocumentacaoIncompletaException
- Confirmação: suíte de testes unitários cobrindo a tabela de correlação
  da Spec (linhas "Unitário")

## Decisões de stack confirmadas
- Spring Boot 4.1.x (não 3.x — linha 3.x é EOL desde 30/jun/2026)
- groupId com.eversonrubira.exporttracking, artifactId export-tracking

## Backlog v2 (fora de escopo das fases atuais)
- Frontend completo do produto — React + TypeScript, moderno, voltado a
  comércio exterior, com Playwright cobrindo E2E. Decisão tomada em
  16/set/2026: v1 deixa de ser só "sistema headless" e passa a mirar
  produto completo com intenção de escalar. Implementação fica pra
  quando o backend do v1 (Fases 0-6) estiver fechado — reabrir o PRD
  antes de começar, pra "portal do cliente" virar critério de aceitação
  real, não só esta nota.
- Rastreio do cliente: link único e não-adivinhável por pedido (UUID do
  pedido, não uma entidade Cliente nova), sem login — mesmo padrão de
  Correios/DHL/Maersk. Cliente só vira entidade própria se aparecer
  fricção real (ex: dashboard agregado por comprador), o que se junta
  ao item de Multiempresa abaixo se/quando acontecer.
- Autenticação/autorização multiusuário (papel de gerente consultando
  embarques)
- Multiempresa/multiusuário (cada exportador com seus próprios pedidos
  e clientes)
- Integração de câmbio/moeda
- Notificações automáticas ao cliente por etapa
