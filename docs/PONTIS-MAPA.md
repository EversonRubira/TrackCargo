# Mapa Pontis (TrackCargo para Pontis)

Documento de planeamento, sem código. Objetivo: descrever o estado
atual do sistema (hoje chamado TrackCargo), o que muda para se tornar
Pontis (espaço partilhado entre exportador e importador), o que é
novo, uma ordem de construção em passos pequenos, os riscos e
inconsistências encontrados, e as decisões que faltam tomar.

Nome do produto: decidido mudar de TrackCargo para Pontis, mas nenhum
nome foi alterado no código nesta sessão. A secção final lista onde o
nome antigo aparece, para quando a renomeação for autorizada.

## a) Estado atual

Esta secção resume o que existe hoje, com o ficheiro de origem de
cada afirmação. É a base factual sobre a qual as secções seguintes
raciocinam.

### Visão geral do sistema

O TrackCargo é um sistema de rastreamento do ciclo de vida de um
pedido de exportação, usado por um único operador (o fornecedor/
exportador). Não existe conceito de empresa, utilizador ou papel: a
API é aberta, sem autenticação (`docs/PRD.md`, secção 4, "Audiência:
uso individual pelo fornecedor... Sem multiusuário no v1"). O cliente
final (comprador) não é utilizador do sistema, não tem acesso nem
login (`docs/PRD.md`, secção 4 e secção 9).

### Modelo de dados (migrations `src/main/resources/db/migration/V1` a `V5`)

- **`pedido`**: a entidade central. Guarda identificação (número do
  pedido, cliente, consignee), logística (país de destino, portos,
  companhia marítima, número de container), condições comerciais
  (preço, moeda, incoterm, forma de pagamento, percentual parcial) e
  o estado atual do ciclo de vida. Entidade Java em
  `src/main/java/com/eversonrubira/exporttracking/pedido/Pedido.java`.
  Não tem nenhuma coluna de "dono" (empresa ou utilizador): qualquer
  chamada à API vê e altera qualquer pedido.
- **`checklist_documento`**: um registo por tipo de documento
  obrigatório (invoice, packing list, BL, certificado sanitário, mais
  um "documento adicional" opcional), com `enviado_em` e `aceito_em`.
  Desde a migration V5, também `reaberto_em`/`motivo_reabertura` e
  suporte a recusa (zera `enviado_em`). Não existe controlo de quem
  enviou ou quem aceitou, porque só há um operador. Não existe campo
  de "visto" (quando alguém abriu/leu o documento), nem campo de
  "privado" ou "partilhado".
- **`pedido_ocorrencia`**: registo de eventos que geram atraso ou
  custo e exigem motivo (reabertura de documento, recusa de
  documento, alteração de dados pós-embarque). Motivo é sempre
  obrigatório, não serve para comentários livres (`docs/SPEC.md`,
  secção "Modelo de dados", tabela `pedido_ocorrencia`).
- **`pedido_transicao`**: histórico de mudanças de estado, com
  timestamp. É o único histórico que existe hoje.
- **`pedido_sequencia`**: contador de numeração automática
  (`NNNNN/AAAA`), uma linha por ano, global ao sistema inteiro (não
  por empresa, porque hoje só há uma).

### Máquina de estados

Nove estados (`PedidoEstado.java`): `CRIADO`,
`DOCUMENTACAO_ENVIADA`, `DOCUMENTACAO_ACEITA`,
`PAGAMENTO_PARCIAL_RECEBIDO`, `EMBARCADO`,
`PAGAMENTO_SALDO_RECEBIDO`, `DOCUMENTOS_ORIGINAIS_ENVIADOS`,
`ENTREGUE`, `CANCELADO`. `CANCELADO` só é alcançável antes de
`EMBARCADO`. As transições manuais (via API) seguem um mapa fixo no
próprio enum; duas transições (`DOCUMENTACAO_ENVIADA` e
`DOCUMENTACAO_ACEITA`) só acontecem como efeito colateral de ações no
checklist, nunca por chamada direta.

### Endpoints (todos sem autenticação)

`PedidoController.java` (`/pedidos`) e `ChecklistController.java`
(`/pedidos/{numeroPedido}/documentos/{tipo}`):

| Método | Rota | O que faz hoje |
|---|---|---|
| POST | `/pedidos` | Cria pedido |
| GET | `/pedidos` | Lista todos os pedidos (filtro opcional por estado) |
| GET | `/pedidos/proximo-numero` | Sugere o próximo número automático |
| GET | `/pedidos/{numero}` | Consulta um pedido |
| PATCH | `/pedidos/{numero}/transicionar` | Muda o estado |
| POST | `/pedidos/{numero}/pagamento-parcial` | Confirma pagamento parcial |
| POST | `/pedidos/{numero}/pagamento-saldo` | Confirma pagamento de saldo |
| GET | `/pedidos/{numero}/historico` | Lista transições |
| PATCH | `/pedidos/{numero}/logistica` | Atualiza companhia marítima e/ou container |
| GET | `/pedidos/{numero}/status.pdf` | Gera PDF de status (parâmetro `?lang=`) |
| PATCH | `/pedidos/{numero}/documentos/{tipo}/enviar` | Marca documento enviado |
| PATCH | `/pedidos/{numero}/documentos/{tipo}/aceitar` | Marca documento aceite |
| PATCH | `/pedidos/{numero}/documentos/{tipo}/reabrir` | Reabre documento já aceite, com motivo |
| PATCH | `/pedidos/{numero}/documentos/{tipo}/recusar` | Recusa documento enviado, com motivo |

Qualquer chamador pode fazer qualquer uma destas chamadas. Não há
conceito de "só o importador pode aceitar", porque não há conceito de
importador.

### Segurança

Não existe nenhuma dependência de segurança no `pom.xml` (sem
`spring-boot-starter-security`), nem nenhuma classe
`SecurityFilterChain` ou `@EnableWebSecurity` em `src/`. Confirmado
por procura no código, não só por falta de menção nos documentos.

### Frontend

`frontend/src/pages/`: três páginas (`ListaPedidos.tsx`,
`CriarPedido.tsx`, `DetalhePedido.tsx`), sem login, consumindo a API
diretamente. Internacionalização completa (pt/en/es) via `i18next`,
estrutura em `frontend/src/i18n/`. O nome "TrackCargo" é texto fixo
em `frontend/src/App.tsx` (linha 16), fora do mecanismo de tradução
(decisão já registada em `docs/SPEC.md`, linha 1072: o nome do
produto é propositadamente o único texto visível que não traduz).

### Testes e CI

Suite completa (unitária, de API, e um fluxo ponta a ponta real via
REST Assured em `FluxoPedidoE2ETest`), mais testes de frontend
(Vitest). `.github/workflows/ci.yml` corre os dois lados (job `test`
para o backend, job `frontend` para o frontend) em cada push e pull
request para `main`.

## b) O que muda

Tabela de cada tabela e endpoint atual, com o destino proposto no
Pontis. "Porquê" explica a razão da mudança, não só o quê.

### Tabelas

| Tabela atual | Destino no Pontis | Porquê |
|---|---|---|
| `pedido` | Altera | Precisa de um dono (a empresa exportadora que criou a operação) e de uma ligação à empresa importadora convidada. Hoje não tem nenhuma das duas colisas. |
| `checklist_documento` | Altera | Precisa de um campo de visibilidade (privado por padrão, partilhado quando marcado) e de um registo de "visto" (quando o importador abriu o documento), que hoje não existem. A regra de quem pode aceitar/recusar passa a ser um papel (importador), não qualquer chamador. |
| `pedido_ocorrencia` | Mantém | Continua a servir para eventos com motivo obrigatório (reabertura, recusa, alteração pós-embarque). Não é o lugar certo para comentários livres, porque exige sempre um motivo e não é isso que um comentário é. |
| `pedido_transicao` | Mantém | O histórico de estado continua a fazer sentido tal como está. Passa a alimentar notificações (ver "O que é novo"), mas a tabela em si não precisa de mudar. |
| `pedido_sequencia` | Mantém, com risco a decidir | O mecanismo de numeração automática continua útil. Risco: hoje o contador é global (uma linha por ano, para o sistema inteiro); com várias empresas exportadoras, pode fazer mais sentido um contador por empresa. Ver secção de riscos. |
| (nenhuma) | Nova: `empresa` | Não existe hoje nenhum conceito de empresa. É a base de "qualquer empresa pode registar-se". |
| (nenhuma) | Nova: `utilizador` | Pessoa que faz login, associada a uma empresa. Não existe hoje (sistema sem login). |
| (nenhuma) | Nova: `operacao_participante` (ou nome equivalente) | Liga uma empresa a uma operação com um papel (exportador ou importador) **nessa operação**. O papel não é uma propriedade fixa da empresa: a mesma empresa pode ser exportadora numa operação e importadora noutra. |
| (nenhuma) | Nova: `convite` | Token único por link de convite, usado para a empresa importadora entrar na operação. |
| (nenhuma) | Nova: `comentario` | Comentários por operação, opcionalmente ligados a um documento ou a uma transição de estado. Guardados com a operação, sem chat geral (pedido explícito). |
| (nenhuma) | Nova: `notificacao` | Registo de notificações enviadas (documento enviado, pagamento, embarque, na v1). |

### Endpoints

| Endpoint atual | Destino | Porquê |
|---|---|---|
| `POST /pedidos` | Altera | Passa a exigir utilizador autenticado; a operação nasce com a empresa desse utilizador como exportadora. |
| `GET /pedidos` | Altera | Hoje lista tudo sem filtro de dono. Passa a listar só as operações onde a empresa do utilizador autenticado participa (como exportadora ou importadora). |
| `GET /pedidos/{numero}` | Altera | Acesso restrito a quem participa na operação. Campos/documentos não marcados como partilhados ficam ocultos para o importador. |
| `PATCH /pedidos/{numero}/transicionar` | Altera (papel a confirmar) | Continua a existir, mas falta decidir se só o exportador pode mudar o estado do pedido ou se o importador também pode disparar alguma transição (ver decisões). |
| `POST /pedidos/{numero}/pagamento-parcial` e `/pagamento-saldo` | Altera (papel a confirmar) | Provavelmente só o exportador confirma o pagamento recebido, mas vale confirmar. |
| `GET /pedidos/{numero}/historico` | Altera | Continua a existir, visível aos dois papéis (histórico de estado não é dado privado por natureza). |
| `PATCH /pedidos/{numero}/logistica` | Altera (papel a confirmar) | Dados logísticos, hoje só o exportador preenche. Confirmar se o importador só lê ou também pode editar algum campo. |
| `GET /pedidos/{numero}/status.pdf` | Altera | Hoje é gerado a pedido, sem controlo nenhum. No Pontis, o uso mais importante deste PDF passa a ser no encerramento da operação, com um fluxo de autorização de ambos os lados que hoje não existe. |
| `PATCH .../documentos/{tipo}/enviar` | Mantém (ator: exportador) | Continua a ser o exportador que envia o documento. |
| `PATCH .../documentos/{tipo}/aceitar` | Altera | Regra nova e central do Pontis: só o importador pode aceitar. Hoje qualquer chamador pode. |
| `PATCH .../documentos/{tipo}/recusar` | Altera | Mesma regra: só o importador pode recusar, com motivo (já existe o campo de motivo, falta a restrição de quem chama). |
| `PATCH .../documentos/{tipo}/reabrir` | Altera (decisão aberta) | Hoje é o exportador que reabre um documento já aceite. Se a aceitação passa a ser um ato do importador, talvez o exportador já não deva poder desfazê-la sozinho. Ver decisões. |
| `GET /pedidos/proximo-numero` | Mantém (ator: exportador) | Sem mudança de regra, só deixa de ser global se o contador passar a ser por empresa (ver tabela acima). |
| (nenhum) | Novo: convite por link | Gerar um link de convite para uma operação e aceitar esse convite (associando a empresa importadora). |
| (nenhum) | Novo: comentários | Criar e listar comentários de uma operação. |
| (nenhum) | Novo: notificações | Listar notificações de um utilizador/empresa. |
| (nenhum) | Novo: encerramento | Marcar a operação como encerrada, com autorização das duas partes, e gerar o PDF final do histórico. |
| (nenhum) | Novo: autenticação | Registo de empresa/utilizador, login, sessão. |

## c) O que é novo

Resumo do que não existe de forma nenhuma hoje e precisa de ser
construído do zero:

- **Empresas e utilizadores**: hoje o sistema não sabe quem está a
  chamá-lo. Qualquer empresa poder registar-se implica um fluxo de
  registo, login, e sessão (ver decisão de autenticação).
- **Convite por link**: mecanismo para o exportador convidar uma
  empresa importadora específica para uma operação, sem que essa
  empresa precise de já ter conta antes de receber o link.
- **Comentários**: texto livre por operação, opcionalmente ligado a
  um documento específico ou a um estado/transição, guardado com a
  operação (sem mensagens soltas fora de uma operação).
- **Notificações**: aviso automático quando algo relevante acontece
  numa operação (documento enviado, pagamento, embarque, na v1).
  Falta decidir o canal (e-mail, dentro da aplicação, ambos).
- **Arquivo e encerramento**: hoje uma operação que chega a
  `ENTREGUE` ou `CANCELADO` fica "parada" (sem mais transições
  possíveis), mas continua tecnicamente editável (não há nenhuma
  trava que impeça chamar `/logistica` ou outro endpoint depois
  disso). Encerrar é um conceito novo: tornar a operação só de
  leitura, com autorização expressa dos dois lados, e gerar o PDF
  final nesse momento.
- **Visibilidade privada/partilhada**: hoje não existe nenhum
  conceito de campo ou documento privado. Tudo o que existe é visível
  a quem fizer a chamada (porque só há um operador). Isto é
  provavelmente a mudança de maior impacto no modelo de dados.
- **"Visto em"**: hoje o sistema não regista quando alguém abriu ou
  leu nada. É um conceito novo, distinto de "aceite" (que já existe).

## d) Ordem de construção

Cada passo é útil sozinho e termina em algo que se pode correr e ver
a funcionar, sem depender de passos futuros para fazer sentido.

1. **Autenticação básica.** Empresa, utilizador, login com Spring
   Security (ver decisão). Sem multiempresa de dados ainda: as
   operações continuam todas visíveis a quem estiver autenticado,
   só deixam de estar abertas sem login nenhum.
   Resultado para correr: fazer login e ver a lista de pedidos de
   antes, agora exigindo sessão.
2. **Dono da operação.** `pedido` ganha a empresa exportadora. A
   listagem passa a filtrar pela empresa do utilizador autenticado.
   Resultado para correr: criar uma operação com a conta da empresa
   A, iniciar sessão com a empresa B e confirmar que B não a vê.
3. **Convite e segunda empresa.** Gerar link de convite para uma
   operação; aceitar o convite associa a empresa importadora a essa
   operação (papel "importador").
   Resultado para correr: empresa A convida empresa B; B aceita o
   convite e passa a ver essa operação específica na sua lista.
4. **Papéis e permissões sobre o checklist.** Só o importador pode
   aceitar/recusar um documento; o exportador continua a poder
   enviar.
   Resultado para correr: a empresa exportadora tenta aceitar um
   documento e recebe um erro de permissão; a empresa importadora
   consegue.
5. **Privado por padrão.** Documentos (e, se decidido, outros campos)
   ganham um estado de partilha. O importador só vê o que estiver
   marcado como partilhado.
   Resultado para correr: um documento novo não aparece para o
   importador até o exportador o marcar como partilhado.
6. **Registo de "visto".** Quando o importador abre a operação ou um
   documento específico, grava-se quando.
   Resultado para correr: a empresa exportadora vê "visto em" depois
   de a importadora abrir a operação.
7. **Comentários.** Criar e listar comentários numa operação,
   opcionalmente ligados a um documento ou a uma transição.
   Resultado para correr: as duas empresas trocam comentários dentro
   da mesma operação e ambas os veem.
8. **Notificações da v1.** Aviso quando um documento é enviado, um
   pagamento é confirmado, ou a operação embarca.
   Resultado para correr: fazer uma destas três ações e confirmar que
   a outra empresa recebe o aviso (mesmo que o canal inicial seja só
   uma lista dentro da aplicação, antes de decidir e-mail).
9. **Encerramento com autorização dupla.** As duas empresas autorizam
   o encerramento; a operação fica só de leitura; gera-se o PDF final.
   Resultado para correr: encerrar uma operação de teste e confirmar
   que já não é possível editá-la, e que o PDF saiu.
10. **(Fora desta leva, depende de confirmação)** Link de leitura sem
    conta. Só se avança depois de a proposta da introdução ser
    confirmada ou rejeitada.

Cada passo acima pressupõe o anterior feito, mas nenhum pressupõe os
seguintes. O passo 1 por si só já é demonstrável sem nenhum dos
restantes existir.

## e) Riscos e inconsistências

- **O PRD atual contradiz diretamente o pedido do Pontis.**
  `docs/PRD.md`, secção 4: "Uso individual pelo fornecedor... Sem
  multiusuário no v1... O cliente final não é usuário do sistema".
  Secção 9 lista "Autenticação e autorização multiusuário" como fora
  de escopo. O Pontis exige exatamente o contrário (duas empresas,
  cada uma com utilizadores próprios). O PRD tem de ser reaberto e
  reescrito antes de qualquer implementação; não assumi que isto já
  estava decidido, só confirmei que o texto atual diz o oposto.
- **O número do pedido (`numero_pedido`) é a chave de negócio
  principal e tem um contador automático global** (`pedido_sequencia`,
  uma linha por ano, para o sistema inteiro,
  `docs/SPEC.md`, secção "Numeração automática do pedido"). Com várias
  empresas exportadoras, dois exportadores diferentes podem querer o
  mesmo número sugerido ao mesmo tempo. Hoje isso nunca foi um
  problema, porque só há um exportador. Fica como decisão aberta se o
  contador passa a ser por empresa.
- **A regra "aceite é definitivo, só se desfaz com `reabrirAposAceite`"
  foi pensada para um único operador a corrigir o seu próprio erro**
  (`docs/SPEC.md`, secção "Regras de negócio — Fase 3"). No Pontis, a
  aceitação passa a ser um ato do importador, de outra empresa.
  Permitir que o exportador reabra unilateralmente algo que o
  importador aceitou é uma mudança de poder entre as partes que não
  foi pedida nem decidida aqui. Fica como pergunta na secção seguinte.
- **Não existe hoje nenhum conceito de visibilidade de dados.** Tudo
  o que existe no `pedido` e no `checklist_documento` é um conjunto
  plano de colunas, todas igualmente visíveis a quem chamar a API
  (porque só há um chamador possível). Decidir "privado por padrão"
  campo a campo, ou só ao nível de documento, é uma decisão de
  modelo de dados com impacto em quase todas as tabelas existentes.
- **Risco já registado antes desta tarefa, sem relação direta com o
  Pontis mas que continua aberto:** `docs/STATUS.md`, secção
  "Migration de proteção — enum Moeda sem tratamento de dado legado"
  (confirmado 26/09/2026). Um pedido com `moeda` fora do enum fechado
  (`USD`/`EUR`/`BRL`) derruba a listagem com erro 500. Não é causado
  pelo Pontis, mas qualquer trabalho futuro no `pedido` vai pisar este
  risco se não for corrigido antes.
- **Existe uma branch antiga e abandonada, `docs/rename-trackcargo`**,
  criada muito antes desta sessão (o seu histórico só chega até à
  Fase 4, muito atrás do estado atual da `main`). Não a toquei. Não
  deve ser reaproveitada: é um ponto de partida desatualizado, não um
  trabalho de renomeação em curso.
- **O endereço remoto do git (`git remote -v`) continua a apontar
  para `qa-backend-export-tracking`**, mas o GitHub já redireciona
  esse endereço para `TrackCargo` (confirmado pela mensagem do
  próprio `git push`: "This repository moved. Please use the new
  location: https://github.com/EversonRubira/TrackCargo.git"). Não é
  uma inconsistência grave (o redirecionamento funciona), mas vale
  atualizar o remoto local e qualquer link novo para o nome atual do
  repositório, não o antigo.
- **A proposta de "link abre a operação em leitura sem conta" ainda
  não está confirmada.** Tratei-a como proposta em aberto, não como
  decisão, e não a incluí na ordem de construção (passo 10, fora
  desta leva) até ser confirmada ou rejeitada.

## f) Decisões para tomar

### 1. Autenticação

- **Opção A (recomendada): Spring Security**, com login por
  formulário ou por token (ex.: JWT), sessão por empresa/utilizador.
  Vantagem: é a solução consolidada e documentada no ecossistema
  Spring, o que já era a inclinação dada no pedido. Desvantagem:
  exige configurar `SecurityFilterChain`, gestão de palavra-passe
  (hash, recuperação), e decidir sessão vs. token sem precedente
  nenhum no código atual (nenhuma linha de segurança existe hoje,
  confirmado na secção "Estado atual").
- **Opção B: serviço externo de autenticação** (ex.: Auth0,
  Keycloak, Firebase Auth). Vantagem: menos código próprio de
  segurança para manter. Desvantagem: mais uma peça de infraestrutura
  externa a gerir, custo próprio, e não é o que foi pedido como
  inclinação.
- **Recomendação:** Opção A. É o que foi pedido como inclinação, e
  não há nenhuma fricção real hoje que justifique depender de um
  serviço externo (princípio já usado no resto do projeto: não
  adicionar dependência sem necessidade comprovada).

### 2. Quem pode reabrir um documento já aceite

- **Opção A: só o exportador continua a poder reabrir**, como hoje,
  mesmo depois de a aceitação passar a ser do importador. Vantagem:
  menos mudança de regra. Desvantagem: dá ao exportador o poder de
  desfazer uma decisão do importador sem o consentimento dele, o que
  pode não corresponder à realidade do negócio (o documento foi
  aceite pelo importador, não deveria ser o exportador a decidir
  reabrir sozinho).
- **Opção B: só o importador pode reabrir o que ele próprio aceitou**
  (o exportador deixa de poder). Vantagem: coerente com "só o
  importador aceita ou recusa". Desvantagem: se o exportador detetar
  um erro no documento depois de aceite, fica sem forma de corrigir
  sem pedir ao importador para reabrir.
- **Opção C: qualquer uma das partes pode pedir reabertura, mas só
  com confirmação da outra** (como o encerramento, mas para um único
  documento). Vantagem: mais justo. Desvantagem: mais um fluxo de
  dupla confirmação para construir, maior custo.
- **Recomendação:** não tenho inclinação clara aqui, porque depende
  de como o negócio real trata um erro detetado depois do aceite.
  Preciso da sua decisão.

### 3. Numeração automática por empresa ou global

- **Opção A: manter um contador global** (como hoje). Vantagem:
  nenhuma mudança no código existente. Desvantagem: duas empresas
  exportadoras podem competir pelo mesmo número sugerido.
- **Opção B: um contador por empresa** (tabela `pedido_sequencia`
  ganha a empresa como parte da chave). Vantagem: cada empresa tem a
  sua própria numeração, sem interferência. Desvantagem: migração de
  dados do contador existente (hoje só tem o ano como chave).
- **Recomendação:** Opção B, porque o número de pedido já é descrito
  como "a chave de negócio do domínio inteiro" (`docs/SPEC.md`), e
  dividir esse espaço entre empresas que não se conhecem evita
  colisão de sugestão sem custo real (é só acrescentar uma coluna à
  chave).

### 4. Visibilidade privada versus partilhada: ao nível de documento ou de campo

- **Opção A: só ao nível de documento** (cada item do checklist tem
  um estado de partilha, o resto do pedido é sempre visível ao
  importador). Vantagem: mudança mais pequena, só toca
  `checklist_documento`. Desvantagem: campos sensíveis do pedido
  (preço acordado, por exemplo) ficam sempre visíveis ao importador,
  o que pode não ser o pretendido.
- **Opção B: campo a campo em todo o pedido.** Vantagem: controlo
  total. Desvantagem: custo de implementação muito maior (cada campo
  precisa de um estado próprio), e overengineering se não houver
  pedido real de esconder campos específicos do pedido em si (só dos
  documentos).
- **Recomendação:** Opção A para começar (coerente com o princípio de
  não construir abstração sem fricção real já seguido no resto do
  projeto), reavaliando para Opção B só se aparecer um caso concreto
  de um campo do pedido que precise de ficar privado.

### 5. Canal de notificação

- **Opção A: e-mail.** Vantagem: chega ao utilizador mesmo sem abrir
  a aplicação. Desvantagem: exige integração de envio de e-mail, que
  não existe hoje (já estava registada como dependência em falta no
  backlog anterior, `docs/STATUS.md`, "Notificações automáticas ao
  cliente por etapa").
- **Opção B: só dentro da aplicação** (uma lista de notificações, sem
  envio externo). Vantagem: não depende de nenhuma integração nova.
  Desvantagem: só é vista se o utilizador abrir a aplicação.
- **Recomendação:** começar pela Opção B (passo 8 da ordem de
  construção já está pensado assim), e acrescentar e-mail depois,
  quando a integração existir. Não vale construir as duas ao mesmo
  tempo sem ainda saber se o utilizador volta à aplicação sozinho ou
  precisa do e-mail para o fazer.

### 6. Proposta "link de leitura sem conta"

Esta não é uma decisão com opções minhas, é só a confirmação pedida:
a proposta descrita na introdução (o link abre a operação em leitura
sem conta, só aceitar/recusar/comentar exige conta) fica para
confirmar ou rejeitar antes do passo 10 da ordem de construção.

## Renomeação TrackCargo para Pontis (inventário, sem alterações)

### (i) Visível ao utilizador: renomear primeiro

| Onde | O quê |
|---|---|
| `frontend/src/App.tsx`, linha 16 | Texto fixo `TrackCargo` no cabeçalho da interface, fora do mecanismo de tradução (decisão já registada em `docs/SPEC.md`, linha 1072, de que o nome do produto não traduz) |
| `README.md`, linha 1 | Título `# TrackCargo` |
| `README.md`, linha 9 | "O TrackCargo acompanha o ciclo de vida completo..." |
| `docs/SPEC.md`, linha 1072 | Menção ao nome do produto como texto que não traduz |
| `docs/STATUS.md`, linha 1 | Título `# Status — TrackCargo` |
| `docs/STATUS.md`, linha 14 | Menção ao nome do produto na regra de i18n |
| `docs/STATUS.md`, linha 1313 | "...foge do domínio atual (TrackCargo modela o pedido de exportação...)" |
| `docs/STATUS.md`, linha 892 | Link para `https://github.com/EversonRubira/TrackCargo/actions/...` dentro de uma evidência de execução registada (ver nota sobre o nome do repositório GitHub na secção de riscos) |
| `frontend/dist/assets/index-*.js` | Ficheiro gerado pelo build do Vite, contém o texto acima embutido. Não é código fonte, não precisa de edição própria: atualiza sozinho no próximo build depois de `App.tsx` ser corrigido |

Não encontrada nenhuma ocorrência do nome no PDF gerado
(`PdfStatusService`) nem nas chaves de tradução (`frontend/src/i18n/locales/*.json`,
`src/main/resources/i18n/pdf-status-messages_*.properties`): o PDF e
as traduções nunca mencionam o nome do produto.

### (ii) Identificadores técnicos: manter por agora, com risco e recomendação

| Identificador | Onde | Risco de renomear agora | Recomendação |
|---|---|---|---|
| Pacote Java `com.eversonrubira.exporttracking` | Usado em `package`/`import` em todos os 62 ficheiros `.java` do projeto (produção e teste) | Renomear um pacote Java obriga a tocar todos os 62 ficheiros de uma vez (mesmo sendo uma operação mecânica de "find and replace", qualquer IDE faz isto, mas é um diff enorme e arriscado de rever à mão) | Manter por agora. Nenhuma fricção real: o nome do pacote nunca é visível a ninguém fora do código |
| `artifactId`/`groupId`/`name` no `pom.xml` | `pom.xml`, linhas 14 a 17 | Mudar o `artifactId` muda o nome do `.jar` gerado e pode afetar scripts de build/deploy que assumam o nome atual (nenhum script assim foi encontrado hoje, mas o risco existe para o futuro) | Manter por agora |
| Nome da aplicação Spring (`application.yml`) | `src/main/resources/application.yml`, linha 3 | Baixo, é só um nome de log/métricas | Pode ser renomeado sem risco relevante, mas sem urgência |
| Base de dados, utilizador e password Postgres | `src/main/resources/application.yml` (linhas 6 a 8), `docker-compose.yml` (linhas 4 a 8) | Renomear a base de dados ou o utilizador invalida qualquer ambiente local já criado (volumes Docker existentes ficam a apontar para um nome antigo) e exige recriar o container do zero | Manter por agora, exatamente pela razão apontada no pedido (não quebrar volumes nem migrações já aplicadas) |
| Nome e volume do container Docker (`export-tracking-db`, `export-tracking-data`) | `docker-compose.yml`, linhas 4 e 12/15 | Mesmo risco da linha anterior: um volume renomeado não encontra os dados do volume antigo | Manter por agora |
| Variáveis de ambiente do CI (`POSTGRES_DB`/`USER`/`PASSWORD`) | `.github/workflows/ci.yml`, linhas 17 a 19 | Têm de acompanhar o que estiver em `application.yml`; renomear um sem o outro quebra o CI | Manter por agora, junto com a linha anterior |
| Nome do repositório GitHub | Já é `TrackCargo` no GitHub (confirmado pela mensagem de redireccionamento do `git push`); o remoto local (`.git/config`) ainda aponta para o endereço antigo `qa-backend-export-tracking`, que o GitHub redireciona automaticamente | Baixo: o redireccionamento já funciona | Atualizar o remoto local (`git remote set-url`) para o endereço atual, e usar o nome atual em qualquer link novo escrito nos documentos a partir de agora, para não acumular mais referências ao nome antigo como a da linha 892 do `docs/STATUS.md` |

### O nome do produto está centralizado num só sítio?

Não. Hoje "TrackCargo" é texto fixo escrito directamente em
`frontend/src/App.tsx` (linha 16), não uma constante nem uma chave de
tradução. Como o próprio `docs/SPEC.md` (linha 1072) já decidiu que o
nome do produto nunca é traduzido, não faz sentido pô-lo no sistema
de i18n (seria uma chave igual nos três idiomas, sem ganho nenhum).
**Proposta:** criar um único ficheiro pequeno, por exemplo
`frontend/src/config/produto.ts`, exportando uma constante
`export const NOME_PRODUTO = 'TrackCargo'`, e `App.tsx` passa a
importar e usar essa constante em vez do texto fixo. Uma troca futura
de nome (Pontis confirmado, ou qualquer outro) passa a ser uma
alteração de uma linha, num ficheiro só. Esta proposta não foi
aplicada nesta sessão (nenhum código foi alterado), fica registada
para quando a renomeação visível (parte (i) acima) for autorizada.
