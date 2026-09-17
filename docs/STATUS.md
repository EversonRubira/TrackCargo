# Status — qa-backend-export-tracking

## Última atualização
17/set/2026 — Correções pós-revisão da Fase 3 aplicadas (branch
`fix/fase3-guard-documento-adicional`, a partir da main já com o
PR #7 mergeado)

## Onde paramos
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
