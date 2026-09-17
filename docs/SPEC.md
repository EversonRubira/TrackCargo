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
| consignee | VARCHAR(200) | NOT NULL (V3) — destinatário no BL, separado do cliente (comprador). Podem ser partes diferentes e o consignee pode mudar depois do embarque (caso real: desistência do comprador original do negócio com seu consignee) |
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
| tipo_documento | VARCHAR(40) | NOT NULL (enum: INVOICE, PACKING_LIST, BL, CERTIFICADO_SANITARIO, DOCUMENTO_ADICIONAL) |
| descricao | VARCHAR(200) | NULL — usado só em `DOCUMENTO_ADICIONAL` |
| enviado_em | TIMESTAMP | NULL |
| aceito_em | TIMESTAMP | NULL |
| reaberto_em | TIMESTAMP | NULL (V3) |
| motivo_reabertura | VARCHAR(500) | NULL (V3) |

Constraint `UNIQUE(pedido_id, tipo_documento)`. Os 4 registros
obrigatórios são criados automaticamente (zerados) no momento da
criação do pedido — não existe pedido sem seu checklist completo
desde o início.

> **Cardinalidade confirmada:** a mesma constraint `UNIQUE(pedido_id,
> tipo_documento)` se aplica a `DOCUMENTO_ADICIONAL`, então
> `PedidoService.adicionarDocumentoAdicional()` aceita só **um**
> documento adicional por pedido — decisão de domínio confirmada pelo
> dono do negócio, não uma limitação a corrigir. A constraint da V1
> foi mantida como está; uma segunda chamada para o mesmo pedido
> lança `DocumentoAdicionalJaExisteException` (checagem explícita
> antes do save, não mais `DataIntegrityViolationException` vazando
> do banco).

### `pedido_ocorrencia` (V3)

| Campo | Tipo | Constraint |
|---|---|---|
| id | UUID | PK |
| pedido_id | UUID | FK → pedido.id, NOT NULL |
| tipo | VARCHAR(40) | NOT NULL (enum: REABERTURA_DOCUMENTO, ALTERACAO_DADOS_PEDIDO, OUTRO) |
| descricao | VARCHAR(500) | NOT NULL — motivo, sempre obrigatório |
| ocorrido_em | TIMESTAMP | NOT NULL |

Índice em `pedido_id`. Tabela genérica para eventos que geram
atraso/custo extra e precisam de motivo documentado — cobre hoje
reabertura de documento já aceito e alteração de consignee
pós-embarque; não é histórico de todas as mudanças do pedido (isso
continua em `pedido_transicao` para estado).

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

## Regras de negócio — Fase 3 (Service)

Decisões vindas de discussão durante a implementação, não do PRD
original — registradas aqui para não ficarem só no código:

- **Aceite de documento do checklist é definitivo.** Uma vez que
  `aceito_em` é preenchido, `enviar()` e `aceitar()` rejeitam qualquer
  nova chamada sobre aquele documento (`DocumentoJaAceitoException`).
  A única forma de mudar um documento já aceito é
  `reabrirAposAceite()`.
- **`reabrirAposAceite()` só reverte o estado do pedido enquanto ele
  ainda está em `DOCUMENTACAO_ACEITA`.** Depois de `EMBARCADO`, o
  estado não regride — não há como "desfazer" um navio que já saiu —
  mas o registro em `pedido_ocorrencia` (tipo `REABERTURA_DOCUMENTO`)
  é criado do mesmo jeito, com o motivo.
- **`DOCUMENTACAO_ENVIADA` (no 1º envio) e `DOCUMENTACAO_ACEITA`
  (quando todo o checklist do pedido está aceito) são efeitos
  colaterais de `ChecklistService`, nunca destino de
  `PATCH /transicionar`.** `PedidoEstado.podeTransicionarManualmentePara()`
  exclui os dois de todo conjunto de destino, e `ChecklistService`
  muda o estado diretamente (sem passar por esse método) — ver
  `TRANSICOES_MANUAIS` em `PedidoEstado`.

`reabrirAposAceite()` valida que o documento estava de fato aceito
(`aceito_em != null`) antes de reabrir — chamado sobre um documento
não aceito, lança `DocumentoNaoAceitoException` (mesmo padrão de
`DocumentoNaoEnviadoException` de `aceitar()`).

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
| Pedido nasce em `CRIADO` com checklist zerado | Unitário | `PedidoServiceTest.criarGeraChecklistZeradoETransicaoInicial` |
| Cancelamento proibido a partir de `EMBARCADO` | Unitário | **Lacuna** — `PedidoEstadoTest` não existe; não há teste direto de `TRANSICOES_MANUAIS` |
| Transição fora de sequência é rejeitada explicitamente | Unitário + API | `PedidoServiceTest.transicionarComEstadoInvalidoLancaExcecaoENaoGravaHistorico`. API ainda não implementada (Fase 4) |
| Avanço para `DOCUMENTACAO_ACEITA` exige todos os docs aceitos | Unitário | `ChecklistServiceTest.aceitarTodosOsItensDisparaDocumentacaoAceita` |
| Aceite de documento é definitivo (não muda por `enviar()`/`aceitar()` de novo) | Unitário | `ChecklistServiceTest.naoPermiteAceitarDocumentoNaoEnviado` cobre o caso de não-enviado; falta caso explícito de "aceitar de novo o que já foi aceito" |
| `reabrirAposAceite()` reverte estado só antes do embarque; exige documento aceito | Unitário | `ChecklistServiceTest.reabrirAntesDoEmbarqueReverteEstadoDoPedido` + `reabrirAposEmbarqueNaoReverteEstadoDoPedido` + `naoPermiteReabrirDocumentoQueNuncaFoiAceito` |
| `adicionarDocumentoAdicional()` rejeita segundo documento pro mesmo pedido | Unitário | `PedidoServiceTest.naoPermiteSegundoDocumentoAdicionalParaOMesmoPedido` |
| Toda transição gera registro de histórico correto | Unitário | `PedidoServiceTest.criarGeraChecklistZeradoETransicaoInicial` + `transicionarComEstadoValidoAtualizaPedidoEGravaHistorico` |
| `alterarConsignee()` grava `PedidoOcorrencia` | Unitário | `PedidoServiceTest.alterarConsigneeAtualizaPedidoEGeraOcorrencia` |
| Unicidade de `numero_pedido` | Repositório | `PedidoRepositoryTest.naoDevePermitirDoisPedidosComMesmoNumero` |
| Consulta por número de pedido retorna estado atual | API | Fora de escopo ainda — Fase 4 |
| Fluxo completo criado→entregue | E2E | Fora de escopo ainda — Fase 5 |
| Fluxo com cancelamento antes do embarque | E2E | Fora de escopo ainda — Fase 5 |

## Fora de escopo desta Spec

Módulo de IA (triagem de testes), pipeline de CI, e autenticação —
não fazem parte da F01, tratados em specs/tickets separados quando a
F01 estiver implementada e testada.
