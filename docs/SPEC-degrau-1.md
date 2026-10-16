# Spec técnica: degrau 1, empresas e contas

Spec do primeiro degrau da ordem de construção descrita em
`docs/PONTIS-MAPA.md`, secção "d". Nenhum código foi escrito nesta
sessão: este documento descreve o que seria preciso construir, para
revisão antes de qualquer implementação.

## Nota de alcance

O `docs/PONTIS-MAPA.md` tinha dividido isto em dois degraus
separados: degrau 1 (só autenticação, sem o pedido pertencer a
ninguém ainda) e degrau 2 (o pedido ganha uma empresa dona). Esta
spec junta os dois num só, porque o pedido desta sessão exige
descrever o que acontece aos dados já existentes quando se introduz
o conceito de empresa, e isso não faz sentido descrever sem já saber
o destino desses dados (a empresa dona). Juntar os dois degraus
adjacentes é uma mudança de organização do plano, não uma decisão de
produto nova: continua a não existir papel de importador nem convite
nesta spec (isso é um degrau posterior).

## Objetivo do degrau

No fim deste degrau, uma empresa consegue registar-se, fazer login, e
só vê e só cria operações (ainda chamadas "pedido" no código, por
não ser para renomear nesta leva) que pertencem a ela própria. Uma
segunda empresa, registada à parte, não vê nada da primeira.

**Resultado para correr e ver:**

1. Registar a empresa A (nome, utilizador, email, senha).
2. Fazer login como empresa A, criar uma operação. Ela aparece na
   lista da empresa A.
3. Terminar sessão, registar a empresa B.
4. Fazer login como empresa B. A lista de operações da empresa B
   está vazia (não vê a operação da empresa A).
5. Tentar aceder diretamente à operação da empresa A pelo número
   (`GET /pedidos/{numero}`), autenticado como empresa B: recebe 404,
   não 200 nem 403 (decisão explicada na secção de autorização).

Não existe ainda: convite, segunda empresa dentro da mesma operação,
papel de importador, privacidade de campo, comentários, notificações.
Todos esses ficam para degraus seguintes, listados no
`docs/PONTIS-MAPA.md`.

## Autenticação: sessão ou JWT

O pedido desta sessão foi para propor a variante mais simples que
sirva, com os dois lados do compromisso (trade-off), e recomendar
uma. "JWT" (JSON Web Token) é um token assinado que o cliente guarda e
envia a cada chamada para provar quem é, sem o servidor guardar nada
sobre essa sessão. "Sessão" é o mecanismo mais antigo e mais usado
com Spring Security: o servidor guarda um registo da sessão em
memória, identificado por um cookie que o navegador envia
automaticamente em cada chamada.

**Opção A, recomendada: sessão (`HttpSession`, suporte nativo do
Spring Security).**

Vantagens:
- Nenhum código próprio de emissão, assinatura ou expiração de token
  para escrever. O Spring Security já faz login, logout e verificação
  de sessão sem configuração extra de infraestrutura.
- O navegador trata do cookie de sessão sozinho, o frontend não
  precisa de guardar nem de reenviar um token manualmente a cada
  chamada.

Desvantagens, com a forma de lidar com cada uma:
- **Sessão em memória não escala para várias instâncias do backend**
  (uma segunda instância não conhece as sessões criadas pela
  primeira). Não é um problema real hoje: o projeto corre numa única
  instância (confirmado em `.github/workflows/ci.yml`, um único job de
  testes, sem nenhuma infraestrutura de múltiplas instâncias
  referida em nenhum documento). Pelo princípio já seguido no resto
  do projeto (nenhuma abstração sem fricção real), não vale introduzir
  agora uma sessão partilhada (ex: Redis) para um problema que ainda
  não existe.
- **CORS com cookies exige `allowCredentials(true)`** na configuração
  atual (`src/main/java/com/eversonrubira/exporttracking/config/WebConfig.java`,
  que hoje só define `allowedOriginPatterns`/`allowedMethods`/
  `allowedHeaders`, sem `allowCredentials`). Sem isto, o navegador
  recusa-se a enviar o cookie de sessão em chamadas de origem
  diferente (o frontend em `localhost:5173`, o backend em
  `localhost:8080`, origens diferentes mesmo em desenvolvimento). O
  frontend (`frontend/src/api/client.ts`) também precisa de passar
  `credentials: 'include'` em cada `fetch`, que hoje não faz.
- **CSRF (Cross-Site Request Forgery, um ataque que usa o cookie de
  sessão de outra página sem o utilizador saber) fica ativo por
  padrão no Spring Security quando há sessão.** A receita
  recomendada para uma SPA (aplicação de página única, o caso do
  frontend React) é um repositório de token CSRF em cookie legível
  por JavaScript (`CookieCsrfTokenRepository.withHttpOnlyFalse()`), que
  o frontend lê e reenvia num cabeçalho em cada chamada que altera
  dados. Isto é trabalho novo de configuração, mas é a receita padrão
  documentada do próprio Spring Security, não uma invenção própria.

**Opção B: JWT.**

Vantagens: sem estado no servidor, escala para várias instâncias sem
nenhuma peça extra. Sem problema de CORS com cookies (o token viaja
num cabeçalho `Authorization`, não um cookie).

Desvantagens: precisa de código próprio para emitir, assinar e
validar o token (ou uma biblioteca extra, mais uma dependência);
"logout" não existe de forma simples (um token já emitido continua
válido até expirar, a não ser que se construa uma lista de tokens
invalidados, mais uma peça de estado que o JWT dizia evitar); o
frontend precisa de guardar o token nalgum lado (`localStorage` é
vulnerável a um ataque de XSS que leia o token).

**Recomendação: Opção A, sessão.** É mais simples para o que este
degrau precisa (uma única instância do backend, sem necessidade real
de logout imediato nem de escalar horizontalmente), e o custo
apontado (CORS com credenciais, CSRF) é configuração documentada do
próprio Spring Security, não código de segurança escrito à mão.

## Modelo de dados

### Tabela nova: `empresa`

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | chave primária |
| nome | VARCHAR(200) | obrigatório |
| criado_em | TIMESTAMP | obrigatório |
| atualizado_em | TIMESTAMP | obrigatório |

Sem mais campos por agora (nenhum número de identificação fiscal,
endereço, etc. foi pedido nem tem fricção real observada ainda:
princípio Hashimoto). Se vier a ser preciso, é um campo opcional
acrescentado depois, não uma razão para adiar este degrau.

### Tabela nova: `usuario`

| Campo | Tipo | Observação |
|---|---|---|
| id | UUID | chave primária |
| empresa_id | UUID | obrigatório, referencia `empresa.id` |
| nome | VARCHAR(200) | obrigatório |
| email | VARCHAR(200) | obrigatório, único |
| senha_hash | VARCHAR(100) | obrigatório, nunca a senha em texto puro (`BCryptPasswordEncoder` ou o `PasswordEncoderFactories.createDelegatingPasswordEncoder()` recomendado pelo próprio Spring Security) |
| criado_em | TIMESTAMP | obrigatório |
| atualizado_em | TIMESTAMP | obrigatório |

Decisão deste degrau: **um utilizador pertence a uma única empresa.**
Não existe pedido nem fricção real observada para um utilizador agir
em nome de mais do que uma empresa. Se isso vier a ser necessário
(uma pessoa a trabalhar para duas empresas distintas), é uma mudança
de modelo a tratar quando aparecer, não agora.

### Tabela alterada: `pedido`

Ganha uma coluna nova:

| Campo | Tipo | Observação |
|---|---|---|
| empresa_id | UUID | obrigatório depois da migração de dados (ver abaixo), referencia `empresa.id` |

A entidade Java `Pedido`
(`src/main/java/com/eversonrubira/exporttracking/pedido/Pedido.java`)
ganha o campo correspondente, seguindo o mesmo padrão já usado para
os outros campos (sem setter público, só através do `Builder` na
criação, porque a empresa dona de uma operação não muda depois de
criada, não há nenhum caso de uso descrito para isso).

### Migração dos dados já existentes

Antes desta migração, qualquer `pedido` já gravado na base de dados
não tem dono nenhum. A migração Flyway seguinte (seria `V6`, depois
da `V5__recusa_documento.sql` já existente) precisa de:

1. Criar a tabela `empresa`.
2. Inserir uma linha de empresa "padrão" (bootstrap), com um `id`
   fixo conhecido, representando a única empresa que existia de
   facto antes de haver conceito de empresa (o fornecedor que usou o
   sistema até aqui).
3. Criar a tabela `usuario`.
4. Acrescentar a coluna `pedido.empresa_id`, inicialmente sem
   restrição `NOT NULL`, preenchê-la com o `id` da empresa padrão
   para todas as linhas já existentes, e só depois aplicar a
   restrição `NOT NULL` e a chave estrangeira. Esta é a mesma técnica
   já usada nas migrações `V2` e `V3` existentes (colunas novas com
   um valor padrão temporário, removido depois de preenchido:
   `docs/SPEC.md`, secção "Modelo de dados", notas de `V2`/`V3`), não
   uma técnica nova.

**Risco explícito, não resolvido por esta migração:** a empresa
padrão fica sem nenhum utilizador capaz de fazer login, porque nunca
existiu uma senha real associada ao uso anterior do sistema. Criar um
utilizador com uma senha fixa dentro da própria migração SQL seria
guardar um segredo em texto no histórico do repositório, prática a
evitar mesmo sendo uma senha "temporária". A recomendação é um passo
manual, fora do Flyway, executado uma única vez depois da migração
(por exemplo, chamar o endpoint de registo associando-o a essa
empresa já existente, ou um pequeno script de administração corrido à
mão), não uma funcionalidade nova do produto para "criar o primeiro
utilizador de uma empresa já existente", porque não há fricção real
que justifique isso como feature permanente, é um problema de
migração única.

## Endpoints

### Novos

| Método | Rota | Autenticação | Efeito |
|---|---|---|---|
| POST | `/auth/registo` | Nenhuma (público) | Cria uma empresa nova e o primeiro utilizador dessa empresa, numa única chamada. Devolve sessão já autenticada |
| POST | `/auth/login` | Nenhuma (público) | Autentica por email/senha, cria sessão |
| POST | `/auth/logout` | Exige sessão | Termina a sessão |
| GET | `/auth/me` | Exige sessão | Devolve o utilizador autenticado e o nome da sua empresa (o frontend usa isto para saber quem está logado e mostrar/esconder o que for preciso) |

### Alterados

Todos os endpoints de `PedidoController` e `ChecklistController`
(listados por completo em `docs/PONTIS-MAPA.md`, secção "a", tabela
de endpoints) passam a:

1. **Exigir sessão.** Sem sessão válida, `401 Unauthorized`, não
   `403` (403 significa "sei quem é, mas não pode"; 401 significa
   "não sei quem é", e aqui ainda não se sabe).
2. **Verificar posse.** Qualquer endpoint com `{numeroPedido}` na
   rota passa a verificar se `pedido.empresa_id` é igual à empresa do
   utilizador autenticado. Se não for, `404 Not Found`, não `403`.

**Porque 404 e não 403 na verificação de posse:** devolver 403
confirmaria ao chamador que aquele número de pedido existe, só que
pertence a outra empresa. Devolver 404 ("não encontrado") não revela
se o pedido existe ou não para quem não é dono. É uma prática comum
de segurança (não confirmar a existência de um recurso a quem não
tem acesso a ele) e tem o custo adicional de zero: a mensagem de erro
já existente (`PedidoNaoEncontradoException`, `erro:
PEDIDO_NAO_ENCONTRADO`) serve exatamente para este caso também, sem
precisar de um código de erro novo.

`POST /pedidos` passa a associar automaticamente o novo pedido à
empresa do utilizador autenticado (não é um campo que o corpo do
pedido envie, é decidido pelo servidor a partir da sessão).

**Onde vive a verificação de posse:** no `PedidoService`
(`src/main/java/com/eversonrubira/exporttracking/pedido/PedidoService.java`),
não no controller. Cada método que recebe `numeroPedido` passa a
receber também o `empresaId` do utilizador autenticado, e a busca já
filtra por ambos (`buscarPorNumero(numeroPedido, empresaId)`), em vez
de buscar só por número e confiar que alguém, mais tarde, verificou a
posse. Pôr a regra no Service e não no Controller significa que
nenhuma chamada nova ao Service pode esquecer-se de verificar isto,
porque a própria assinatura do método já exige o dado.

### Sem alteração

`GET /pedidos/proximo-numero` continua a sugerir a partir de um
contador global, sem filtro de empresa. A decisão de separar esse
contador por empresa está listada como decisão em aberto no
`docs/PONTIS-MAPA.md`, secção "f.3", e não é resolvida por este
degrau (não há fricção ainda: com uma só empresa a usar o sistema
hoje, mesmo depois deste degrau, uma colisão de número sugerido
exige duas empresas ativas ao mesmo tempo, o que só acontece a
partir do degrau do convite).

## Quadro de autorização

| Quem | Pode fazer |
|---|---|
| Visitante sem conta | Registar uma empresa nova (`POST /auth/registo`), fazer login (`POST /auth/login`). Nada mais |
| Utilizador autenticado | Tudo o que já existe hoje (`PedidoController`, `ChecklistController`), mas só sobre pedidos cuja `empresa_id` seja a da sua própria empresa |

Não há ainda papéis dentro de uma empresa (ex: "gestor" versus
"operador"): qualquer utilizador de uma empresa pode fazer tudo o que
essa empresa pode fazer. Distinguir papéis dentro da mesma empresa
não foi pedido e não tem fricção observada.

## Frontend

Duas telas novas, consistentes com as três já existentes
(`frontend/src/pages/`):

- **Login** (`/login`): email, senha. Em caso de sucesso, redireciona
  para a lista de pedidos (`/`).
- **Registo** (`/registo`): nome da empresa, nome do utilizador,
  email, senha. Em caso de sucesso, já fica autenticado (a própria
  chamada de registo devolve sessão), redireciona para `/`.

`frontend/src/App.tsx` ganha uma verificação: se não houver sessão
válida (chamando `GET /auth/me` ao carregar a aplicação), as rotas
`/`, `/pedidos/novo` e `/pedidos/:numeroPedido` redirecionam para
`/login` em vez de mostrar a tela.

`frontend/src/api/client.ts` ganha `credentials: 'include'` em cada
chamada `fetch` (necessário para o cookie de sessão viajar entre
`localhost:5173` e `localhost:8080`, ver secção de autenticação
acima), e passa a tratar uma resposta `401` de forma distinta de
outros erros: redireciona para `/login` em vez de mostrar a mensagem
de erro normal no formulário ou no banner.

Sem tradução nova pedida além das chaves de texto das duas telas
novas (campos do formulário, mensagens de erro de login/registo),
seguindo a mesma estrutura já existente em
`frontend/src/i18n/locales/{pt,en,es}.json` (nenhum texto fixo
novo, conforme a regra já registada em `docs/STATUS.md`).

## Tabela de correlação: critério de aceitação × teste

| Critério | Tipo de teste | O que valida |
|---|---|---|
| Registo com nome de empresa, utilizador, email e senha válidos cria empresa e utilizador, devolve sessão autenticada | API | Novo: teste de controller de autenticação |
| Registo com email já usado por outro utilizador é rejeitado | API | Novo: teste de controller de autenticação |
| Login com credenciais corretas autentica (sessão criada); com senha errada ou email inexistente, rejeita sem revelar qual dos dois está errado | API | Novo: teste de controller de autenticação |
| Logout termina a sessão; uma chamada protegida depois do logout volta a exigir login | API | Novo: teste de controller de autenticação |
| Senha nunca é gravada em texto puro na base de dados | Unitário | Novo: teste do serviço de registo, verificando que o valor gravado não é igual ao valor enviado |
| Qualquer endpoint de `PedidoController`/`ChecklistController` sem sessão retorna 401 | API | Alterado: cada teste existente destes dois controllers passa a precisar de autenticar primeiro; a ausência de autenticação vira um caso de teste novo próprio |
| `POST /pedidos` autenticado associa o pedido à empresa do utilizador que o criou | Unitário + API | Alterado: `PedidoServiceTest`/`PedidoControllerTest`, caso novo |
| `GET /pedidos` só lista pedidos da empresa autenticada, nunca de outra empresa | API | Novo: duas empresas, cada uma só vê os seus |
| `GET /pedidos/{numero}` de uma empresa diferente da dona retorna 404, igual a um pedido inexistente | API | Novo |
| Pedidos gravados antes da migração ficam associados à empresa padrão criada pela migração, e essa empresa consegue listá-los depois de ter um utilizador associado | Integração (Postgres real, migração completa) | Novo |
| CORS permite cookie de sessão entre o frontend (`localhost:5173`) e o backend (`localhost:8080`) | Manual (`curl`/navegador), mesmo padrão já usado para confirmar o `encodedSolidusHandling` do `WebConfig` (`docs/SPEC.md`, secção "Numeração automática do pedido") | Novo |
| Tela de login e de registo funcionam de ponta a ponta no navegador, incluindo redirecionamento para `/login` quando a API responde 401 | Frontend (Vitest) + manual no navegador | Novo |

## Impacto nos testes atuais

**Quebram, porque chamam endpoints protegidos sem autenticar
primeiro:**

- `PedidoControllerTest` (todos os métodos que chamam
  `PedidoController`, hoje sem nenhuma autenticação).
- `ChecklistControllerTest` (mesma razão).
- `FluxoPedidoE2ETest` (usa REST Assured contra a API real, mesma
  razão).

A correção não é reescrever a regra de negócio destes testes, é
acrescentar um passo de autenticação antes de cada chamada (criar uma
empresa/utilizador de teste, autenticar, guardar a sessão/cookie, e
usá-la no resto do teste). O volume de testes afetados é grande (as
três classes somam mais de 60 métodos, conforme o inventário em
`docs/PONTIS-MAPA.md`), mas a mudança em cada um é mecânica e igual
em todos: não é uma reescrita de lógica de teste, é um passo de setup
comum, idealmente extraído para um método auxiliar partilhado em vez
de repetido 60 vezes.

**Quebram, porque a assinatura dos métodos do Service muda:**

- `PedidoServiceTest`: os métodos que hoje chamam
  `PedidoService.buscarPorNumero(numeroPedido)` ou equivalentes
  passam a precisar de passar também um `empresaId`. Testes que hoje
  criam um pedido sem empresa também precisam de ajuste (o `Builder`
  de `Pedido` passa a exigir `empresaId`).

**Não devem quebrar, mas precisam de um pedido de teste com
`empresaId` preenchido para continuar a compilar:**

- `PedidoRepositoryTest` (cria `Pedido` diretamente, vai precisar de
  uma empresa de teste para satisfazer a nova coluna obrigatória).

**Não devem ser afetados:**

- `ChecklistServiceTest` (regras do checklist em si, sem relação com
  posse de empresa).
- `PdfStatusServiceTest`, `PdfStatusMessagesParityTest` (recebem os
  dados já prontos como parâmetro, não fazem busca por posse).
- `Iso6346Test`, `ValidacaoCodigoMapperTest`,
  `GlobalExceptionHandlerTest`, `PedidoOcorrenciaRepositoryTest`,
  `PedidoSequenciaServiceTest` (sem relação com empresa/autenticação).
- Testes de frontend já existentes
  (`paridadeDeChaves.test.ts`, `erros.render.test.tsx`,
  `navegacaoNumeroPedido.test.ts`): não dependem de autenticação
  real, continuam a correr como estão.

## Riscos

- **Volume de testes a ajustar é o maior risco de execução deste
  degrau**, não a dificuldade técnica de nenhuma peça isolada (nem
  Spring Security, nem a migração, são complexos em si). Vale medir
  isto antes de começar: se o método auxiliar partilhado de
  autenticação de teste não for bem pensado desde o primeiro teste
  ajustado, o custo de repetir o ajuste 60 vezes cresce.
- **CORS com `allowCredentials(true)` não pode conviver com
  `allowedOrigins("*")`** (uma regra do próprio navegador, não do
  Spring): como o `WebConfig` atual já usa `allowedOriginPatterns`
  com origens específicas (`localhost:5173`,
  `https://*.app.github.dev`), não uma origem universal, não há
  conflito aqui, mas é um ponto a confirmar ao implementar, não a
  assumir.
- **A senha da empresa padrão migrada (ver "Migração dos dados já
  existentes") fica como passo manual, fora do controlo automático de
  migração.** Se esse passo manual não for executado, a empresa
  padrão fica com pedidos mas sem ninguém capaz de lhes aceder depois
  deste degrau.
- **Este degrau não resolve nada de partilha entre empresas** (isso
  são os degraus seguintes do `docs/PONTIS-MAPA.md`). Uma empresa
  sozinha com os seus próprios pedidos, sem convite nem importador,
  pode parecer pouco à primeira vista, mas é a base sem a qual nenhum
  dos degraus seguintes tem onde assentar.

## Fora deste degrau

- Convite por link, segunda empresa dentro da mesma operação, papel
  de importador (degraus 3 e 4 do `docs/PONTIS-MAPA.md`).
- Privacidade de campo ou documento (degrau 5).
- Registo de "visto em" (degrau 6).
- Comentários (degrau 7).
- Notificações (degrau 8).
- Encerramento com autorização dupla (degrau 9).
- Numeração automática por empresa em vez de global (decisão em
  aberto no `docs/PONTIS-MAPA.md`, secção "f.3", não resolvida aqui).
- Qualquer distinção de papéis dentro da mesma empresa (ex: gestor
  versus operador).
- Recuperação de senha esquecida, verificação de email, ou qualquer
  outro fluxo de gestão de conta além de registo, login e logout.
