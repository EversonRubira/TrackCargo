# PRD v2: espaço partilhado entre exportador e importador

## Nota de versionamento

Este documento substitui, nas partes indicadas, o `docs/PRD.md`
original (aqui chamado "PRD v1"). Optei por um ficheiro novo em vez
de editar o PRD v1 no próprio sítio, por duas razões: o PRD v1
descreve com precisão o que foi de facto construído e porque (as
decisões de audiência de utilizador único, por exemplo, foram
corretas para aquela fase, não um erro a corrigir), e o resto do
projeto já segue este padrão de histórico preservado em vez de
reescrito (as migrações Flyway `V1` a `V5`, o `docs/STATUS.md` que
acumula secções em vez de as substituir). Apagar o PRD v1 esconderia
o porquê de decisões passadas que continuam válidas noutras partes
do sistema.

Este documento assinala, secção a secção, o que do PRD v1 se mantém
sem mudança, o que é reescrito, e o que é novo. Nenhuma secção do PRD
v1 é apagada sem referência aqui.

O nome do produto novo ainda não está decidido. Este documento usa
"o produto novo" ou "Pontis (nome provisório)" só como etiqueta de
planeamento, nunca como nome definitivo. Nenhum nome foi alterado em
código nesta sessão.

## 1. Sumário executivo (reescrito)

**PRD v1 dizia:** sistema de rastreamento do ciclo de vida de pedidos
de exportação, do lado do fornecedor, de uso individual.

**Agora:** o produto passa a ser um espaço partilhado por operação de
exportação entre duas empresas, a exportadora e a importadora. O
exportador cria a operação (o que antes se chamava "pedido") e
convida a empresa importadora por um link. As duas empresas veem o
mesmo estado, os mesmos documentos partilhados e o mesmo histórico,
e podem comentar dentro da operação. Não é um mercado de negócios
(marketplace): o negócio já está fechado quando a operação é criada,
o produto só acompanha a execução depois do acordo comercial, exatamente
como o PRD v1 já descrevia a fase de negociação como "fora do sistema"
(PRD v1, seção 9).

A máquina de estados, as regras de transição e o checklist documental
descritos no PRD v1 (F01) mantêm-se como a base do produto. O que
muda é quem vê e quem pode agir sobre cada parte: antes, um único
operador via e fazia tudo; agora, duas empresas, com papéis diferentes
(exportador e importador), e privacidade por padrão.

## 2. Problema (mantém o do PRD v1, acrescenta um novo)

O problema original mantém-se, sem mudança (PRD v1, secção 2): no
comércio exterior de commodities, o rastreio de um processo de
exportação depende de memória e comunicação informal, sem registo
estruturado nem histórico que mostre responsabilidade.

**Problema novo, que o PRD v1 não cobria porque assumia um único
utilizador:** o importador (o comprador, do outro lado do negócio)
não tem hoje nenhuma forma de ver o estado do processo a não ser
receber um PDF gerado manualmente pelo exportador (PRD v1, F03). Não
há aviso automático de progresso, não há forma de o importador
aceitar ou recusar um documento diretamente, e não há registo de que
o importador viu ou respondeu a algo. O exportador também não tem
forma de saber se o importador já viu um documento antes de ligar a
perguntar.

## 3. Oportunidade (estende a do PRD v1)

A oportunidade original mantém-se (PRD v1, secção 3): uma máquina de
estados explícita com histórico auditável evidencia responsabilidade
sem precisar de regras de gravidade codificadas à parte.

**Extensão:** ao partilhar esse mesmo estado com o importador, em vez
de só o exportador o ver, o histórico auditável passa a servir às
duas partes ao mesmo tempo, sem duplicar comunicação informal por
telefone ou e-mail para cada atualização.

## 4. Audiência (reescreve a secção 4 do PRD v1)

**PRD v1 dizia:** "Uso individual pelo fornecedor... Sem multiusuário
no v1... O cliente final não é usuário do sistema, sem login, sem
acesso direto."

**Agora:** duas empresas por operação, cada uma com os seus próprios
utilizadores (pessoas que fazem login em nome dessa empresa):

- **Empresa exportadora**: cria a operação, convida a empresa
  importadora, envia documentos, confirma pagamentos recebidos,
  preenche dados logísticos.
- **Empresa importadora**: entra na operação por convite, vê o que
  for partilhado, aceita ou recusa documentos (ação exclusiva dela,
  não do exportador), comenta.

Qualquer empresa se pode registar no sistema (não há lista fechada de
empresas aprovadas). O papel de uma empresa (exportadora ou
importadora) não é uma propriedade fixa da empresa em si: a mesma
empresa pode ser exportadora numa operação e importadora noutra,
dependendo de quem cria o quê. Isto é descrito com mais detalhe em
`docs/PONTIS-MAPA.md`, secção "b) O que muda", tabela de tabelas
(entidade `operacao_participante`).

Fica em aberto, listado em `docs/PONTIS-MAPA.md`, secção "f", qual o
papel exato do importador em transições de estado além de aceitar ou
recusar documentos, e em `docs/PONTIS-MAPA.md`, secção "c), Link de
leitura sem conta" (proposta ainda não confirmada), se alguém sem
conta pode ver uma operação só em leitura.

## 5. Objetivos e métricas (estende os do PRD v1)

Os quatro objetivos do PRD v1 (secção 5) mantêm-se sem mudança:
ciclo de vida rastreável, nenhuma transição inválida aceite
silenciosamente, checklist documental como bloqueio de avanço, e
cobertura de teste real.

**Objetivos novos:**

- Qualquer empresa envolvida numa operação consegue ver o estado
  dessa operação sem depender de um PDF enviado manualmente.
- O sistema regista quando o importador abriu um documento e quando
  respondeu (aceitou ou recusou), não só o resultado final.
- Nenhum dado de uma operação é visível a uma empresa que não
  participa nela. Dentro de uma operação, nenhum dado não marcado
  como partilhado é visível ao importador.

## 6. Features

### F01: Ciclo de vida da operação de exportação (mantém, PRD v1)

A máquina de estados, as regras de transição, o checklist documental
de 4 itens obrigatórios mais 1 opcional, e os dois marcos de
pagamento descritos no PRD v1 (secção 6, F01) mantêm-se sem mudança
de regra de negócio. O que muda é o controlo de acesso sobre estas
ações, não a sequência em si: ver `docs/PONTIS-MAPA.md`, secção "b)
O que muda", tabela de endpoints, para o detalhe de qual papel pode
chamar cada ação.

Nomenclatura: o PRD v1 chama "pedido" à entidade central. Este
documento passa a chamar-lhe "operação", porque o termo descreve
melhor algo partilhado por duas empresas (um "pedido" sugere que
pertence a quem o fez, uma "operação" não). **Esta troca de palavra
é só deste documento de planeamento, não foi aplicada ao código**: a
tabela `pedido`, as classes Java e os endpoints `/pedidos` continuam
com esse nome, conforme pedido explícito de não alterar nomes nesta
sessão.

### F02: Painel (mantém o conceito do PRD v1, passa a exigir conta)

O painel web do PRD v1 (secção 6, F02) mantém as suas três telas
(lista, criar, detalhe). Deixa de ser utilizável sem autenticação: a
frase do PRD v1 "login fica condicionado ao item Multiempresa do
backlog v2, quando houver de fato mais de um usuário" deixa de se
aplicar, porque esse "mais de um usuário" é agora o próprio produto.

### F03: PDF de status (mantém o conceito do PRD v1, novo uso no encerramento)

O PDF de status descrito no PRD v1 (secção 6, F03) mantém-se como
está. Ganha um uso novo, descrito em `docs/PONTIS-MAPA.md`, secção
"c) O que é novo" (arquivo e encerramento): no fim de uma operação, as
duas empresas autorizam o encerramento e o PDF final do histórico é
gerado nesse momento, não só a pedido avulso como hoje.

### F04: Empresas, contas e autenticação (nova, degrau 1)

Qualquer empresa pode registar-se e criar utilizadores próprios, que
fazem login para agir em nome dessa empresa. Antes desta feature, não
existe nenhum conceito de empresa ou utilizador no sistema (PRD v1,
secção 4, confirmado em código: nenhuma dependência de segurança
existe hoje). Esta é a feature-fundação de todas as outras do produto
novo, da mesma forma que F01 era a fundação do PRD v1: sem conta não
há forma de uma operação pertencer a alguém, nem de distinguir
exportador de importador.

Especificação técnica completa em `docs/SPEC-degrau-1.md` (degrau 1
da ordem de construção do `docs/PONTIS-MAPA.md`, secção "d").

### F05: Operação partilhada (nova, descrita em `docs/PONTIS-MAPA.md`, implementação em specs futuras)

Convite por link, papéis por operação, privacidade por padrão,
registo de quando o importador viu algo, comentários, notificações e
encerramento com autorização dupla. Descrita com detalhe em
`docs/PONTIS-MAPA.md`, secções "b" e "c". Não especificada
tecnicamente neste documento: cada parte desta feature corresponde a
um degrau da ordem de construção (`docs/PONTIS-MAPA.md`, secção "d",
degraus 2 a 9), com a sua própria spec quando chegar a vez.

## 7. User stories (acrescenta às do PRD v1)

As sete user stories do PRD v1 (secção 7) mantêm-se válidas do ponto
de vista do exportador, mas duas precisam de ajuste de vocabulário
("pedido" passa a "operação") e de ter o pressuposto de conta
acrescentado (ex: "autenticado como a empresa exportadora").

**Novas, do ponto de vista do importador:**

* Como importador, quero registar a minha empresa e aceitar um
  convite, para entrar numa operação sem precisar de contactar o
  exportador fora do sistema para pedir acesso.
* Como importador, quero ver só os documentos e dados que o
  exportador partilhou comigo, para não depender de perguntar o que
  está disponível.
* Como importador, quero aceitar ou recusar um documento diretamente,
  com motivo quando recuso, para que o exportador saiba a decisão sem
  depender de um telefonema.
* Como importador, quero comentar dentro de uma operação, ligado a um
  documento ou a um estado específico, para que a conversa fique
  registada no contexto certo.

**Nova, do ponto de vista do exportador:**

* Como exportador, quero saber quando o importador viu um documento,
  para distinguir "ainda não olhou" de "está a demorar a decidir".

## 8. Consumes / Provides (estende o do PRD v1)

F01 continua a ser a feature-fundação para as regras do ciclo de
vida. F04 (empresas e contas) passa a ser a feature-fundação de
acesso: todas as outras features, incluindo F01, F02 e F03, passam a
depender de F04 para saber quem está a chamar e o que essa pessoa
pode ver ou fazer. F05 consome F04 (precisa de empresas e utilizadores
já a existir) e estende F01 com papéis e privacidade.

## 9. Fora de escopo (reescreve a secção 9 do PRD v1)

**PRD v1 dizia:** "Autenticação e autorização multiusuário... segue
fora de escopo, sem mudança; fase 2, decisão explícita de adiamento,
não esquecimento."

**Agora:** essa frase deixa de se aplicar. A "fase 2" que o PRD v1
previa é precisamente este documento. Autenticação e multiutilizador
passam a ser o núcleo do produto (F04), não mais um item adiado.

As restantes exclusões de escopo do PRD v1 mantêm-se sem mudança:
geração de ficheiro para os documentos do checklist em si, cálculo de
valores/moeda de pagamento, negociação comercial fora do sistema,
módulo de triagem por IA.

**Novas exclusões de escopo, explicitamente não decididas e não
construídas nesta leva** (listadas como perguntas abertas em
`docs/PONTIS-MAPA.md`, secção "f", e repetidas aqui para não se
perderem):

* Link de leitura sem conta (proposta ainda não confirmada).
* Retenção de uma operação arquivada (quanto tempo se guarda depois
  de encerrada).
* Lembretes automáticos se o importador não responder.
* Quem paga pelo uso do produto.
* Granularidade de privacidade além do que o degrau que a introduzir
  precisar (campo a campo versus só por documento).
* Quem pode reabrir um documento já aceite pelo importador.
* Numeração automática por empresa ou global.
* Canal de notificação (e-mail versus só dentro da aplicação).

Nenhuma destas foi decidida nesta sessão, e nenhuma delas é assumida
como resolvida pelo resto deste documento.
