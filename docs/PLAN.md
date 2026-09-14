# Plan — F01: Ciclo de vida do pedido de exportação

> Ritmo bloco a bloco: cada bloco abaixo é confirmado por você antes de
> avançar pro próximo. `docs/STATUS.md` é atualizado a cada marco
> concluído, registrando o ponto exato de retomada.

## Fase 0 — Scaffold

- Scaffold do projeto via `start.spring.io` (ou CLI equivalente),
  dependências: Web, Data JPA, PostgreSQL Driver, Flyway, Validation.
- `docker-compose.yml` com serviço Postgres.
- `application.yml` com datasource apontando pro container.
- Confirmação: `mvn spring-boot:run` sobe sem erro, aplicação conecta
  no Postgres.

## Fase 1 — Bloco: Entity + Migration

- Migration Flyway `V1__create_pedido_schema.sql` (as 3 tabelas).
- Entidades JPA: `Pedido`, `ChecklistDocumento`, `PedidoTransicao`.
- Enum `PedidoEstado` com mapa de transições válidas +
  `podeTransicionarPara()`.
- Confirmação: aplicação sobe, Flyway aplica a migration, tabelas
  existem no Postgres (verificação manual ou teste de contexto).

## Fase 2 — Bloco: Repository

- `PedidoRepository`, `ChecklistDocumentoRepository`,
  `PedidoTransicaoRepository` (Spring Data JPA).
- Query customizada: buscar pedido por `numero_invoice`.
- Confirmação: teste de repositório (`@DataJpaTest`) validando o
  find por invoice e a constraint de unicidade.

## Fase 3 — Bloco: Service + regras de domínio

- `PedidoService`: criar pedido (+ checklist zerado + transição
  inicial), transicionar estado (validando + gravando histórico).
- `ChecklistService`: marcar enviado/aceito, disparar transição
  automática quando aplicável.
- Exceções de domínio: `TransicaoInvalidaException`,
  `PedidoNaoEncontradoException`, `DocumentacaoIncompletaException`.
- **Este é o bloco onde a cobertura de JUnit 5 é prioridade** — é
  aqui que mora a regra de negócio real.
- Confirmação: suíte de testes unitários cobrindo a tabela de
  correlação da Spec (linhas "Unitário").

## Fase 4 — Bloco: Controller + DTOs

- DTOs de request/response, validação Bean Validation.
- `PedidoController` com os endpoints definidos na Spec.
- `@ControllerAdvice` global de tratamento de erro.
- Confirmação: testes de API (`@SpringBootTest` + `MockMvc` ou REST
  Assured) cobrindo as linhas "API" da tabela de correlação.

## Fase 5 — Bloco: E2E

- Setup Playwright.
- Cenário 1: fluxo completo criado → entregue.
- Cenário 2: fluxo com cancelamento antes do embarque.
- Confirmação: os dois cenários rodam localmente e passam.

## Fase 6 — CI

- GitHub Actions: roda a suíte inteira (unitário + API + E2E) a cada
  push. Badge de status no README.
- Fora desta Spec, mas é o gate final antes de considerar a F01
  "defensável" para portfólio.

---

**Foundation feature**: F01 não tem dependência de nenhuma outra
feature — é ela própria a fundação do projeto. Módulo de IA (fase
posterior) consome os relatórios de execução gerados na Fase 6.
