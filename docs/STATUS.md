# Status — TrackCargo

## Última atualização
18/set/2026 — Scaffold do frontend F02 (Vite + React + TypeScript +
Tailwind v4 + React Router), branch `feat/f02-frontend-scaffold`

## Onde paramos (scaffold do frontend F02)

`frontend/` criado com `npm create vite@latest frontend -- --template
react-ts`, irmão do backend Java. Sem polish visual fino ainda — o
objetivo desta fase era ter as 3 telas consumindo a API real, não dado
inventado.

- **Stack**: Vite 8, React 19, TypeScript 6, Tailwind CSS v4 (via
  plugin `@tailwindcss/vite` — a v4 não usa mais
  `tailwind.config.js`/PostCSS, é `@import "tailwindcss";` +
  `@theme { }` no CSS), React Router v8 (`react-router` puro — não
  `react-router-dom`, que ficou pra trás na v7; `BrowserRouter` agora
  sai do pacote principal).
- **Cliente HTTP** (`src/api/client.ts`): fetch nativo, uma função por
  endpoint existente (`listarPedidos`, `buscarPedido`, `criarPedido`,
  `transicionar`, `confirmarPagamentoParcial/Saldo`,
  `enviarDocumento/aceitarDocumento/reabrirDocumento`,
  `buscarHistorico`, `atualizarLogistica`, `urlStatusPdf`). Base URL
  via `VITE_API_BASE_URL` (default `http://localhost:8080`).
- **Tipos** (`src/api/types.ts`): interfaces espelhando os DTOs Java
  (`PedidoResponse`, `ChecklistDocumentoResponse`,
  `PedidoTransicaoResponse`, `ErrorResponse` — conferido campo a campo
  no `ErrorResponse.java` real, que é `erro/mensagem/estadoAtual/
  estadoSolicitado`, não o que se poderia supor por convenção) e os 4
  enums (`PedidoEstado`, `TipoDocumento`, `Incoterm`,
  `FormaPagamento`), incluindo o mapa `TRANSICOES_MANUAIS` copiado do
  `PedidoEstado` do backend pra habilitar só os botões de transição
  válidos em cada estado.
- **3 telas**: Lista (`/`, filtro por estado), Criar (`/pedidos/novo`,
  formulário agrupado em Identificação/Descrição da
  mercadoria/Condições comerciais), Detalhe (`/pedidos/:numeroPedido`,
  dados + logística editável + checklist com enviar/aceitar/reabrir +
  botões de transição dinâmicos por `TRANSICOES_MANUAIS` +
  pagamento-parcial/saldo condicionais ao estado + link de PDF +
  histórico). Sem mock — tudo vem da API.
- **CORS**: `WebConfig` novo (`config/WebConfig.java`,
  `WebMvcConfigurer` — sem Spring Security no projeto, então foi o
  caminho mais simples) liberando `http://localhost:5173`.
- **CI**: novo job `frontend` em `.github/workflows/ci.yml`, paralelo
  ao `test` do Java — `npm ci && npm run build`
  (`working-directory: frontend`, cache do `npm` via
  `package-lock.json`).
- `npm run build` (tsc -b + vite build) confirmado passando local antes
  do PR. A evidência real desta fase é o CI verde no PR, não o build
  local — sem ambiente de teste sempre disponível agora.

## Onde paramos (backend de F02/F03)

Implementados os 3 endpoints que o SPEC.md já especificava (PR #16,
mergeado) — nenhuma linha de React ainda, só backend:

- **`GET /pedidos`** (novo — antes só existia `GET /pedidos/{numero}`),
  filtro opcional `?estado=`. `PedidoRepository.findByEstado()` novo;
  `PedidoService.listar(PedidoEstado estadoOuNull)` delega pra
  `findAll()` ou `findByEstado()`. Reusa `PedidoResponse`, sem DTO de
  listagem próprio (decisão já registrada no SPEC.md).
- **`PATCH /pedidos/{numero}/logistica`** — atualiza `ciaMaritima`
  e/ou `numeroContainer`, PATCH parcial (cada campo só muda se vier
  preenchido). `Pedido.aplicarDadosLogisticos()` pacote-privado
  substituiu `setCiaMaritima`/`setNumeroContainer`, os únicos
  setters públicos remanescentes na entidade desde a Fase 1 — não
  existem mais. Sem `PedidoOcorrencia` (progressão normal, não
  correção). Sem regra de transição de estado (funciona em qualquer
  estado do ciclo de vida).
- **`GET /pedidos/{numero}/status.pdf`** — `PdfStatusService` novo
  (pacote `pedido.pdf`), gera o PDF via OpenPDF a partir de `Pedido`
  + histórico: dados do pedido + tabela de progresso das 8 etapas do
  ciclo de vida com a atual destacada (`CANCELADO` vira selo à parte).
  Controller devolve `ResponseEntity<byte[]>` com `produces =
  MediaType.APPLICATION_PDF_VALUE`. Mesmo método (`gerar(pedido,
  historico)`) que a futura automação de e-mail do backlog v2 vai
  chamar direto, sem HTTP.

**Descoberta de stack (5ª desta linha, depois de Flyway,
`@DataJpaTest`, `@WebMvcTest`/Jackson 3, e Groovy do REST Assured):**
OpenPDF mudou de pacote entre versões — `com.lowagie.text.*` (nome
herdado do iText 2.x, o que o SPEC.md especulava antes da
implementação) só existe até a série 1.3.x; a partir da 2.x/3.x é
`org.openpdf.text.*`. Usamos a **3.0.5**, confirmada como a versão
estável atual via `maven-metadata.xml` do Maven Central antes de
fixar no `pom.xml` — não a mais recente encontrada só resolvendo
`dependency:get`, que teria deixado passar `1.3.42`/`2.0.x` sem
avisar que não eram as mais novas. Retornar `byte[]` de um
`@RestController` não teve nenhuma pegadinha própria do Boot 4.1 —
`MediaType.APPLICATION_PDF`/`ByteArrayHttpMessageConverter` seguem
sem mudança de pacote em `spring-web`, a migração pra Jackson 3 afeta
só a conversão JSON.

**Testes novos:**
- `PedidoServiceTest` (+5): `listarSemFiltroRetornaTodosOsPedidos`,
  `listarComFiltroDeEstadoRetornaSoOsQueBatem`,
  `atualizarDadosLogisticosComOsDoisCamposAtualizaAmbos`,
  `atualizarDadosLogisticosComSoCiaMaritimaNaoMexeNoContainer`,
  `atualizarDadosLogisticosComSoContainerNaoMexeNaCiaMaritima`.
- `PedidoControllerTest` (+8): listagem com/sem filtro (2), logística
  com os 3 casos + 404 (4), PDF com 200/content-type e 404 (2).
- `FluxoPedidoE2ETest` (+1 cenário novo, e o cenário
  `fluxoCompletoCriadoAteEntregue` ganhou passos extras): dados
  logísticos preenchidos depois do embarque, `GET /pedidos?estado=`
  confirmando o filtro, e `GET /pedidos/{numero}/status.pdf` gerando
  um PDF de verdade (não mockado) no fim do fluxo completo — mais um
  cenário dedicado só de listagem (`listarSemFiltroInclui...`), sem
  duplicar o setup do fluxo principal.

`docs/SPEC.md` atualizado: as 3 linhas da tabela de correlação que
estavam "Planejado" agora apontam pros testes reais; seção de
endpoints de F02/F03 marcada como implementada; nota sobre o pacote
real do OpenPDF corrigida (era só especulação antes da
implementação).

## Resultado da suíte completa (mvn test) — 2 rodadas
**59/59 verde nas duas rodadas** (6 `ChecklistServiceTest` + 3
`PedidoRepositoryTest` + 15 `PedidoServiceTest` + 9
`ChecklistControllerTest` + 22 `PedidoControllerTest` + 4
`FluxoPedidoE2ETest`):
- Rodada 1: suíte completa normal.
- Rodada 2: banco recriado do zero (`DROP DATABASE` + `CREATE
  DATABASE`), forçando o Flyway a reaplicar V1→V2→V3.

Nota de ambiente (mantida de fases anteriores): `docker compose`
continua não testado nesta sessão pela mesma restrição de rede do
sandbox já registrada — Postgres 16 nativo usado no lugar, mesmas
credenciais/porta do `application.yml`.

## Estado de saída (backend de F02/F03)
Fechado: os 3 endpoints implementados e testados, suíte completa
59/59 em duas rodadas, `docs/SPEC.md` e `docs/STATUS.md` atualizados.
PR aberto, aguardando revisão/merge. **Nenhum código React ainda** —
isso é só backend, como pedido. Não avanço pro frontend sem
confirmação.

---

## Onde paramos (Fase 6 — CI, última fase do PLAN.md)

`.github/workflows/ci.yml`, disparado em `push` e `pull_request` pra
`main`. Job único (`ubuntu-latest`) com Postgres 16 como serviço
(mesmas credenciais do `docker-compose.yml`, `health-cmd pg_isready`),
Java 21 via `actions/setup-java@v4` (Temurin, cache maven nativo), e
`mvn --batch-mode --no-transfer-progress test` — a suíte completa
(unitário + API + E2E com REST Assured) contra esse Postgres real, sem
mock na camada de banco. Confirmado que não há `testFailureIgnore`/
`skipTests`/`maven.test.skip` no `pom.xml` — comportamento padrão do
Maven/Surefire de falhar o build em qualquer teste quebrado se
mantém.

**Evidência real (não só `mvn test` local)**: o PR #14 disparou o
workflow de verdade — [run
#35330808183](https://github.com/EversonRubira/TrackCargo/actions/runs/35330808183),
`status: completed`, `conclusion: success`, todos os steps verdes
(subida do container do Postgres incluída). Log do job confirma:
`Tests run: 45, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`,
27.8s de build. As linhas `role "root" does not exist` no log do
container Postgres são as tentativas de conexão do healthcheck antes
do banco aceitar conexões (esperado, não é erro); a linha `duplicate
key value violates unique constraint` é o próprio
`PedidoRepositoryTest.naoDevePermitirDoisPedidosComMesmoNumero`
validando a constraint de unicidade — comportamento esperado do
teste, não uma falha.

No pull do `postgres:16` a política de rede do GitHub Actions não
tem a mesma restrição que bloqueava o sandbox local nas fases
anteriores — o container subiu normalmente como serviço nativo do
runner, sem precisar do `docker-compose.yml` em si.

Fora de escopo (não pedido em nenhuma fase até agora): badge no
README, deploy, build de imagem Docker, publicação de artefato.

## Estado de saída da Fase 6 — e do PLAN.md
Fechada: workflow criado, PR aberto, CI rodou de verdade no PR e
passou (45/45). Esta é a última fase do PLAN.md — as 6 fases da F01
(Scaffold → Entity/Migration → Repository → Service → Controller →
E2E → CI) estão implementadas e com suíte verde. PR aberto, aguardando
revisão/merge. Não avanço além da Fase 6 sem confirmação — não há
próxima fase no PLAN.md além desta; qualquer trabalho futuro (módulo
de IA, backlog v2) exigiria reabrir o PRD/SPEC antes.

---

## Onde paramos (Fase 5 — E2E)

Ajuste de escopo decidido com o dono do domínio antes de começar:
**REST Assured no lugar de Playwright**, porque hoje não existe UI
nenhuma (só API REST) — Playwright automatiza browser, e sem página
pra abrir ele não testaria nada além do que uma chamada HTTP direta
já cobre. Fica reservado pra quando o frontend (React, backlog v2)
existir de verdade. Documentado em `docs/PLAN.md` (Fase 5) e
`docs/SPEC.md` (seção de decisões de design) antes de qualquer linha
de código.

`FluxoPedidoE2ETest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`,
contra o Postgres real — nenhum mock nessa camada) com 3 cenários:
1. `fluxoCompletoCriadoAteEntregue`: cria pedido, envia+aceita os 4
   documentos do checklist, pagamento parcial, embarca, pagamento de
   saldo, documentos originais, entrega — valida o `estado` retornado
   em cada passo e confere `GET /historico` no final batendo com a
   sequência completa em ordem.
2. `cancelamentoAntesDoEmbarqueImpedeQualquerTransicaoDepois`: cria,
   cancela em `CRIADO`, confirma 409 em qualquer transição tentada
   depois.
3. `pagamentoSaldoForaDeSequenciaRetorna409NaAPIReal`: pagamento-saldo
   sem estar `EMBARCADO`, confirmando que o `GlobalExceptionHandler`
   devolve 409 na ponta real da API (não só testado no nível de
   Service, que já era coberto desde a Fase 4).

**2 pegadinhas de stack encontradas com REST Assured 5.5.6 (nenhuma é
bug do REST Assured, são dois efeitos colaterais específicos desta
stack — detalhes técnicos completos no SPEC.md):**
1. Ele exige Jackson 2/Gson/Johnzon/Yasson no classpath pra
   serializar `.body(pojo)`/`.body(map)`; o projeto está em Jackson 3
   e ele não reconhece — `IllegalStateException: Cannot serialize
   object`. Contornado mandando o corpo como `String` (JSON literal
   via text block), sem precisar de mais nenhuma dependência.
2. `spring-boot-dependencies` força Groovy 5.0.8 (via import de
   `groovy-bom`), mas REST Assured 5.5.x foi construído contra Groovy
   4.0.22 — o MOP do Groovy 5 quebra dentro do REST Assured
   (`NullPointerException` em `ClosureMetaClass` especificamente em
   requests `PATCH`). Corrigido com `<dependencyManagement>` explícito
   no `pom.xml` re-fixando `org.apache.groovy:*` em `4.0.22` — só um
   override de propriedade não bastava porque o `groovy-bom`
   importado já vem com a versão interpolada.

`docs/SPEC.md` e `docs/PLAN.md` atualizados com a decisão de escopo,
as duas pegadinhas de stack, os endpoints/cenários E2E na tabela de
correlação, e a nota de que `@SpringBootTest`/`@LocalServerPort`
continuam em `spring-boot-test` (não sofreram o split de
`@DataJpaTest`/`@WebMvcTest`).

## Resultado da suíte completa (mvn test) — 2 rodadas
**45/45 verde nas duas rodadas** (6 `ChecklistServiceTest` + 3
`PedidoRepositoryTest` + 10 `PedidoServiceTest` + 9
`ChecklistControllerTest` + 14 `PedidoControllerTest` + 3 novos
`FluxoPedidoE2ETest`):
- Rodada 1: suíte completa normal.
- Rodada 2: banco recriado do zero (`DROP DATABASE` + `CREATE
  DATABASE`) antes, forçando o Flyway a reaplicar V1→V2→V3 —
  equivalente a `docker compose down -v && up -d` limpo.

Nota de ambiente (mantida de fases anteriores, confirmada de novo
nesta sessão): `docker compose up` continua bloqueado pela política
de rede do sandbox — pull de `postgres:16` retorna 403 no blob do
registry, não é intermitente. Usei Postgres 16 nativo com as mesmas
credenciais/porta do `application.yml`.

## Estado de saída da Fase 5
Fechada: `FluxoPedidoE2ETest` com os 3 cenários pedidos, suíte
completa 45/45 em duas rodadas, `docs/SPEC.md`/`docs/PLAN.md`
atualizados com a decisão de escopo e as pegadinhas de stack. PR
aberto, aguardando revisão/merge. Não avanço pra Fase 6 sem
confirmação.

---

## Onde paramos (endpoints de pagamento + histórico)

Fechada a lacuna registrada no PR #9: `PedidoService` ganhou
`confirmarPagamentoParcial(numeroPedido)` e
`confirmarPagamentoSaldo(numeroPedido)`, reusando a mesma validação de
`transicionar()` (`podeTransicionarManualmentePara()`, extraída pra um
método privado `transicionarValidando()` compartilhado pelos três) —
nenhuma trava nova, mesmo mapa de transições do enum. `Pedido` ganhou
`confirmarPagamentoParcial()`/`confirmarPagamentoSaldo()`
pacote-privados (mesmo padrão de `aplicarTransicao`/`aplicarConsignee`
— nunca setter público) pra marcar
`pagamentoParcialConfirmadoEm`/`pagamentoSaldoConfirmadoEm`.

`PedidoTransicaoRepository` ganhou
`findByPedidoIdOrderByOcorridoEmAsc()`; `PedidoService.buscarHistorico()`
delega pra ela. Endpoints novos em `PedidoController`: `POST
/pedidos/{numero}/pagamento-parcial`, `POST
/pedidos/{numero}/pagamento-saldo` (200 com `PedidoResponse`
atualizado) e `GET /pedidos/{numero}/historico` (200 com
`PedidoTransicaoResponse[]`, DTO novo — não expõe `PedidoTransicao`
direto). `docs/SPEC.md` atualizada: tabela de endpoints e tabela de
correlação critério×teste.

**Testes novos:**
- `PedidoServiceTest` (+5): `confirmarPagamentoParcialMarcaDataETransicionaEstado`,
  `confirmarPagamentoParcialForaDeSequenciaLancaExcecao`,
  `confirmarPagamentoSaldoMarcaDataETransicionaEstado`,
  `confirmarPagamentoSaldoSemEstarEmbarcadoLancaExcecao`,
  `buscarHistoricoDelegaParaORepositorioOrdenado`.
- `PedidoControllerTest` (+7): caminho válido e 409 pros dois
  endpoints de pagamento, e histórico vazio/com transições/pedido
  inexistente (404) pro endpoint de leitura.

Nenhuma mudança de pacote/artefato de teste nova nesta rodada (Jackson
3, `@WebMvcTest`/`MockitoBean` já mapeados no PR #9) — só reaproveitei
o que já estava configurado.

## Estado de saída (endpoints de pagamento + histórico)
Fechado: os 3 endpoints implementados, `docs/SPEC.md` atualizada,
suíte completa 42/42 em duas rodadas (uma com o Postgres recriado do
zero — `docker compose` continua bloqueado pela política de rede do
sandbox, confirmado de novo). PR aberto, aguardando review/merge do
PR #9 primeiro (esta branch depende dele) e depois deste. Não avanço
pra Fase 5 sem confirmação.

**Atenção pro merge:** como esta branch partiu de
`feat/fase4-controller-dtos` (PR #9) em vez da `main`, o PR desta
branch provavelmente vai pedir merge do #9 primeiro, ou vai precisar
de rebase depois que o #9 mergear — confira o diff do PR antes de
aprovar pra não ver o conteúdo do #9 duplicado nele.

---

## Onde paramos (Fase 4 — Controller + DTOs)

`PedidoController` e `ChecklistController` implementados com DTOs
próprios (nenhuma entidade JPA exposta na API), Bean Validation e
`GlobalExceptionHandler` (`@RestControllerAdvice`) mapeando as 7
exceções de domínio existentes + validação + parâmetro de rota
inválido pros status HTTP corretos. Detalhes completos e tabela de
mapeamento em `docs/SPEC.md` ("Endpoints" e "Mapeamento de exceção →
status HTTP").

**Decisões de design tomadas nesta fase:**
- `ChecklistController` separado de `PedidoController` — espelha a
  separação já existente `PedidoService`/`ChecklistService` (cada um
  dono do ciclo de vida de uma entidade); justificado no PR e no
  SPEC.md.
- `CriarPedidoRequest` agrupa preço/moeda/incoterm/forma de
  pagamento/percentual parcial num `CondicoesComerciaisRequest`
  aninhado, em vez dos 14 campos do Builder soltos no payload — são
  os termos do acordo comercial, mudam juntos; não adiciona validação
  nova, só reorganiza.
- Validação nos DTOs de request limitada a espelhar as constraints já
  existentes no schema (`NOT NULL`, tamanho de coluna) — não inventei
  validação semântica (ex: `@Positive` em preço/quantidade,
  faixa de `percentualParcial`) que a Spec não pede. Fica como
  possível follow-up se virar fricção real.
- `enviar`/`aceitar`/`reabrir` devolvem `204 No Content` — não existe
  corpo de resposta definido pra eles na Spec; cliente busca o pedido
  de novo via GET se precisar do estado atualizado.
- Nova exceção `ChecklistDocumentoNaoEncontradoException` (404) —
  necessária pro caso real de pedir `enviar`/`aceitar`/`reabrir` pra
  um `tipo` sem registro de checklist ainda (hoje só possível pra
  `DOCUMENTO_ADICIONAL`, que só existe depois de
  `adicionarDocumentoAdicional()`), não é validação inventada.

**Descoberta de stack (além das duas já registradas):** Boot 4.1 já
está em Jackson 3 — `ObjectMapper`/`jackson-databind` migraram de
`com.fasterxml.jackson.core` pra `tools.jackson.core` (pacote
`tools.jackson.databind.*`); `jackson-annotations` ficou no
groupId/pacote antigo. `@WebMvcTest`/`AutoConfigureMockMvc` também
saíram de `spring-boot-test-autoconfigure` pro artefato dedicado
`spring-boot-starter-webmvc-test` (pacote
`org.springframework.boot.webmvc.test.autoconfigure`), e `@MockBean`
foi removido — usar `org.springframework.test.context.bean.override.mockito.MockitoBean`
(de `spring-test`). Detalhes em `docs/SPEC.md`.

**Testes novos:** `PedidoControllerTest` (7) e `ChecklistControllerTest`
(9) via `@WebMvcTest` + `MockitoBean` (sem banco, sem contexto Spring
Boot completo) — cobrem as linhas "API" da tabela de correlação do
SPEC.md, incluindo os casos de erro (404, 409, 400) de cada endpoint,
não só o caminho feliz.

## Resultado da suíte completa (mvn test) — 2 rodadas
**30/30 verde nas duas rodadas:**
- `ChecklistServiceTest`: 6/6
- `PedidoServiceTest`: 5/5
- `PedidoRepositoryTest`: 3/3
- `PedidoControllerTest`: 7/7 (novo)
- `ChecklistControllerTest`: 9/9 (novo)

Rodada 1: suíte completa normal, Postgres já com as 3 migrations
aplicadas de execuções anteriores (Flyway confirmou schema em dia,
sem reaplicar).

Rodada 2: banco recriado do zero antes de rodar (`DROP DATABASE` +
`CREATE DATABASE`) pra forçar o Flyway a aplicar V1→V2→V3 de novo,
equivalente ao efeito de um `docker compose down -v && up -d` limpo.
Resultado idêntico, 30/30.

Nota de ambiente (mantida desde a correção da Fase 3): `docker
compose down && docker compose up -d` não funciona nesta sessão —
pull de `postgres:16` bloqueado pela política de rede do sandbox
(confirmado de novo agora, 403 no blob do registry, não é
intermitente). Usei o Postgres 16 nativo já instalado na máquina,
mesma porta/credenciais que o `application.yml` espera, e recriei o
banco entre as duas rodadas pra simular o efeito de um ambiente
limpo do zero.

## Estado de saída da Fase 4
Fechada: `PedidoController`, `ChecklistController`, DTOs,
`GlobalExceptionHandler` implementados; suíte completa 30/30 em duas
rodadas (uma delas contra banco recriado do zero). `docs/SPEC.md`
atualizada com os endpoints implementados, mapeamento de exceção→HTTP,
tabela de correlação, e 3 inconsistências pré-existentes corrigidas
de passagem (`numero_invoice`→`numero_pedido` desatualizado desde a
V2, colunas da V2 nunca documentadas na tabela `pedido`,
`DocumentacaoIncompletaException` citada mas nunca implementada).

PR aberto, aguardando revisão/merge. Não avanço para a Fase 5 (E2E)
sem confirmação.

---

## Onde paramos (revisão pós-Fase 3)
Revisão de código da Fase 3 (PR #7, mergeado) identificou 2 bugs e
1 lacuna de teste. PR #7 já estava fechado quando a correção começou,
então os ajustes foram feitos em branch nova a partir da main
atualizada, não reabrindo a branch antiga.

**Confirmado correto na revisão (sem mudança):**
- Aceite de documento é definitivo (`enviar()`/`aceitar()` rejeitam
  documento com `aceito_em` preenchido; só `reabrirAposAceite()` muda).
- `reabrirAposAceite()` só reverte `pedido.estado` pra
  `DOCUMENTACAO_ENVIADA` enquanto o pedido está em
  `DOCUMENTACAO_ACEITA` — depois de `EMBARCADO` só grava a ocorrência.
  Coberto por `ChecklistServiceTest.reabrirAposEmbarqueNaoReverteEstadoDoPedido`.
- `DOCUMENTACAO_ENVIADA`/`DOCUMENTACAO_ACEITA` seguem inalcançáveis via
  `/transicionar` manual — `TRANSICOES_MANUAIS` os exclui de todo
  conjunto de destino, e `ChecklistService` nunca passa por
  `podeTransicionarManualmentePara()` pra aplicá-los.
- Não há mais nenhum padrão `findAll()` + filtro em memória em
  `src/main`.

**2 bugs corrigidos nesta branch:**
1. `reabrirAposAceite()` agora lança `DocumentoNaoAceitoException`
   (mesmo padrão de `DocumentoNaoEnviadoException`) se chamado com
   `aceito_em == null` — antes passava direto sem validar, gravando
   uma `PedidoOcorrencia` sem sentido. Coberto por
   `ChecklistServiceTest.naoPermiteReabrirDocumentoQueNuncaFoiAceito`.
2. `PedidoService.adicionarDocumentoAdicional()` agora checa
   `ChecklistDocumentoRepository.existsByPedidoIdAndTipoDocumento()`
   antes do save e lança `DocumentoAdicionalJaExisteException` em vez
   de deixar vazar `DataIntegrityViolationException` do Postgres.
   Cardinalidade confirmada pelo dono do domínio: um documento
   adicional por pedido, constraint da V1 mantida como está — só o
   comportamento de erro mudou, de exceção genérica de banco pra
   exceção de domínio explícita. Coberto por
   `PedidoServiceTest.naoPermiteSegundoDocumentoAdicionalParaOMesmoPedido`.

**Lacuna de teste fechada:** `PedidoServiceTest` criado (Mockito, sem
banco, mesmo padrão do `ChecklistServiceTest`) cobrindo `criar()`
(checklist zerado + transição inicial no histórico), `transicionar()`
(caminho válido e `TransicaoInvalidaException` no inválido) e
`alterarConsignee()` (gera `PedidoOcorrencia` correta). Não criado
`PedidoEstadoTest` — fica registrado como lacuna remanescente, fora do
escopo pedido para esta correção.

`docs/SPEC.md` mantém as atualizações da revisão anterior (campo
`consignee`, tabela `pedido_ocorrencia`, as 3 regras de negócio, tabela
de correlação corrigida) — atualizar novamente se quiser refletir que
os 2 bugs já foram corrigidos.

## Resultado da suíte completa (mvn test)
14/14 verde:
- `ChecklistServiceTest`: 6/6 (5 originais + o novo caso de reabertura
  sem aceite)
- `PedidoServiceTest`: 5/5 (novo)
- `PedidoRepositoryTest`: 3/3, contra Postgres 16 real com Flyway
  aplicando as 3 migrations (V1, V2, V3)

Nota de ambiente: o `docker-compose.yml` do repo não pôde ser usado
nesta sessão — pull de `postgres:16` bloqueado pela política de rede
do sandbox (403 no blob do registry). Rodei contra um Postgres 16
nativo já instalado na máquina, com as mesmas credenciais que o
`application.yml` espera (`export_tracking`/`export_tracking`,
porta 5432) — schema e comportamento idênticos ao que o
docker-compose proveria; só o transporte (container vs. instalação
nativa) foi diferente.

## Estado de saída desta revisão
Fase 3 fechada: os 2 bugs identificados foram corrigidos, a lacuna de
`PedidoServiceTest` foi preenchida, e a suíte completa passa (14/14)
contra Postgres real. Aguardando sua confirmação para abrir o PR e,
depois, para avançar à Fase 4 (Controller) — não avanço sem isso.

---

## Histórico — antes da revisão (texto original da Fase 3)
Fase 3 (Service + regras de domínio) concluída e testada (8/8):

- PedidoService: criar() (gera checklist inicial + transição CRIADO),
  transicionar() consumindo podeTransicionarManualmentePara() do enum
  pela primeira vez, adicionarDocumentoAdicional(),
  alterarConsignee() (gera PedidoOcorrencia)
- ChecklistService: enviar() (bloqueado se já aceito), aceitar()
  (exige enviado, dispara DOCUMENTACAO_ENVIADA no 1º envio e
  DOCUMENTACAO_ACEITA quando todo o checklist do pedido está aceito),
  reabrirAposAceite() — exceção deliberada com motivo obrigatório
- Regra de negócio nova, vinda de caso real relatado: consignee
  (destinatário no BL) é campo próprio, separado de cliente
  (comprador) — podem ser partes diferentes e o consignee pode mudar
  depois do embarque (caso real: desistência do comprador original
  do negócio com seu consignee). Notify Party do BL não entrou —
  sem fricção real observada ainda.
- pedido_ocorrencia: tabela genérica (tipo + descrição obrigatória +
  quando) cobrindo tanto reabertura de documento quanto alteração de
  dados pós-embarque — evita campo disperso por cenário
- Regra de reversão de estado: reabrirAposAceite() só reverte
  pedido.estado pra DOCUMENTACAO_ENVIADA se o pedido ainda estiver em
  DOCUMENTACAO_ACEITA (antes do embarque). Depois de EMBARCADO, só
  registra a ocorrência — não há como "desfazer" um navio que já saiu
- Pedido ganhou Builder (construtor tinha chegado a 14 parâmetros
  com consignee) — estado e consignee só mudam via métodos
  pacote-privado (aplicarTransicao, aplicarConsignee), nunca setter
  público
- ChecklistDocumentoRepository ganhou findByPedidoId — evita table
  scan que o findAll() + filter em memória fazia antes
- Testes unitários com Mockito (ChecklistServiceTest, sem banco) +
  PedidoRepositoryTest contra Postgres real (3/3 + 8/8 no total)

## Próximo passo (histórico — já concluído nesta sessão)
As duas pendências abaixo (registradas quando a revisão da Fase 3 foi
fechada) já foram feitas: os 2 bugs foram corrigidos (PR mergeado) e
`PedidoServiceTest` foi escrito. A Fase 4 completa (Controller + DTOs,
descrita no topo deste arquivo) também já foi implementada nesta
sessão — aguardando review/merge do PR.

## Próximo passo real
Fase 5 do PLAN.md (E2E, Playwright) — **não iniciar sem confirmação
explícita**, conforme pedido. Antes de começar, vale reler o PLAN.md
pra confirmar se o escopo de Fase 5 mudou dado o que ficou registrado
como "ainda não implementado" (pagamento-parcial, pagamento-saldo,
histórico) — os 2 cenários E2E do PLAN.md (`criado→entregue` e
cancelamento antes do embarque) dependem de rotas que não existem
ainda.

## Decisões de stack confirmadas
- Spring Boot 4.1.x (não 3.x — linha 3.x é EOL desde 30/jun/2026)
- Jackson 3 (`tools.jackson.*`) desde a Fase 4 — Boot 4.1 já vem assim,
  não foi upgrade feito por nós
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
