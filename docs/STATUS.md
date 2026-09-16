# Status — qa-backend-export-tracking

## Última atualização
16/set/2026 — Correção de schema pós-Fase 2 (V2)

## Onde paramos
Fase 2 (Repository) concluída na Fase anterior. Hoje entrou uma correção
de modelo que reflete o fluxo real de negócio, descoberta ao detalhar
a Fase 3:

- O sistema começa pelo PO (Pedido/Purchase Order), não pela invoice —
  a invoice é gerada a partir dos dados do PO e é só um item do
  checklist documental. Migration V2 renomeia numero_invoice para
  numero_pedido (chave de negócio desde a criação).
- Campos de PO que faltavam no schema: porto_origem (texto livre —
  não faz diferença comercial, quem fixa o preço é o fornecedor
  independente do porto), incoterm (enum com os 11 termos do
  Incoterms 2020 — padrão internacional fechado, mesmo hoje só
  usando CFR/FOB), forma_pagamento (enum: CARTA_CREDITO,
  TT_ANTECIPADO, TT_CONTRA_DOCUMENTOS, COBRANCA_DOCUMENTARIA) e
  percentual_parcial (decimal — o saldo é 100 - percentual_parcial,
  calculado, nunca persistido).
- TipoDocumento ganhou DOCUMENTO_ADICIONAL (documento extra que
  alguns países exigem) — ChecklistDocumento ganhou campo descricao
  opcional pra esse caso. A checagem de "documentação aceita" deixa
  de contar "4 tipos fixos" e passa a ser "todos os itens do
  checklist deste pedido", já preparada pro item opcional.
- Regra de negócio confirmada pro reenvio de documento: aceite é
  definitivo e por documento — uma vez que aceito_em é preenchido,
  aquele documento trava, sem volta. reenviar só é válido enquanto
  aceito_em ainda for nulo. Isso elimina qualquer necessidade de
  reverter pedido.estado — DOCUMENTACAO_ACEITA, uma vez alcançado,
  nunca desfaz.
- PedidoRepositoryTest atualizado e validado (3/3) contra o schema V2.

## Próximo passo
Fase 3 do PLAN.md: Service + regras de domínio
- PedidoService: criar pedido (+ checklist zerado com os itens fixos,
  + transição inicial), transicionar() usando
  podeTransicionarManualmentePara() do enum (primeiro consumidor
  real desse método)
- ChecklistService: enviar (só se aceito_em nulo), aceitar (exige
  enviado_em, imutável depois de setado), dispara DOCUMENTACAO_ENVIADA
  no 1º envio e DOCUMENTACAO_ACEITA quando todos os itens do
  checklist estão aceitos — direto, sem passar por
  podeTransicionarManualmentePara()
- Exceções de domínio: TransicaoInvalidaException,
  PedidoNaoEncontradoException, DocumentoJaAceitoException
- Ponto em aberto: construtor de Pedido já tem 13 parâmetros —
  avaliar builder/record de request quando PedidoService.criarPedido()
  chamar de verdade, não antes
- Confirmação: testes unitários cobrindo a tabela de correlação da
  Spec (linhas "Unitário")

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
