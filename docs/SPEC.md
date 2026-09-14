# Spec técnica — F01: Ciclo de vida do pedido de exportação

> Entrada: `docs/PRD.md` (F01). Complexidade classificada como **média**
> (entidade central com máquina de estados + duas entidades relacionadas,
> sem concorrência, sem integração externa, sem multiusuário).

## Stack

Java 21, Spring Boot 3.x, Spring Data JPA (Hibernate), PostgreSQL,
Flyway, Docker Compose (Postgres local), JUnit 5, Bean Validation.

## Decisões de design (com trade-off já resolvido, não reabrir sem fricção real)

- **Máquina de estados**: enum `PedidoEstado` com mapa estático de
  transições válidas + método `podeTransicionarPara(Estado)`. Sem State
  Pattern — não há comportamento divergente por estado hoje, só
  validação de sequência (princípio Hashimoto).
- **ID técnico**: UUID (v4), gerado na aplicação. `numero_invoice` é a
  chave de negócio (única, usada nas rotas), não o ID técnico.
- **Histórico de transições**: tabela dedicada `pedido_transicao`,
  populada explicitamente no service a cada mudança de estado —
  sem Envers.
- **Erros**: exceções de domínio customizadas, capturadas por
  `@ControllerAdvice` global. `TransicaoInvalidaException` → 409,
  `PedidoNaoEncontradoException` → 404,
  `DocumentacaoIncompletaException` → 409.
- **Transições automáticas vs. manuais**: os dois primeiros passos do
  fluxo documental são efeitos colaterais de ações no checklist, não
  chamadas manuais de transição — reflete o negócio real (o estado
  muda *porque* o documento foi enviado/aceito, não por um clique
  redundante). As demais transições (embarque, pagamentos, entrega,
  cancelamento) são explícitas.

## Modelo de dados

### `pedido`

| Campo | Tipo | Constraint |
|---|---|---|
| id | UUID | PK |
| numero_invoice | VARCHAR(50) | UNIQUE, NOT NULL |
| cliente | VARCHAR(200) | NOT NULL |
| pais_destino | VARCHAR(100) | NOT NULL |
| porto_destino | VARCHAR(100) | NOT NULL |
| produto | VARCHAR(100) | NOT NULL |
| quantidade | NUMERIC(12,2) | NOT NULL |
| unidade_medida | VARCHAR(10) | NOT NULL (ex: TON, KG) |
| cia_maritima | VARCHAR(100) | NULL |
| numero_container | VARCHAR(30) | NULL |
| preco_acordado | NUMERIC(14,2) | NOT NULL |
| moeda | VARCHAR(3) | NOT NULL, DEFAULT 'USD' |
| estado | VARCHAR(40) | NOT NULL |
| pagamento_parcial_confirmado_em | TIMESTAMP | NULL |
| pagamento_saldo_confirmado_em | TIMESTAMP | NULL |
| criado_em | TIMESTAMP | NOT NULL |
| atualizado_em | TIMESTAMP | NOT NULL |

Índice único em `numero_invoice` (é a rota de consulta principal).

### `checklist_documento`

| Campo | Tipo | Constraint |
|---|---|---|
| id | UUID | PK |
| pedido_id | UUID | FK → pedido.id, NOT NULL |
| tipo_documento | VARCHAR(40) | NOT NULL (enum: INVOICE, PACKING_LIST, BL, CERTIFICADO_SANITARIO) |
| enviado_em | TIMESTAMP | NULL |
| aceito_em | TIMESTAMP | NULL |

Constraint `UNIQUE(pedido_id, tipo_documento)`. Os 4 registros são
criados automaticamente (zerados) no momento da criação do pedido —
não existe pedido sem seu checklist completo desde o início.

### `pedido_transicao`

| Campo | Tipo | Constraint |
|---|---|---|
| id | UUID | PK |
| pedido_id | UUID | FK → pedido.id, NOT NULL |
| estado_anterior | VARCHAR(40) | NULL (null só no registro de criação) |
| estado_novo | VARCHAR(40) | NOT NULL |
| ocorrido_em | TIMESTAMP | NOT NULL |

Índice em `pedido_id` (consulta de histórico por pedido).

### Migration Flyway
`V1__create_pedido_schema.sql` cria as três tabelas acima nesta
ordem (respeitando FKs).

## Máquina de estados — implementação

```java
enum PedidoEstado {
    CRIADO,
    DOCUMENTACAO_ENVIADA,
    DOCUMENTACAO_ACEITA,
    PAGAMENTO_PARCIAL_RECEBIDO,
    EMBARCADO,
    PAGAMENTO_SALDO_RECEBIDO,
    DOCUMENTOS_ORIGINAIS_ENVIADOS,
    ENTREGUE,
    CANCELADO
}
```

Mapa de transições válidas (`Map<PedidoEstado, Set<PedidoEstado>>`),
com `CANCELADO` acrescido como destino válido em todo estado anterior
a `EMBARCADO` (não em `EMBARCADO` nem posteriores).

## Endpoints

| Método | Rota | Efeito |
|---|---|---|
| POST | `/pedidos` | Cria pedido (estado `CRIADO`), cria checklist zerado (4 itens), grava transição inicial (anterior=null, novo=CRIADO) |
| GET | `/pedidos/{numeroInvoice}` | Consulta pedido + estado atual + checklist |
| GET | `/pedidos/{numeroInvoice}/historico` | Lista transições ordenadas por `ocorrido_em` |
| PATCH | `/pedidos/{numeroInvoice}/documentos/{tipo}/enviar` | Marca `enviado_em`. Se pedido está em `CRIADO`, transiciona automaticamente para `DOCUMENTACAO_ENVIADA` |
| PATCH | `/pedidos/{numeroInvoice}/documentos/{tipo}/aceitar` | Marca `aceito_em` (exige `enviado_em` preenchido, senão 409). Se todos os 4 itens ficarem com `aceito_em` preenchido, transiciona automaticamente para `DOCUMENTACAO_ACEITA` |
| POST | `/pedidos/{numeroInvoice}/pagamento-parcial` | Exige estado `DOCUMENTACAO_ACEITA`. Marca `pagamento_parcial_confirmado_em`, transiciona para `PAGAMENTO_PARCIAL_RECEBIDO` |
| POST | `/pedidos/{numeroInvoice}/pagamento-saldo` | Exige estado `EMBARCADO`. Marca `pagamento_saldo_confirmado_em`, transiciona para `PAGAMENTO_SALDO_RECEBIDO` |
| PATCH | `/pedidos/{numeroInvoice}/transicionar` | Body `{ "novoEstado": "..." }`. Usado para `EMBARCADO`, `DOCUMENTOS_ORIGINAIS_ENVIADOS`, `ENTREGUE`, `CANCELADO` — valida contra o mapa de transições, rejeita com 409 se inválida |

Toda resposta de erro segue corpo padrão:
```json
{ "erro": "TRANSICAO_INVALIDA", "mensagem": "...", "estadoAtual": "...", "estadoSolicitado": "..." }
```

## Tabela de correlação — critério de aceitação × teste

| Critério (do PRD) | Tipo de teste | O que valida |
|---|---|---|
| Pedido nasce em `CRIADO` com checklist zerado | Unitário | `PedidoServiceTest` — criação gera 4 itens de checklist sem datas |
| Cancelamento proibido a partir de `EMBARCADO` | Unitário | `PedidoEstadoTest` — `podeTransicionarPara(CANCELADO)` retorna false a partir de `EMBARCADO` e posteriores |
| Transição fora de sequência é rejeitada explicitamente | Unitário + API | `PedidoServiceTest` (exceção lançada) + teste de API validando 409 |
| Avanço para `DOCUMENTACAO_ACEITA` exige todos os docs aceitos | Unitário | `ChecklistServiceTest` — aceitar 3 de 4 não transiciona; aceitar o 4º transiciona |
| Toda transição gera registro de histórico correto | Unitário | `PedidoServiceTest` — verifica `estado_anterior`/`estado_novo` gravados |
| Consulta por número de invoice retorna estado atual | API | `PedidoControllerIT` — GET retorna 200 com estado correto |
| Fluxo completo criado→entregue | E2E | Playwright — percorre o fluxo via API/UI e confirma estado final `ENTREGUE` |
| Fluxo com cancelamento antes do embarque | E2E | Playwright — cria, cancela em `DOCUMENTACAO_ACEITA`, confirma estado final `CANCELADO` e histórico íntegro |

## Fora de escopo desta Spec

Módulo de IA (triagem de testes), pipeline de CI, e autenticação —
não fazem parte da F01, tratados em specs/tickets separados quando a
F01 estiver implementada e testada.
