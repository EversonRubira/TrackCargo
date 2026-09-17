# Status — qa-backend-export-tracking

## Última atualização
17/set/2026 — Fase 3 (Service) concluída

## Onde paramos
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
Fase 4 do PLAN.md: Controller + DTOs
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
