# Status — qa-backend-export-tracking

## Última atualização
17/set/2026 — Revisão da Fase 3 (PR #7) antes do merge

## Onde paramos
Revisão de código da Fase 3 (`feat/fase3-service-consignee-ocorrencia`,
PR #7) concluída. Suíte atual roda 8/8 verde, mas a cobertura real é
mais estreita do que "8/8" sugere — ver lacunas abaixo antes de tratar
a fase como fechada.

**Confirmado correto na revisão:**
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
  `src/main` (o único caso, em `ChecklistService.aceitar()`, já tinha
  sido corrigido pra `findByPedidoId`).

**2 bugs encontrados, não corrigidos ainda (aguardando decisão antes
do merge):**
1. `reabrirAposAceite()` não valida que o documento estava de fato
   aceito — chamado com `aceito_em == null`, não lança exceção e ainda
   grava uma `PedidoOcorrencia`. Falta um guard equivalente ao
   `DocumentoNaoEnviadoException` de `aceitar()`.
2. `PedidoService.adicionarDocumentoAdicional()` esbarra na constraint
   `UNIQUE(pedido_id, tipo_documento)` (V1, não revisada na V3): só
   aceita um documento adicional por pedido — segunda chamada estoura
   `DataIntegrityViolationException`. Método recebe `descricao` por
   chamada, o que sugere que múltiplos documentos extras eram a
   intenção; schema atual não suporta isso.

**Lacuna de teste (não é bug, mas fecha errado a Fase 3 se ignorada):**
`PedidoServiceTest` não existe — `criar()`, `transicionar()` (incluindo
o caminho de `TransicaoInvalidaException`) e `alterarConsignee()` estão
sem teste unitário direto. `PedidoEstadoTest` também não existe. Os
8/8 verdes cobrem só `ChecklistService` (Mockito) e `PedidoRepository`
(Postgres via `@DataJpaTest`) — não `PedidoService`. PLAN.md é
explícito que Fase 3 é onde a cobertura de JUnit é prioridade; esse
critério ainda não fecha.

`docs/SPEC.md` foi atualizada nesta revisão: campo `consignee`,
tabela `pedido_ocorrencia`, as 3 regras de negócio da Fase 3 e a
tabela de correlação critério×teste corrigida pra refletir as lacunas
acima (estava citando `PedidoServiceTest`/`PedidoEstadoTest` como se
já existissem).

## Estado de saída desta revisão
Fase 3 **não fechada** — histórico de commits anterior (8959983,
17ec8aa) descreve como concluída; a revisão contesta essa conclusão
até os dois bugs serem resolvidos e `PedidoServiceTest` existir. Não
avançar pra Fase 4 (Controller) até isso ser decidido.

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

## Próximo passo
Antes de fechar a Fase 3 e avançar (bloqueado até decisão):
1. Decidir e corrigir os 2 bugs listados em "Estado de saída desta
   revisão" (guard em `reabrirAposAceite()`; cardinalidade de
   `adicionarDocumentoAdicional()`).
2. Escrever `PedidoServiceTest` cobrindo `criar()`, `transicionar()`
   (caminho de `TransicaoInvalidaException` incluído) e
   `alterarConsignee()`.

Só depois disso, Fase 4 do PLAN.md: Controller + DTOs
- PedidoController: POST /pedidos, GET /pedidos/{numero},
  PATCH /pedidos/{numero}/transicionar
- ChecklistController (ou endpoints dentro do PedidoController):
  enviar/aceitar documento, reabrirAposAceite
- DTOs de request/response (não expor entidade JPA direto na API)
- Exception handler (@ControllerAdvice) mapeando as exceções de
  domínio pros status HTTP certos (404, 409)
- Atenção: Boot 4.1 já mudou pacote de teste 2x (Flyway, DataJpaTest)
  — verificar se @WebMvcTest também mudou de artefato/pacote antes
  de escrever o teste, não depois do erro de compilação
- Confirmação: testes cobrindo a tabela de correlação da Spec
  (linhas "API")

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
