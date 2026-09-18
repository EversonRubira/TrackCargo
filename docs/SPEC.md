# Spec técnica — F01 (ciclo de vida do pedido), F02 (painel do fornecedor), F03 (PDF de status)

> Entrada: `docs/PRD.md` (F01, F02, F03 — PR #15). Complexidade
> classificada como **média** (entidade central com máquina de
> estados + duas entidades relacionadas, sem concorrência, sem
> integração externa, sem multiusuário).
>
> **F02 (painel do fornecedor) e F03 (PDF de status) estão
> especificadas nas seções próprias abaixo, mas ainda não
> implementadas** — este é um documento de spec antes do código,
> mesmo padrão usado antes do scaffold do backend (Fase 0). Nenhuma
> linha de React nem o endpoint de listagem/PDF existem ainda.

## Stack

Backend: Java 21, Spring Boot 4.1.x, Spring Data JPA (Hibernate),
PostgreSQL, Flyway, Docker Compose (Postgres local), JUnit 5, Bean
Validation.

Frontend (F02, ainda não implementado): React + TypeScript + Vite +
Tailwind CSS + React Router. Cliente HTTP: `fetch` nativo — sem axios,
não há necessidade real hoje que justifique mais uma dependência (o
projeto já erra pro lado de poucas dependências, ver REST Assured e
Groovy nas notas de Fase 5).

Geração de PDF (F03, ainda não implementado): **OpenPDF**
(`com.github.librepdf:openpdf`, pacote `com.lowagie.text.*` — nome
herdado do iText 2.x, de quem o OpenPDF é fork) no **backend**. Ver
decisão completa na seção "Decisões de design" e no F03 abaixo.

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

> Nota de stack (Fase 5): `@SpringBootTest`/`@LocalServerPort`
> continuam em `spring-boot-test` (`org.springframework.boot.test.*`),
> não sofreram o mesmo split de `@DataJpaTest`/`@WebMvcTest` — não
> precisou de artefato novo. Duas pegadinhas achadas com REST Assured
> 5.5.6, nenhuma delas do REST Assured em si:
> 1. Ele exige um serializer JSON explícito no classpath
>    (Jackson Databind 2.x, Gson, Johnzon ou Yasson) pra serializar
>    `.body(pojo)`/`.body(map)` — o projeto está em Jackson 3
>    (`tools.jackson.*`, ver acima), que ele não reconhece, e falha em
>    runtime com `IllegalStateException: Cannot serialize object
>    because no JSON serializer found in classpath`. Contornado
>    mandando o corpo como `String` (JSON literal via text block) —
>    não precisa de serializer nenhum, e evita adicionar mais uma
>    dependência só pra isso.
> 2. `spring-boot-dependencies` (importado via parent) fixa Groovy em
>    **5.0.8** através de um import de `groovy-bom`, mas o REST
>    Assured 5.5.x foi construído contra Groovy 4.0.22 — o MOP do
>    Groovy 5 quebra internamente no REST Assured
>    (`NullPointerException` em `ClosureMetaClass` ao montar
>    requests `PATCH`, especificamente). Corrigido com
>    `<dependencyManagement>` explícito no `pom.xml` re-fixando
>    `org.apache.groovy:groovy`/`groovy-xml`/`groovy-json` em
>    `4.0.22` — um simples override de propriedade não bastava,
>    porque o placeholder `${groovy.version}` dentro do `groovy-bom`
>    importado já foi interpolado com o valor do POM do Boot quando
>    publicado.

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
- **E2E: REST Assured em vez de Playwright (mudança de escopo da
  Fase 5, decidida com o dono do domínio).** O PLAN.md original
  previa Playwright pros dois cenários E2E, mas hoje não existe UI
  nenhuma — o sistema é só API REST (o "portal do cliente"/frontend
  React é backlog v2, ainda não construído). Rodar Playwright contra
  nada seria usar a ferramenta certa pro trabalho errado: Playwright
  automatiza *browser*, e sem página pra abrir ele não testa nada que
  uma chamada HTTP direta já não cubra, só adiciona a sobrecarga de
  um browser headless sem necessidade. REST Assured testa o mesmo
  contrato (API real, `@SpringBootTest(RANDOM_PORT)`, Postgres real,
  sem mock) com a ferramenta proporcional ao que existe agora.
  Playwright volta a fazer sentido quando o frontend existir de
  verdade — nesse momento os cenários de UI dele são adicionais aos
  de API, não substitutos.
- **Transições automáticas vs. manuais**: os dois primeiros passos do
  fluxo documental são efeitos colaterais de ações no checklist, não
  chamadas manuais de transição — reflete o negócio real (o estado
  muda *porque* o documento foi enviado/aceito, não por um clique
  redundante). As demais transições (embarque, pagamentos, entrega,
  cancelamento) são explícitas.
- **PDF de status (F03) nasce no backend, não no frontend.** O
  backlog v2 já compromete com envio automático de e-mail a cada
  mudança de estado — isso só roda no backend (reação a uma transição
  dentro de `PedidoService`, sem navegador aberto, sem usuário
  clicando nada). Gerar o PDF no frontend agora significaria
  reescrever a mesma lógica em Java quando essa automação chegar. O
  endpoint HTTP (`GET /pedidos/{numeroPedido}/status.pdf`, ver F03) é
  o mesmo ponto que tanto o botão "Gerar PDF de status" do painel
  quanto a futura automação de e-mail vão usar — o botão via HTTP, a
  automação chamando o serviço de geração diretamente (mesmo processo,
  sem round-trip HTTP consigo mesma). Um só lugar que sabe montar o
  PDF, não dois.
- **Biblioteca de PDF: OpenPDF, não iText moderno.** OpenPDF é fork
  livre do iText 2.x (licença LGPL/MPL, permissiva). O iText moderno
  (5+) é AGPL — obriga a abrir o código de qualquer produto que o
  use publicamente ou exige licença comercial paga. Como este é um
  produto que pode ser vendido (não é ferramenta interna descartável),
  AGPL é uma armadilha de licenciamento a evitar desde o início, não
  um detalhe pra resolver depois.
- **Stack do frontend (F02): React + TypeScript + Vite + Tailwind CSS
  + React Router, `fetch` nativo em vez de axios.** Vite pela
  velocidade de dev padrão do ecossistema React hoje; Tailwind evita
  escrever CSS a mais pra 3 telas; React Router porque são 3 rotas
  reais (lista, criar, detalhe), não uma SPA de página única. `fetch`
  nativo cobre tudo que as 3 telas precisam (GET/POST/PATCH com JSON)
  sem justificar a dependência extra do axios — mesmo raciocínio já
  aplicado ao backend (não adicionar biblioteca sem fricção real).

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

### Endpoints novos (F02/F03 — spec, ainda não implementados)

| Método | Rota | Efeito |
|---|---|---|
| GET | `/pedidos` | Lista pedidos (`PedidoResponse[]`, mesmo DTO do GET individual — sem projeção resumida própria, ver justificativa abaixo). Query param opcional `?estado=` filtra por `PedidoEstado` (ex: `?estado=EMBARCADO`); omitido, retorna todos |
| GET | `/pedidos/{numeroPedido}/status.pdf` | Gera e devolve o PDF de status (F03) do pedido no estado atual. `Content-Type: application/pdf`. Sem autenticação, mesma decisão de escopo do resto da API nesta leva. 404 (`PEDIDO_NAO_ENCONTRADO`) se o pedido não existir |

**`GET /pedidos` reusa `PedidoResponse` sem criar um DTO de listagem
resumido.** Cada linha da tela de lista (F02) só exibe um subconjunto
dos campos (número, cliente, consignee, produto, ciaMaritima, estado,
atualizadoEm), mas criar um segundo DTO só pra isso seria otimização
prematura: o volume esperado é de uso por um único operador (ver
Audiência no PRD), não uma tabela com milhares de linhas exigindo
payload enxuto. Frontend simplesmente ignora os campos que não
mostra na tabela.

**Suporte necessário no backend pra `GET /pedidos`:**
`PedidoRepository` ganha `findAll()` (já herdado de `JpaRepository`,
nada a escrever) e `findByEstado(PedidoEstado estado)` (novo, Spring
Data derivado); `PedidoService` ganha `listar(PedidoEstado
estadoOuNull)` que delega pra um ou outro conforme o parâmetro seja
nulo; `PedidoController` ganha o método de rota com
`@RequestParam(required = false) PedidoEstado estado`.

**`GET /pedidos/{numeroPedido}/status.pdf` (F03) — desenho do
serviço:** um `PdfStatusService` (pacote a decidir, provavelmente
`pedido.pdf`) monta o documento a partir de `Pedido` + a lista de
`PedidoTransicao` (pro estado atual e a posição na barra de
progresso) — os mesmos dados que `PedidoResponse`/
`PedidoTransicaoResponse` já expõem, nenhum campo novo precisa ser
calculado ou persistido. Conteúdo do PDF: dados do pedido (número,
cliente, consignee, produto, incoterm) + visual de progresso das 8
etapas do ciclo de vida (`CRIADO` → ... → `ENTREGUE`; `CANCELADO` é
estado terminal fora dessa sequência, tratado à parte no visual —
ex: um selo "cancelado" em vez de posição na barra), marcando em qual
etapa o pedido está agora — inspirado em rastreio de transportadora
(DHL, Correios). `PedidoController.statusPdf()` (ou um controller
próprio) chama `PdfStatusService.gerar(pedido, historico)`
diretamente — é o mesmo método que a futura automação de e-mail
(backlog v2) vai chamar, sem passar por HTTP: o endpoint é uma forma
de expor esse serviço pro navegador, não o único consumidor dele.

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

## F02 — Painel do fornecedor (spec técnica, ainda não implementado)

Três rotas (React Router), uma por tela do PRD:

| Rota | Tela |
|---|---|
| `/pedidos` | Lista de pedidos |
| `/pedidos/novo` | Criar pedido |
| `/pedidos/:numeroPedido` | Detalhe do pedido |

### Lista de pedidos

Chama `GET /pedidos` (com `?estado=` quando o filtro estiver ativo).
Colunas da tabela, todas vindas de `PedidoResponse`: `numeroPedido`,
`cliente`, `consignee`, `produto` (rotulado "descrição" na UI),
`ciaMaritima`, `estado`, `atualizadoEm` (rotulado "última
atualização"). Filtro por estado é um `<select>` com os 9 valores de
`PedidoEstado`, refazendo o GET com `?estado=` ao mudar.

### Criar pedido

Formulário mapeado campo a campo pro `CriarPedidoRequest` existente —
nenhum campo novo, nenhum campo do formulário sem destino no DTO.
Agrupamento visual (PRD, seção F02) e o campo do DTO correspondente:

| Grupo do formulário | Campo(s) do `CriarPedidoRequest` |
|---|---|
| Identificação | `numeroPedido`, `cliente`, `consignee` |
| Descrição da mercadoria | `produto`, `quantidade`, `unidadeMedida` |
| Logística | `paisDestino`, `portoOrigem`, `portoDestino` |
| Condições comerciais | `condicoesComerciais.precoAcordado`, `condicoesComerciais.moeda`, `condicoesComerciais.incoterm`, `condicoesComerciais.formaPagamento`, `condicoesComerciais.percentualParcial` |

Submit: `POST /pedidos` com o corpo montado no formato aninhado que
`CriarPedidoRequest` já exige (objeto `condicoesComerciais`, não os 5
campos soltos). Sucesso (`201`) navega pra `/pedidos/{numeroPedido}`
(o `numeroPedido` retornado no `PedidoResponse` da resposta).

**Gap conhecido, sem endpoint ainda:** `ciaMaritima` e
`numeroContainer` existem no schema e em `PedidoResponse`, mas não
há nenhum endpoint (nem em `CriarPedidoRequest`, nem em nenhum PATCH)
pra defini-los — nem F01 nem esta spec de F02 criam um. Fazem sentido
como dados que só existem depois da reserva de espaço no navio,
tipicamente depois da criação do pedido, mas isso é uma lacuna real
de API, não uma escolha de UI: o formulário de criação não os inclui
porque não têm onde ir, e a tela de detalhe (abaixo) só os exibe
como texto (read-only) quando presentes — sem UI de edição, porque
não haveria o que chamar. Registrar como pendência pra uma spec
futura, não resolver aqui.

### Detalhe do pedido

Carrega `GET /pedidos/{numeroPedido}` (dados do pedido + checklist,
via `PedidoResponse`/`ChecklistDocumentoResponse`) e `GET
/pedidos/{numeroPedido}/historico` (via `PedidoTransicaoResponse[]`)
ao montar a tela. Ações e o endpoint que cada uma dispara:

| Ação na tela | Endpoint |
|---|---|
| Enviar documento (por tipo do checklist) | `PATCH /pedidos/{numero}/documentos/{tipo}/enviar` |
| Aceitar documento (por tipo) | `PATCH /pedidos/{numero}/documentos/{tipo}/aceitar` |
| Reabrir documento (por tipo + motivo) | `PATCH /pedidos/{numero}/documentos/{tipo}/reabrir` |
| Transicionar (escolher novo estado) | `PATCH /pedidos/{numero}/transicionar` |
| Cancelar | **Não é endpoint separado** — é `PATCH /pedidos/{numero}/transicionar` com `{ "novoEstado": "CANCELADO" }`, já que `CANCELADO` é só mais um destino válido no mapa de transições do enum. A UI pode dar um botão dedicado "Cancelar pedido", mas por baixo é a mesma chamada de transicionar |
| Confirmar pagamento parcial | `POST /pedidos/{numero}/pagamento-parcial` |
| Confirmar pagamento de saldo | `POST /pedidos/{numero}/pagamento-saldo` |
| Gerar PDF de status | `GET /pedidos/{numero}/status.pdf` — abre em nova aba (`target="_blank"`) ou dispara download; não é uma chamada `fetch` que precise de tratamento de JSON, é navegação direta pra uma URL que devolve `application/pdf` |

Toda ação que muda estado (enviar/aceitar/reabrir/transicionar/
pagamentos) recarrega `GET /pedidos/{numero}` e `GET
/pedidos/{numero}/historico` depois de um `2xx` — não há
atualização otimista de estado no cliente, o backend é a fonte da
verdade e as chamadas são baratas o suficiente pra um único operador.

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
| Fluxo completo criado→entregue (documentação, pagamentos, embarque, entrega) — valida cada estado retornado e o histórico completo ao final | E2E | `FluxoPedidoE2ETest.fluxoCompletoCriadoAteEntregue` |
| Fluxo com cancelamento antes do embarque — transição após `CANCELADO` rejeitada com 409 | E2E | `FluxoPedidoE2ETest.cancelamentoAntesDoEmbarqueImpedeQualquerTransicaoDepois` |
| Pagamento-saldo fora de sequência retorna 409 na API real (não só no nível de Service) | E2E | `FluxoPedidoE2ETest.pagamentoSaldoForaDeSequenciaRetorna409NaAPIReal` |
| `GET /pedidos` lista todos os pedidos sem filtro; `?estado=` filtra corretamente | Unitário + API | **Planejado** (F02, ainda não implementado) — `PedidoServiceTest.listarSemFiltroRetornaTodosOsPedidos` + `listarComFiltroDeEstadoRetornaSoOsQueBatem` + `PedidoControllerTest.listarRetorna200ComTodosOsPedidos` + `listarComFiltroEstadoRetorna200SoComOsFiltrados` |
| `GET /pedidos/{numero}/status.pdf` retorna 200 com `Content-Type: application/pdf`; pedido inexistente retorna 404 | API | **Planejado** (F03, ainda não implementado) — `PedidoControllerTest.gerarPdfStatusRetorna200ComContentTypePdf` + `gerarPdfStatusDePedidoInexistenteRetorna404` |

## Fora de escopo desta Spec

Módulo de IA (triagem de testes), pipeline de CI, e autenticação —
não fazem parte de F01/F02/F03, tratados em specs/tickets separados
quando estiverem implementadas e testadas. Autenticação, especificamente,
segue fora de escopo pras três features desta leva (F01, F02, F03) —
mesma decisão registrada no PRD.
