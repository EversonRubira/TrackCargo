# Spec técnica — F01: Ciclo de vida do pedido de exportação

> Entrada: `docs/PRD.md` (F01). Complexidade classificada como **média**
> (entidade central com máquina de estados + duas entidades relacionadas,
> sem concorrência, sem integração externa, sem multiusuário).

## Stack

Java 21, Spring Boot 4.1.x, Spring Data JPA (Hibernate), PostgreSQL,
Flyway, Docker Compose (Postgres local), JUnit 5, Bean Validation.

> Nota de stack (Fase 4): Boot 4.1 trocou pacote/artefato de teste 3
> vezes ao longo das fases — Flyway (Fase 1), `@DataJpaTest`/
> `AutoConfigureTestDatabase` (Fase 2, artefato
> `spring-boot-starter-data-jpa-test`) e agora `@WebMvcTest`/
> `AutoConfigureMockMvc` (Fase 4, artefato
> `spring-boot-starter-webmvc-test`, pacote
> `org.springframework.boot.webmvc.test.autoconfigure`). Fase 4 também
> descobriu que Boot 4.1 já está em **Jackson 3**: `ObjectMapper`/
> `jackson-databind`/`jackson-core` migraram do groupId
> `com.fasterxml.jackson.core` pro groupId `tools.jackson.core`
> (pacote `tools.jackson.databind.*`), embora `jackson-annotations`
> tenha ficado no groupId/pacote antigo (`com.fasterxml.jackson.*`).
> `@MockBean` foi removido; o substituto é
> `org.springframework.test.context.bean.override.mockito.MockitoBean`
> (de `spring-test`, não é mais um tipo do Boot).

## Decisões de design (com trade-off já resolvido, não reabrir sem fricção real)

- **Máquina de estados**: enum `PedidoEstado` com mapa estático de
  transições válidas + método `podeTransicionarPara(Estado)`. Sem State
  Pattern — não há comportamento divergente por estado hoje, só
  validação de sequência (princípio Hashimoto).
- **ID técnico**: UUID (v4), gerado na aplicação. `numero_pedido`
  (renomeado de `numero_invoice` na V2) é a chave de negócio (única,
  usada nas rotas), não o ID técnico.
- **Histórico de transições**: tabela dedicada `pedido_transicao`,
  populada explicitamente no service a cada mudança de estado —
  sem Envers.
- **Erros**: exceções de domínio customizadas, capturadas por
  `@RestControllerAdvice` global (`GlobalExceptionHandler`, Fase 4)
  — mapeamento completo na seção "Mapeamento de exceção → status
  HTTP" abaixo. `DocumentacaoIncompletaException` citada aqui em
  fases anteriores nunca chegou a existir: o que ela cobriria virou
  `DocumentoNaoEnviadoException`/`DocumentoJaAceitoException`.
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
| numero_pedido | VARCHAR(50) | UNIQUE, NOT NULL (renomeado de `numero_invoice` na V2 — nem todo pedido tem invoice emitida ainda quando criado) |
| cliente | VARCHAR(200) | NOT NULL |
| consignee | VARCHAR(200) | NOT NULL (V3) — destinatário no BL, separado do cliente (comprador). Podem ser partes diferentes e o consignee pode mudar depois do embarque (caso real: desistência do comprador original do negócio com seu consignee) |
| pais_destino | VARCHAR(100) | NOT NULL |
| porto_origem | VARCHAR(100) | NOT NULL (V2) |
| porto_destino | VARCHAR(100) | NOT NULL |
| produto | VARCHAR(100) | NOT NULL |
| quantidade | NUMERIC(12,2) | NOT NULL |
| unidade_medida | VARCHAR(10) | NOT NULL (ex: TON, KG) |
| cia_maritima | VARCHAR(100) | NULL |
| numero_container | VARCHAR(30) | NULL |
| preco_acordado | NUMERIC(14,2) | NOT NULL |
| moeda | VARCHAR(3) | NOT NULL, DEFAULT 'USD' |
| incoterm | VARCHAR(10) | NOT NULL (V2) — Incoterms 2020 |
| forma_pagamento | VARCHAR(30) | NOT NULL (V2) |
| percentual_parcial | NUMERIC(5,2) | NOT NULL (V2) |
| estado | VARCHAR(40) | NOT NULL |
| pagamento_parcial_confirmado_em | TIMESTAMP | NULL |
| pagamento_saldo_confirmado_em | TIMESTAMP | NULL |
| criado_em | TIMESTAMP | NOT NULL |
| atualizado_em | TIMESTAMP | NOT NULL |

Índice único em `numero_pedido` (é a rota de consulta principal).

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

Implementados na Fase 4 (`PedidoController` + `ChecklistController`):

| Método | Rota | Efeito |
|---|---|---|
| POST | `/pedidos` | Cria pedido (estado `CRIADO`), cria checklist zerado (4 itens), grava transição inicial (anterior=null, novo=CRIADO). Body: `CriarPedidoRequest` |
| GET | `/pedidos/{numeroPedido}` | Consulta pedido + estado atual + checklist |
| PATCH | `/pedidos/{numeroPedido}/transicionar` | Body `{ "novoEstado": "..." }`. Valida contra `podeTransicionarManualmentePara()`, rejeita com 409 se inválida |
| PATCH | `/pedidos/{numeroPedido}/documentos/{tipo}/enviar` | Marca `enviado_em`. Se pedido está em `CRIADO`, transiciona automaticamente para `DOCUMENTACAO_ENVIADA` |
| PATCH | `/pedidos/{numeroPedido}/documentos/{tipo}/aceitar` | Marca `aceito_em` (exige `enviado_em` preenchido, senão 409). Se todos os itens obrigatórios ficarem com `aceito_em` preenchido, transiciona automaticamente para `DOCUMENTACAO_ACEITA` |
| PATCH | `/pedidos/{numeroPedido}/documentos/{tipo}/reabrir` | Body `{ "motivo": "..." }`. Exige documento aceito (senão 409); reverte pra `DOCUMENTACAO_ENVIADA` só se o pedido ainda não tiver embarcado; grava `pedido_ocorrencia` sempre |
| POST | `/pedidos/{numeroPedido}/pagamento-parcial` | Exige `podeTransicionarManualmentePara(PAGAMENTO_PARCIAL_RECEBIDO)` (mesma trava de `/transicionar`, hoje só a partir de `DOCUMENTACAO_ACEITA`), senão 409. Marca `pagamento_parcial_confirmado_em`, transiciona e grava `pedido_transicao` |
| POST | `/pedidos/{numeroPedido}/pagamento-saldo` | Exige `podeTransicionarManualmentePara(PAGAMENTO_SALDO_RECEBIDO)` (hoje só a partir de `EMBARCADO`), senão 409. Marca `pagamento_saldo_confirmado_em`, transiciona e grava `pedido_transicao` |
| GET | `/pedidos/{numeroPedido}/historico` | Lista `pedido_transicao` do pedido ordenada por `ocorrido_em` ascendente (`PedidoTransicaoResponse[]`) |

`enviar`/`aceitar`/`reabrir` devolvem `204 No Content` — não há corpo de
resposta definido pra eles ainda (cliente pode consultar `GET
/pedidos/{numero}` de novo se precisar do estado atualizado).
`pagamento-parcial`/`pagamento-saldo` devolvem `200` com o `PedidoResponse`
atualizado, igual `/transicionar`.

**Decisão de design:** `ChecklistController` ficou separado de
`PedidoController` (em vez de um único controller com todas as rotas)
espelhando a separação já existente `PedidoService`/`ChecklistService`
— cada um dono do ciclo de vida de uma entidade. As rotas continuam
aninhadas sob `/pedidos/{numeroPedido}/documentos/{tipo}` por
estrutura de URL (sub-recurso REST), o que não obriga as duas
entidades a viverem no mesmo controller.

`confirmarPagamentoParcial()`/`confirmarPagamentoSaldo()` reusam a
mesma trava de validação de `transicionar()`
(`podeTransicionarManualmentePara()`) através de um método privado
compartilhado em `PedidoService` — não duplicam a checagem nem
contornam o mapa de transições do enum.

Toda resposta de erro segue corpo padrão:
```json
{ "erro": "TRANSICAO_INVALIDA", "mensagem": "...", "estadoAtual": "...", "estadoSolicitado": "..." }
```
`estadoAtual`/`estadoSolicitado` só são preenchidos pra
`TransicaoInvalidaException`; nos demais casos ficam `null`.

### Mapeamento de exceção → status HTTP (`GlobalExceptionHandler`)

| Exceção | HTTP | `erro` |
|---|---|---|
| `PedidoNaoEncontradoException` | 404 | `PEDIDO_NAO_ENCONTRADO` |
| `ChecklistDocumentoNaoEncontradoException` | 404 | `CHECKLIST_DOCUMENTO_NAO_ENCONTRADO` |
| `TransicaoInvalidaException` | 409 | `TRANSICAO_INVALIDA` |
| `DocumentoJaAceitoException` | 409 | `DOCUMENTO_JA_ACEITO` |
| `DocumentoNaoEnviadoException` | 409 | `DOCUMENTO_NAO_ENVIADO` |
| `DocumentoNaoAceitoException` | 409 | `DOCUMENTO_NAO_ACEITO` |
| `DocumentoAdicionalJaExisteException` | 409 | `DOCUMENTO_ADICIONAL_JA_EXISTE` |
| `MethodArgumentNotValidException` (Bean Validation) | 400 | `VALIDACAO_INVALIDA` |
| `MethodArgumentTypeMismatchException` (ex: `{tipo}` inválido na rota) | 400 | `PARAMETRO_INVALIDO` |

`ChecklistDocumentoNaoEncontradoException` é nova nesta fase: cobre o
caso de pedir `enviar`/`aceitar`/`reabrir` pra um `tipo` que não tem
registro de checklist pro pedido (hoje só possível pra
`DOCUMENTO_ADICIONAL`, já que os 4 tipos obrigatórios sempre existem
desde a criação do pedido).

## Tabela de correlação — critério de aceitação × teste

| Critério (do PRD) | Tipo de teste | O que valida |
|---|---|---|
| Pedido nasce em `CRIADO` com checklist zerado | Unitário | `PedidoServiceTest.criarGeraChecklistZeradoETransicaoInicial` |
| Cancelamento proibido a partir de `EMBARCADO` | Unitário | **Lacuna** — `PedidoEstadoTest` não existe; não há teste direto de `TRANSICOES_MANUAIS` |
| Transição fora de sequência é rejeitada explicitamente | Unitário + API | `PedidoServiceTest.transicionarComEstadoInvalidoLancaExcecaoENaoGravaHistorico` + `PedidoControllerTest.transicionarComEstadoInvalidoRetorna409` |
| Avanço para `DOCUMENTACAO_ACEITA` exige todos os docs aceitos | Unitário | `ChecklistServiceTest.aceitarTodosOsItensDisparaDocumentacaoAceita` |
| Aceite de documento é definitivo (não muda por `enviar()`/`aceitar()` de novo) | Unitário | `ChecklistServiceTest.naoPermiteAceitarDocumentoNaoEnviado` cobre o caso de não-enviado; falta caso explícito de "aceitar de novo o que já foi aceito" |
| `reabrirAposAceite()` reverte estado só antes do embarque; exige documento aceito | Unitário | `ChecklistServiceTest.reabrirAntesDoEmbarqueReverteEstadoDoPedido` + `reabrirAposEmbarqueNaoReverteEstadoDoPedido` + `naoPermiteReabrirDocumentoQueNuncaFoiAceito` |
| `adicionarDocumentoAdicional()` rejeita segundo documento pro mesmo pedido | Unitário | `PedidoServiceTest.naoPermiteSegundoDocumentoAdicionalParaOMesmoPedido` |
| Toda transição gera registro de histórico correto | Unitário | `PedidoServiceTest.criarGeraChecklistZeradoETransicaoInicial` + `transicionarComEstadoValidoAtualizaPedidoEGravaHistorico` |
| `alterarConsignee()` grava `PedidoOcorrencia` | Unitário | `PedidoServiceTest.alterarConsigneeAtualizaPedidoEGeraOcorrencia` |
| `confirmarPagamentoParcial()`/`confirmarPagamentoSaldo()` marcam data, transicionam e reusam a validação de `transicionar()` | Unitário | `PedidoServiceTest.confirmarPagamentoParcialMarcaDataETransicionaEstado` + `confirmarPagamentoParcialForaDeSequenciaLancaExcecao` + `confirmarPagamentoSaldoMarcaDataETransicionaEstado` + `confirmarPagamentoSaldoSemEstarEmbarcadoLancaExcecao` |
| Unicidade de `numero_pedido` | Repositório | `PedidoRepositoryTest.naoDevePermitirDoisPedidosComMesmoNumero` |
| POST /pedidos cria e retorna 201 com checklist | API | `PedidoControllerTest.criarRetorna201ComPedidoCriado` |
| Request de criação inválido retorna 400 | API | `PedidoControllerTest.criarComCamposObrigatoriosFaltandoRetorna400` |
| Consulta por número de pedido retorna estado atual + checklist | API | `PedidoControllerTest.buscarPorNumeroRetorna200ComPedidoEChecklist` |
| Pedido inexistente retorna 404 | API | `PedidoControllerTest.buscarPorNumeroInexistenteRetorna404` |
| PATCH /transicionar com estado inválido retorna 409 com estadoAtual/estadoSolicitado | API | `PedidoControllerTest.transicionarComEstadoInvalidoRetorna409` |
| PATCH /transicionar sem novoEstado retorna 400 | API | `PedidoControllerTest.transicionarSemNovoEstadoRetorna400` |
| Enviar/aceitar documento retorna 204; erro de domínio retorna 409 | API | `ChecklistControllerTest.enviarRetorna204` + `enviarDocumentoJaAceitoRetorna409` + `aceitarRetorna204` + `aceitarDocumentoNaoEnviadoRetorna409` |
| Reabrir documento aceito retorna 204; sem aceite prévio retorna 409; sem motivo retorna 400 | API | `ChecklistControllerTest.reabrirRetorna204` + `reabrirDocumentoNaoAceitoRetorna409` + `reabrirSemMotivoRetorna400` |
| Documento inexistente (tipo sem registro de checklist) retorna 404; `{tipo}` inválido na rota retorna 400 | API | `ChecklistControllerTest.enviarDocumentoInexistenteRetorna404` + `tipoDocumentoInvalidoNaRotaRetorna400` |
| Pagamento parcial/saldo fora de sequência retorna 409; válido retorna 200 | API | `PedidoControllerTest.confirmarPagamentoParcialRetorna200` + `confirmarPagamentoParcialForaDeSequenciaRetorna409` + `confirmarPagamentoSaldoRetorna200` + `confirmarPagamentoSaldoSemEstarEmbarcadoRetorna409` |
| Histórico vazio retorna lista vazia; com transições retorna ordenado; pedido inexistente retorna 404 | API | `PedidoControllerTest.historicoVazioRetorna200ComListaVazia` + `historicoComTransicoesRetornaListaOrdenada` + `historicoDePedidoInexistenteRetorna404` |
| Fluxo completo criado→entregue | E2E | Fora de escopo ainda — Fase 5 |
| Fluxo com cancelamento antes do embarque | E2E | Fora de escopo ainda — Fase 5 |

## Fora de escopo desta Spec

Módulo de IA (triagem de testes), pipeline de CI, e autenticação —
não fazem parte da F01, tratados em specs/tickets separados quando a
F01 estiver implementada e testada.
