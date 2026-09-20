# Spec técnica — F01 (ciclo de vida do pedido), F02 (painel do fornecedor), F03 (PDF de status)

> Entrada: `docs/PRD.md` (F01, F02, F03 — PR #15). Complexidade
> classificada como **média** (entidade central com máquina de
> estados + duas entidades relacionadas, sem concorrência, sem
> integração externa, sem multiusuário).
>
> **Backend de F02/F03 implementado** (`GET /pedidos`, `PATCH
> /pedidos/{numero}/logistica`, `GET /pedidos/{numero}/status.pdf`
> — ver seção "Endpoints de F02/F03" abaixo). **F02 em si (o painel,
> React) segue só especificado, sem nenhuma linha de código ainda**
> — as telas descritas na seção "F02 — Painel do fornecedor" abaixo
> vão consumir esses endpoints quando implementadas.

## Stack

Backend: Java 21, Spring Boot 4.1.x, Spring Data JPA (Hibernate),
PostgreSQL, Flyway, Docker Compose (Postgres local), JUnit 5, Bean
Validation.

Frontend (F02, ainda não implementado): React + TypeScript + Vite +
Tailwind CSS + React Router. Cliente HTTP: `fetch` nativo — sem axios,
não há necessidade real hoje que justifique mais uma dependência (o
projeto já erra pro lado de poucas dependências, ver REST Assured e
Groovy nas notas de Fase 5).

Geração de PDF (F03): **OpenPDF `3.0.5`** (`com.github.librepdf:openpdf`,
pacote `org.openpdf.text.*` — renomeado de `com.lowagie.text.*`, nome
herdado do iText 2.x, a partir da major version 2.x/3.x) no
**backend**. Ver decisão completa na seção "Decisões de design" e no
F03 abaixo. Extração de texto nos testes usa o `PdfTextExtractor` do
próprio OpenPDF (nenhuma dependência nova) — ver "Seção Documentos do
PDF de status" mais abaixo pra limitação conhecida dele com
acentos/cedilha.

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
  **Vitest** entrou como devDependency (numeração automática) só pro
  teste puro de encode/decode de URL do bug de navegação — sem jsdom
  nem Testing Library, não precisou renderizar componente nenhum pra
  provar a correção.

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

### `pedido_ocorrencia` (V3, colunas `tipo_documento`/`envio_recusado_em` na V5)

| Campo | Tipo | Constraint |
|---|---|---|
| id | UUID | PK |
| pedido_id | UUID | FK → pedido.id, NOT NULL |
| tipo | VARCHAR(40) | NOT NULL (enum: REABERTURA_DOCUMENTO, RECUSA_DOCUMENTO (V5), ALTERACAO_DADOS_PEDIDO, OUTRO) |
| descricao | VARCHAR(500) | NOT NULL — motivo, sempre obrigatório |
| ocorrido_em | TIMESTAMP | NOT NULL |
| tipo_documento | VARCHAR(40) | NULL (V5) — só preenchido em `RECUSA_DOCUMENTO`; `NULL` pros demais tipos, que não se referem a um documento específico do checklist |
| envio_recusado_em | TIMESTAMP | NULL (V5) — só preenchido em `RECUSA_DOCUMENTO`: a data do `enviado_em` que foi recusado (o `ocorrido_em` da própria linha já é a data da recusa) |

Índice em `pedido_id`. Tabela genérica para eventos que geram
atraso/custo extra e precisam de motivo documentado — cobre hoje
reabertura de documento já aceito, recusa de documento (V5) e
alteração de consignee pós-embarque; não é histórico de todas as
mudanças do pedido (isso continua em `pedido_transicao` para estado).

> **Por que colunas novas em vez de só o `descricao` de texto livre
> (V5):** o PDF de status ao cliente (tarefa futura e separada) vai
> precisar renderizar, por documento, uma lista estruturada de
> recusas com data do envio recusado + data da recusa + motivo — não
> dá pra extrair isso de forma confiável de uma string livre. As duas
> colunas ficam `NULL` pros tipos de ocorrência que não se aplicam
> (mesmo padrão de coluna opcional já usado em `checklist_documento.descricao`,
> só preenchida em `DOCUMENTO_ADICIONAL`) — não foi necessário
> normalizar em tabela própria, a fricção real (o PDF) só pede esses
> dois campos extras.

### `pedido_sequencia` (V4)

| Campo | Tipo | Constraint |
|---|---|---|
| ano | INT | PK |
| proximo_numero | INT | NOT NULL |

Uma linha por ano, criada sob demanda (na primeira reserva de verdade
daquele ano, não antes). Contador dedicado pra sugestão/reserva
automática de `numero_pedido` — ver seção "Numeração automática do
pedido" abaixo.

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

## Regra de negócio — recusa de documento (V5)

- **Pré-condição: `enviado_em` preenchido e `aceito_em` nulo.**
  Documento não enviado → `DocumentoNaoEnviadoException` (409, reusa a
  mesma exceção de `aceitar()` — a pré-condição "documento não foi
  enviado ainda" é idêntica). Documento já aceito →
  `DocumentoJaAceitoException` (409, mesma exceção reusada de
  `enviar()`/`aceitar()` — recusar um documento aceito não faz
  sentido, a única forma de mudar um documento aceito continua sendo
  `reabrirAposAceite()`). Nenhuma exceção nova foi criada — as duas
  pré-condições já tinham exceção de domínio equivalente.
- **Efeito em `checklist_documento`:** `enviado_em` volta a `NULL`
  (documento pendente de novo). O motivo/data da recusa não ficam
  aqui — vivem só em `pedido_ocorrencia` (ver tabela acima), que nunca
  é sobrescrita por um reenvio posterior.
- **`recusar()` nunca transiciona `pedido.estado`.** Diferente de
  `reabrirAposAceite()` (que regride `DOCUMENTACAO_ACEITA` →
  `DOCUMENTACAO_ENVIADA` enquanto o pedido ainda não embarcou), a
  recusa não tem nenhuma regressão de estado — nem mesmo desfazer o
  `CRIADO` → `DOCUMENTACAO_ENVIADA` do primeiro envio, se o documento
  recusado for o único que já tinha sido enviado. Motivo: como
  `DOCUMENTACAO_ACEITA` só é alcançada quando **todos** os documentos
  têm `aceito_em` preenchido, um documento "enviado, não aceito"
  (pré-condição pra recusar) com o pedido já em `DOCUMENTACAO_ACEITA`
  ou além só existe em dois cenários — um `DOCUMENTO_ADICIONAL`
  enviado depois da aceitação geral, ou um documento reaberto
  (`reabrirAposAceite`) e reenviado depois do embarque. Em ambos, a
  mesma regra de `reabrirAposAceite()` pós-embarque se aplica ("não há
  como desfazer um navio que já saiu"), só que de forma ainda mais
  direta: a recusa nunca desfaz uma aceitação, então não há nada pra
  regredir. Decisão confirmada com o dono do domínio antes da
  implementação (não é uma omissão).
- **Reenvio após recusa reusa `enviar()` sem nenhuma mudança** — como
  `marcarEnviado()` já não tinha guarda contra reenviar um documento
  "enviado, não aceito" (só bloqueia contra documento já aceito), o
  fluxo enviar→recusar→reenviar já funcionava sem alteração; só
  precisou de teste confirmando (`FluxoPedidoE2ETest.recusarDocumentoEReenviarAtualizaDataDeEnvio`).
- **Motivo:** texto livre, `@NotBlank` + `@Size(max = 500)` no DTO
  (`RecusarDocumentoRequest`, mesmo formato de `ReabrirDocumentoRequest`),
  `trim()` aplicado no service antes de salvar a ocorrência.
- **Consulta cronológica de recusas por documento:** método
  `ChecklistService.buscarRecusas(numeroPedido, tipo)`, delega pra
  `PedidoOcorrenciaRepository.findByPedido_NumeroPedidoAndTipoAndTipoDocumentoOrderByOcorridoEmAsc(...)`.
  **Sem endpoint REST próprio ainda** — o único consumidor previsto
  hoje é o PDF de status (tarefa futura e separada), então expor uma
  rota agora seria antecipar uma necessidade que ainda não existe;
  decisão confirmada com o dono do domínio antes da implementação.

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
| PATCH | `/pedidos/{numeroPedido}/documentos/{tipo}/recusar` | Body `{ "motivo": "..." }` (V5). Exige `enviado_em` preenchido e `aceito_em` nulo (senão 409 `DOCUMENTO_NAO_ENVIADO`/`DOCUMENTO_JA_ACEITO`); zera `enviado_em` (documento volta a pendente), grava `pedido_ocorrencia` (tipo `RECUSA_DOCUMENTO`, com `tipoDocumento` + `envioRecusadoEm`); nunca transiciona `pedido.estado` — ver "Regra de negócio — recusa de documento" acima. `204 No Content`, mesmo padrão de `enviar`/`aceitar`/`reabrir` |
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

### Endpoints de F02/F03 (implementados)

| Método | Rota | Efeito |
|---|---|---|
| GET | `/pedidos` | Lista pedidos (`PedidoResponse[]`, mesmo DTO do GET individual — sem projeção resumida própria, ver justificativa abaixo). Query param opcional `?estado=` filtra por `PedidoEstado` (ex: `?estado=EMBARCADO`); omitido, retorna todos |
| GET | `/pedidos/{numeroPedido}/status.pdf` | Gera e devolve o PDF de status (F03) do pedido no estado atual. `Content-Type: application/pdf`. Sem autenticação, mesma decisão de escopo do resto da API nesta leva. 404 (`PEDIDO_NAO_ENCONTRADO`) se o pedido não existir |
| PATCH | `/pedidos/{numeroPedido}/logistica` | Atualiza `ciaMaritima` e/ou `numeroContainer`. Body: `AtualizarLogisticaRequest` (os dois campos opcionais, cada um só é alterado se vier preenchido — `null`/ausente deixa o valor atual como está). `200` com `PedidoResponse` atualizado. 404 se o pedido não existir |
| GET | `/pedidos/proximo-numero` | Devolve o próximo `numeroPedido` sugerido pro ano corrente (`ProximoNumeroResponse.numeroPedidoSugerido`, formato `NNNNN/AAAA`). Só espia o contador — não reserva, não incrementa nada (ver "Numeração automática do pedido" abaixo) |

**`GET /pedidos` reusa `PedidoResponse` sem criar um DTO de listagem
resumido.** Cada linha da tela de lista (F02) só exibe um subconjunto
dos campos (número, cliente, consignee, produto, ciaMaritima, estado,
atualizadoEm), mas criar um segundo DTO só pra isso seria otimização
prematura: o volume esperado é de uso por um único operador (ver
Audiência no PRD), não uma tabela com milhares de linhas exigindo
payload enxuto. Frontend simplesmente ignora os campos que não
mostra na tabela.

**Suporte no backend pra `GET /pedidos`:**
`PedidoRepository` ganhou `findByEstado(PedidoEstado estado)` (Spring
Data derivado, `findAll()` já vem de `JpaRepository`);
`PedidoService.listar(PedidoEstado estadoOuNull)` delega pra um ou
outro conforme o parâmetro seja nulo; `PedidoController.listar()`
recebe `@RequestParam(required = false) PedidoEstado estado`.

**`GET /pedidos/{numeroPedido}/status.pdf` (F03) — implementação:**
`PdfStatusService` (pacote `pedido.pdf`) monta o documento a partir
de `Pedido` + a lista de `PedidoTransicao` + o checklist
(`List<ChecklistDocumento>`) + as recusas por documento
(`Map<TipoDocumento, List<PedidoOcorrencia>>`) — os mesmos dados que
`PedidoResponse`/`PedidoTransicaoResponse`/`ChecklistDocumentoResponse`
já expõem, nenhum campo novo calculado ou persistido. Conteúdo do
PDF: dados do pedido (número, cliente, consignee, produto, incoterm)
+ tabela de progresso das 8 etapas do ciclo de vida (`CRIADO` → ... →
`ENTREGUE`, células até a atual destacadas com fundo verde claro;
`CANCELADO` é estado terminal fora dessa sequência, mostrado como
selo "PEDIDO CANCELADO" em vez de posição na barra) — inspirado em
rastreio de transportadora (DHL, Correios) — seguida da seção
"Documentos" (ver abaixo). `PedidoService`/`ChecklistService` continuam
sendo as únicas fontes desses dados; `PdfStatusService` continua puro
(sem repositório/service injetado), só recebe tudo pronto como
parâmetro.

`PedidoController.statusPdf()` compõe os quatro dados
(`pedidoService.buscarPorNumero/buscarHistorico/buscarChecklist` +
`checklistService.buscarRecusasPorDocumento`) antes de chamar
`pdfStatusService.gerar(...)`, devolvendo `ResponseEntity<byte[]>`
com `produces = MediaType.APPLICATION_PDF_VALUE`. Essa composição de
4 chamadas está inline no controller por enquanto (comentário no
código aponta isso) — quando a automação de e-mail (backlog v2)
existir, ela vai precisar exatamente da mesma composição antes de
chamar `gerar()`, e aí sim vale extrair pra um método reutilizável;
hoje só tem um chamador, extrair antes disso seria abstração sem
fricção real.

### Seção "Documentos" do PDF de status (recusa de documento)

Logo depois da barra de progresso, uma tabela com uma linha por item
do checklist — **ordem estável do enum `TipoDocumento`** (não a
ordem de retorno de `findByPedidoId()`, que não garante ordem),
colunas `Documento | Status | Último envio | Aceito em`:

- **Rótulos legíveis na coluna Documento** (não o nome do enum):
  "Invoice", "Packing list", "BL", "Certificado sanitário",
  "Documento adicional" (+ `" - " + descricao` quando houver). Método
  privado `PdfStatusService.rotulo(TipoDocumento)` — `switch`
  expression **sem `default`** de propósito: um `TipoDocumento` novo
  sem rótulo vira erro de compilação, não um documento sem nome
  legível silenciosamente no PDF que o cliente recebe.
- **Status calculado, sem coluna nova no banco:** "Aceito" se
  `aceitoEm != null`; senão "Enviado" se `enviadoEm != null`; senão
  "Recusado, aguardando reenvio" se existir pelo menos uma recusa
  registrada pra aquele documento; senão "Pendente".
- **Recusas:** logo abaixo da linha do documento (quando existirem),
  uma célula com `colspan` total da tabela (não uma coluna estreita —
  o motivo tem até 500 caracteres, escrito pro cliente ler, precisa
  de espaço pra quebrar linha direito) listando **todas** as recusas
  em ordem cronológica, mesmo depois de o documento ter sido
  reenviado e aceito (`pedido_ocorrencia` nunca é sobrescrita pelo
  reenvio — ver V5 na seção de modelo de dados): `"Recusado em
  dd/MM/yyyy HH:mm (envio de dd/MM/yyyy HH:mm): <motivo>"`.
- **Composição dos dados:** `ChecklistService.buscarRecusasPorDocumento(numeroPedido)`
  (novo) itera `TipoDocumento.values()` reaproveitando o
  `buscarRecusas()` já existente — só entram no mapa os tipos com
  pelo menos uma recusa. Nenhuma query nova.

> **Limitação encontrada: extração de texto do PDF não decodifica
> acentos/cedilha de fontes padrão não embutidas.** O
> `PdfTextExtractor` do próprio OpenPDF 3.0.5
> (`org.openpdf.text.pdf.parser`) transforma **qualquer** caractere
> acima de `0x7F` em WinAnsiEncoding (todo acento/cedilha do
> português: ã, ç, é, í, ó, ú, etc.) num `"?"` durante a extração,
> quando a fonte é um Type1 padrão (Helvetica, uma das 14 fontes base
> do PDF) não embutido, sem CMap `ToUnicode`. Confirmado que **o PDF
> em si está correto**: renderizando a página como imagem (PyMuPDF) o
> texto aparece com acentuação perfeita — é só a extração automatizada
> que falha, não a geração. Caracteres realmente fora do WinAnsi
> (emoji, cirílico, CJK) têm comportamento que varia com as fontes do
> sistema onde o PDF é gerado (podem ser omitidos, virar `"?"` ou, com
> fallback de fonte do SO, até renderizar) — não é uma garantia da
> biblioteca, por isso os testes não travam numa expectativa
> específica pra esses caracteres, só confirmam que a geração não
> quebra e que o resto do texto ao redor continua legível.
>
> Cheguei a adicionar o Apache PDFBox como dependência só de teste
> pra tentar extrair texto de forma mais confiável, mas comparei os
> dois extratores no mesmo PDF antes de decidir manter isso: **PDFBox
> tem exatamente a mesma limitação** (também vira `"?"` pra qualquer
> acento/cedilha, e também varia pra caracteres fora do WinAnsi) — ou
> seja, não resolvia nada que o `PdfTextExtractor` do próprio OpenPDF
> já não fizesse. Removida a dependência; os testes usam só o
> extrator que já é parte da stack de produção. Nenhuma mudança na
> fonte/encoding de `PdfStatusService` foi feita pra "corrigir" isso —
> embutir uma fonte Unicode de verdade (TrueType + Identity-H)
> resolveria a extração e ampliaria o alfabeto suportado, mas é uma
> mudança de escopo maior (bundle de arquivo de fonte) do que esta
> feature pediu; fica registrado como possível follow-up se virar
> fricção real.

> **Biblioteca real: OpenPDF `3.0.5`** (`com.github.librepdf:openpdf`,
> confirmada como a versão estável atual via `maven-metadata.xml`
> antes de fixar). O pacote das classes **não é** `com.lowagie.text.*`
> como esta Spec especulava antes da implementação — a partir da
> major version 2.x/3.x o OpenPDF renomeou o pacote pra
> `org.openpdf.text.*` (`org.openpdf.text.Document`,
> `org.openpdf.text.pdf.PdfWriter`, `org.openpdf.text.pdf.PdfPTable`
> etc.), embora `com.lowagie.text` ainda apareça na documentação
> antiga do projeto. Confirmado inspecionando o jar antes de escrever
> qualquer linha de código — o padrão desta stack de checar antes de
> assumir (Flyway, `@DataJpaTest`, `@WebMvcTest`/Jackson 3, Groovy do
> REST Assured) valeu de novo. Licença permanece LGPL/MPL (dual),
> motivo original de escolher OpenPDF em vez do iText moderno (AGPL)
> continua válido.
>
> Retornar `byte[]` de um `@RestController` não tem nada de peculiar
> no Boot 4.1: `MediaType.APPLICATION_PDF`/`APPLICATION_PDF_VALUE` e
> `ByteArrayHttpMessageConverter` continuam em `spring-web` sem
> mudança de pacote — a migração pra Jackson 3 (Fase 4) afeta só a
> conversão JSON, `byte[]` passa por um converter totalmente
> separado. Único gotcha real de stack nesta peça foi o pacote do
> OpenPDF, não o Spring.

**`PATCH /pedidos/{numeroPedido}/logistica` — por que é um endpoint
separado, sem regra de transição de estado nenhuma:** diferente do
resto do fluxo (documentação, pagamentos, embarque), `ciaMaritima` e
`numeroContainer` não chegam num momento fixo do ciclo de vida. A
companhia marítima é confirmada no booking; o número do container
com lacre só existe depois, quando a carga é de fato alocada — é o
BL que formaliza isso, e o BL pode sair em qualquer estado a partir
de `DOCUMENTACAO_ENVIADA`. Não existe um "estado" pra essa
informação chegar, só a necessidade de poder preenchê-la quando
chegar — por isso não é um destino de `/transicionar` nem um gatilho
de transição automática, é metadado que pode ser atualizado a
qualquer momento do ciclo de vida (inclusive antes de `EMBARCADO`,
se o booking já veio com essas informações).

**Sem `PedidoOcorrencia` aqui.** O registro de ocorrências existe pra
eventos que geram atraso/custo e precisam de motivo documentado
(reabertura de documento, alteração de consignee pós-embarque) — são
correções ou exceções ao fluxo esperado. Preencher companhia
marítima e container quando a informação chega é o contrário disso:
é progressão normal do dado logístico, não uma correção. Gravar
ocorrência aqui trataria o caso comum como se fosse exceção.

**Sem setter público — mesmo padrão de `aplicarConsignee`.** `Pedido`
ganha um método pacote-privado (`aplicarDadosLogisticos(String
ciaMaritima, String numeroContainer)`, cada parâmetro só aplicado se
não-nulo) chamado só por `PedidoService`. Isso implica remover os
setters públicos `setCiaMaritima`/`setNumeroContainer` que existem na
entidade desde a Fase 1 — hoje o único caso remanescente de mutação
pública em `Pedido`, uma inconsistência com o padrão adotado a partir
da Fase 3 (`aplicarTransicao`, `aplicarConsignee`, ambos
pacote-privados). Fica registrado como parte da implementação deste
endpoint, não uma limpeza à parte.

**Numeração automática do pedido (`GET /pedidos/proximo-numero`).**
`numeroPedido` segue opcionalmente o padrão `NNNNN/AAAA` (5 dígitos com
zero à esquerda, ano de 4 dígitos) — sugerido automaticamente pelo
frontend na tela "Criar pedido" (campo continua editável, usuário pode
digitar qualquer outro valor manualmente).

- **Tabela dedicada (`pedido_sequencia`, V4), não uma coluna calculada
  em cima de `MAX(numero_pedido)`.** Ainda que hoje o sistema seja de
  usuário único (sem concorrência real observada), `numero_pedido` é a
  chave de negócio do domínio inteiro (rota principal da API, chave
  única no banco) — um contador que pode colidir ou pular número não é
  um detalhe cosmético aqui, é o tipo de robustez que vale desde o
  início, não uma otimização prematura.
- **`GET /pedidos/proximo-numero` só espia o contador (`SELECT`), nunca
  reserva nem incrementa.** Se o usuário abre o formulário de criação e
  desiste sem enviar, nenhum número fica pulado.
- **O incremento de verdade só acontece dentro de `PedidoService.criar()`**,
  via `PedidoSequenciaService.reservarSeCorresponder(numeroPedido)`,
  atômico com a criação do pedido (mesma transação — se a criação falhar,
  o incremento também é desfeito). A lógica: extrai `numero`/`ano` do
  `numeroPedido` recém-criado (regex `NNNNN/AAAA`); se não bater no
  padrão (número manual, formato livre), não mexe em nada; se bater,
  faz um `UPDATE pedido_sequencia SET proximo_numero = proximo_numero + 1
  WHERE ano = :ano AND proximo_numero = :numero` — um CAS (compare-and-swap)
  atômico que só avança o contador se o valor atual for exatamente o
  número sendo usado. Consequência direta, sem regra extra nenhuma:
  **se o usuário editar a sugestão pra outro valor, o contador não
  avança** (o `UPDATE` não encontra a linha com aquele `proximo_numero`
  e afeta zero registros); só avança quando o número sugerido é de fato
  o que foi criado.
- **Reset por ano é automático, não um job/cron.** A chave da tabela é
  o próprio `ano` extraído do `numeroPedido` (não necessariamente "o
  ano corrente do servidor") — cada ano tem sua própria linha/contador,
  criada sob demanda (`INSERT ... ON CONFLICT (ano) DO NOTHING`) na
  primeira reserva daquele ano. Isso também cobre virada de ano sem
  código dedicado: `sugerirProximoNumero()` sempre calcula pro
  `Year.now()`, então em 1º/jan a sugestão já volta a ser `00001/<ano
  novo>` porque não existe linha pra esse ano ainda.
- **Concorrência:** o `UPDATE` com `WHERE proximo_numero = :numero` é a
  proteção — duas transações tentando reservar o mesmo número
  concorrentemente serializam no lock de linha do Postgres; a que
  perder a corrida simplesmente não encontra mais o valor esperado
  (já foi incrementado pela outra) e não afeta nenhuma linha, sem
  exception, sem retry manual. Coberto por
  `PedidoSequenciaServiceTest.duasReservasSimultaneasParaOMesmoNumeroSoUmaAvancaOContador`.

**Bug de navegação corrigido junto (pré-existente, não introduzido por
esta feature, mas que a numeração automática tornaria trivial de
disparar):** `numeroPedido` contendo `/` (exatamente o padrão
`NNNNN/AAAA` acima) quebrava a navegação do frontend — `Link
to={`/pedidos/${numeroPedido}`}` e `navigate(`/pedidos/${numeroPedido}`)`
montavam a URL sem codificar a barra, então `/pedidos/00001/2026` virava
dois segmentos de rota (`numeroPedido` + um segmento extra) em vez de
um só, e a rota `/pedidos/:numeroPedido` nunca casava. Corrigido em
`ListaPedidos.tsx` (link da lista) e `CriarPedido.tsx` (redirect
pós-criação) com `encodeURIComponent(numeroPedido)` — `client.ts` já
fazia isso em toda chamada de API (`buscarPedido`, `transicionar` etc.),
só os dois pontos de navegação client-side (React Router) estavam sem.

Só o frontend não bastava: o Tomcat embarcado rejeita por padrão uma
barra codificada (`%2F`) na URL com `400 Bad Request` (proteção
genérica contra path traversal, sem relação com este domínio — aqui
`numeroPedido` nunca vira caminho de arquivo). Confirmado testando
`GET /pedidos/00001%2F2026` manualmente antes de mexer no backend: sem
o ajuste, a chamada de API que o frontend já corrigido faria também
quebraria, só que no servidor em vez do router do cliente.
`WebConfig` ganhou um `WebServerFactoryCustomizer<TomcatServletWebServerFactory>`
que seta `encodedSolidusHandling=passthrough` no connector: o Tomcat
repassa a URL codificada pro dispatcher do Spring sem decodificar
antes, e o Spring casa a rota pelos segmentos originais (`%2F` continua
sendo "um caractere dentro do segmento", não um separador) — só decodifica
o valor de cada `@PathVariable` depois de já ter casado o segmento, daí
`numeroPedido` chega em `"00001/2026"` inteiro no controller. Confirmado
manualmente com `curl` (`GET`, incluindo a rota aninhada `/historico`)
antes e depois do ajuste — 400 sem o customizer, 200 com ele.

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
| `DocumentoJaAceitoException` | 409 | `DOCUMENTO_JA_ACEITO` — reusada por `enviar()`, `aceitar()` e `recusar()` (V5) |
| `DocumentoNaoEnviadoException` | 409 | `DOCUMENTO_NAO_ENVIADO` — reusada por `aceitar()` e `recusar()` (V5) |
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

Ao montar a tela, chama `GET /pedidos/proximo-numero` e pré-preenche
`numeroPedido` com a sugestão (`NNNNN/AAAA` do ano corrente) — campo
continua editável normalmente, falha na chamada não impede o cadastro
(usuário preenche manualmente).

Submit: `POST /pedidos` com o corpo montado no formato aninhado que
`CriarPedidoRequest` já exige (objeto `condicoesComerciais`, não os 5
campos soltos). Sucesso (`201`) navega pra `/pedidos/{numeroPedido}`
(o `numeroPedido` retornado no `PedidoResponse` da resposta,
codificado com `encodeURIComponent` — ver "Numeração automática do
pedido" acima pra por quê).

`ciaMaritima` e `numeroContainer` **não fazem parte deste
formulário** — não têm um momento fixo do ciclo de vida (companhia
sai no booking, container só depois da carga alocada), então não
faz sentido pedi-los na criação do pedido. Preenchidos depois, na
tela de detalhe, via `PATCH /pedidos/{numeroPedido}/logistica` (ver
seção de endpoints acima) — não pelo formulário de criação nem por
`CriarPedidoRequest`.

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
| Preencher/atualizar companhia marítima e/ou container | `PATCH /pedidos/{numero}/logistica` — sem regra de estado, disponível em qualquer momento do ciclo de vida (ver seção de endpoints acima) |
| Gerar PDF de status | `GET /pedidos/{numero}/status.pdf` — abre em nova aba (`target="_blank"`) ou dispara download; não é uma chamada `fetch` que precise de tratamento de JSON, é navegação direta pra uma URL que devolve `application/pdf` |

Toda ação que muda estado (enviar/aceitar/reabrir/transicionar/
pagamentos) recarrega `GET /pedidos/{numero}` e `GET
/pedidos/{numero}/historico` depois de um `2xx` — não há
atualização otimista de estado no cliente, o backend é a fonte da
verdade e as chamadas são baratas o suficiente pra um único operador.
Atualizar dados logísticos não muda estado (não gera transição), mas
recarrega `GET /pedidos/{numero}` do mesmo jeito — é a única fonte
dos valores atualizados de `ciaMaritima`/`numeroContainer`.

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
| Recusar documento (enviado, não aceito) volta `enviado_em` a nulo e grava `pedido_ocorrencia` com motivo/tipoDocumento/envioRecusadoEm; nunca transiciona `pedido.estado` (mesmo com pedido já embarcado) | Unitário | `ChecklistServiceTest.recusarDocumentoEnviadoVoltaParaPendenteEGravaOcorrencia` + `recusarNaoAlteraEstadoDoPedidoMesmoComPedidoJaEmbarcado` + `recusarAplicaTrimNoMotivoAntesDeSalvar` |
| Recusar documento não enviado ou já aceito rejeita (409); recusar duas vezes mantém as duas ocorrências no histórico, em ordem, sem apagar a anterior | Unitário | `ChecklistServiceTest.naoPermiteRecusarDocumentoNaoEnviado` + `naoPermiteRecusarDocumentoJaAceito` + `recusarDuasVezesGravaDuasOcorrenciasDistintasSemApagarAAnterior` |
| `PATCH /recusar` retorna 204 no caminho válido; 409 se não enviado/já aceito; 400 com motivo vazio, só espaços ou maior que 500 caracteres | API | `ChecklistControllerTest.recusarRetorna204` + `recusarDocumentoNaoEnviadoRetorna409` + `recusarDocumentoJaAceitoRetorna409` + `recusarSemMotivoRetorna400` + `recusarComMotivoSoEspacosRetorna400` + `recusarComMotivoMaiorQue500CaracteresRetorna400` |
| Consulta cronológica de recusas por documento filtra por tipo/tipoDocumento e ordena por `ocorrido_em`, mesmo com outros tipos de ocorrência e outros documentos no meio | Repositório (Postgres real) | `PedidoOcorrenciaRepositoryTest.buscaRecusasDeUmDocumentoEmOrdemCronologicaIgnorandoOutrosTiposEDocumentos` + `semRecusasRegistradasRetornaListaVazia` |
| Reenvio após recusa grava uma nova data em `enviado_em` (fluxo enviar→recusar→reenviar completo); recusar documento já aceito retorna 409 na API real | E2E | `FluxoPedidoE2ETest.recusarDocumentoEReenviarAtualizaDataDeEnvio` + `recusarDocumentoJaAceitoRetorna409NaAPIReal` |
| Documento inexistente (tipo sem registro de checklist) retorna 404; `{tipo}` inválido na rota retorna 400 | API | `ChecklistControllerTest.enviarDocumentoInexistenteRetorna404` + `tipoDocumentoInvalidoNaRotaRetorna400` |
| Pagamento parcial/saldo fora de sequência retorna 409; válido retorna 200 | API | `PedidoControllerTest.confirmarPagamentoParcialRetorna200` + `confirmarPagamentoParcialForaDeSequenciaRetorna409` + `confirmarPagamentoSaldoRetorna200` + `confirmarPagamentoSaldoSemEstarEmbarcadoRetorna409` |
| Histórico vazio retorna lista vazia; com transições retorna ordenado; pedido inexistente retorna 404 | API | `PedidoControllerTest.historicoVazioRetorna200ComListaVazia` + `historicoComTransicoesRetornaListaOrdenada` + `historicoDePedidoInexistenteRetorna404` |
| Fluxo completo criado→entregue (documentação, pagamentos, embarque, entrega) — valida cada estado retornado e o histórico completo ao final | E2E | `FluxoPedidoE2ETest.fluxoCompletoCriadoAteEntregue` |
| Fluxo com cancelamento antes do embarque — transição após `CANCELADO` rejeitada com 409 | E2E | `FluxoPedidoE2ETest.cancelamentoAntesDoEmbarqueImpedeQualquerTransicaoDepois` |
| Pagamento-saldo fora de sequência retorna 409 na API real (não só no nível de Service) | E2E | `FluxoPedidoE2ETest.pagamentoSaldoForaDeSequenciaRetorna409NaAPIReal` |
| `GET /pedidos` lista todos os pedidos sem filtro; `?estado=` filtra corretamente | Unitário + API | `PedidoServiceTest.listarSemFiltroRetornaTodosOsPedidos` + `listarComFiltroDeEstadoRetornaSoOsQueBatem` + `PedidoControllerTest.listarSemFiltroRetorna200ComTodosOsPedidos` + `listarComFiltroEstadoRetorna200SoComOsFiltrados` |
| `GET /pedidos/{numero}/status.pdf` retorna 200 com `Content-Type: application/pdf`; pedido inexistente retorna 404 | API | `PedidoControllerTest.gerarPdfStatusRetorna200ComContentTypePdf` + `gerarPdfStatusDePedidoInexistenteRetorna404` |
| Seção "Documentos" do PDF: status calculado corretamente (Pendente/Enviado/Aceito/Recusado aguardando reenvio) com as datas certas | Unitário (PDF real, extração via `PdfTextExtractor` do OpenPDF) | `PdfStatusServiceTest.documentoPendenteMostraStatusPendenteComTravessoesNasDatas` + `documentoEnviadoMostraStatusEnviadoComData` + `documentoAceitoMostraStatusAceitoComData` + `documentoRecusadoAguardandoReenvioMostraStatusMotivoEAsDuasDatas` |
| Recusa continua listada após reenvio/aceite; duas recusas do mesmo documento aparecem na ordem da lista; documentos ordenados pela ordem do enum, não da lista recebida | Unitário (PDF real) | `PdfStatusServiceTest.documentoRecusadoReenviadoEAceitoMantemARecusaNoHistorico` + `duasRecusasDoMesmoDocumentoAparecemNaOrdemDaLista` + `documentosSaoOrdenadosPelaOrdemDoEnumNaoPelaOrdemDaListaRecebida` |
| Coluna Documento usa rótulos legíveis (não o nome do enum), documento adicional mostra a descrição | Unitário (PDF real) | `PdfStatusServiceTest.colunaDocumentoUsaRotulosLegiveisEmVezDoNomeDoEnum` |
| Motivo de 500 caracteres não lança exceção e não é truncado; motivo com acentos/cedilha não quebra a geração (extração garante só o texto ASCII ao redor — ver limitação documentada acima); motivo com emoji/caractere não-latino não lança exceção e o resto do motivo continua legível | Unitário (PDF real) | `PdfStatusServiceTest.motivoComQuinhentosCaracteresNaoLancaExcecaoENaoTemOFinalTruncado` + `motivoComAcentosECedilhaNaoLancaExcecaoEMantemTextoAoRedorLegivel` + `motivoComEmojiECaracterNaoLatinoNaoLancaExcecaoEMantemRestoDoMotivoLegivel` |
| Fluxo real enviar→recusar→GET status.pdf (motivo aparece) →reenviar→GET status.pdf de novo (status "Enviado", recusa antiga ainda listada) | E2E | `FluxoPedidoE2ETest.statusPdfMostraMotivoDaRecusaEDepoisOStatusEnviadoComARecusaAindaListada` |
| `PATCH /pedidos/{numero}/logistica` atualiza só `ciaMaritima`, só `numeroContainer`, ou os dois juntos; não gera `PedidoOcorrencia`; sem regra de estado (funciona em qualquer estado); pedido inexistente retorna 404 | Unitário + API | `PedidoServiceTest.atualizarDadosLogisticosComOsDoisCamposAtualizaAmbos` + `atualizarDadosLogisticosComSoCiaMaritimaNaoMexeNoContainer` + `atualizarDadosLogisticosComSoContainerNaoMexeNaCiaMaritima` + `PedidoControllerTest.atualizarLogisticaComSoCiaMaritimaAtualizaSoEsseCampo` + `atualizarLogisticaComSoNumeroContainerAtualizaSoEsseCampo` + `atualizarLogisticaComOsDoisCamposAtualizaAmbos` + `atualizarLogisticaDePedidoInexistenteRetorna404` |
| Listagem e atualização de dados logísticos no meio do fluxo real; PDF de status gerado de verdade (OpenPDF) no final do fluxo completo | E2E | `FluxoPedidoE2ETest.fluxoCompletoCriadoAteEntregue` (logística + `?estado=` + `status.pdf`) + `listarSemFiltroIncluiPedidoRecemCriadoEComFiltroDeEstadoSoOsQueBatem` |
| `GET /pedidos/proximo-numero` sugere `00001/<ano>` sem histórico e não cria/altera o contador | API + Integração | `PedidoControllerTest.proximoNumeroRetorna200ComSugestaoDoService` + `PedidoSequenciaServiceTest.sugerirProximoNumeroSemHistoricoRetorna00001ParaAnoAtual` + `sugerirProximoNumeroApenasEspiaNaoCriaNemAlteraOContador` |
| `reservarSeCorresponder()` só avança o contador quando o número criado bate com o sugerido; número manual ou fora de sequência não avança | Unitário + Integração | `PedidoServiceTest.criarGeraChecklistZeradoETransicaoInicial` (verifica a chamada) + `PedidoSequenciaServiceTest.reservarSeCorresponderAvancaContadorQuandoNumeroBateComOSugerido` + `reservarSeCorresponderComNumeroManualForaDoPadraoNaoCriaSequencia` + `reservarSeCorresponderComNumeroDiferenteDoAtualNaoAvancaOContador` |
| Sequência reseta por ano (anos independentes); duas reservas concorrentes pro mesmo número não colidem (só uma avança) | Integração (Postgres real) | `PedidoSequenciaServiceTest.sequenciaResetaPorAnoDoisAnosAvancamIndependentemente` + `duasReservasSimultaneasParaOMesmoNumeroSoUmaAvancaOContador` |
| Número de pedido com barra (`NNNNN/AAAA`) navega corretamente (link da lista, redirect pós-criação, chamada de API) | Frontend (Vitest) + manual (`curl`) | `navegacaoNumeroPedido.test.ts` (encode/decode de um único segmento de rota) — confirmado manualmente com `curl` que `GET /pedidos/00001%2F2026` (e `/historico`) retorna 200 após o `WebConfig`/Tomcat `encodedSolidusHandling=passthrough` |

## Fora de escopo desta Spec

Módulo de IA (triagem de testes), pipeline de CI, e autenticação —
não fazem parte de F01/F02/F03, tratados em specs/tickets separados
quando estiverem implementadas e testadas. Autenticação, especificamente,
segue fora de escopo pras três features desta leva (F01, F02, F03) —
mesma decisão registrada no PRD.
