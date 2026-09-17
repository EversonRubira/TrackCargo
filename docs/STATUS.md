# Status — qa-backend-export-tracking

## Última atualização
17/set/2026 — Fase 4 (Controller + DTOs) implementada, branch
`feat/fase4-controller-dtos`

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
