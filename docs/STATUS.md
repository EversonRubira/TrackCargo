# Status — TrackCargo

## Última atualização
22/set/2026 — i18n-infra concluído (Passo 1 + Bloco 3, frontend),
branch `feature/i18n-infra` (requisito irrevogável: pt-BR/en/es).
PR ainda não aberto — aguardando confirmação final do usuário.

## Regra de projeto a partir de agora
**Nenhum texto de interface fixo no código.** Todo texto visível ao
usuário (backend: PDF de status; frontend: as 4 telas) vem de uma
chave de tradução/mensagem — nunca uma string literal hardcoded na
lógica de apresentação. Exceções explícitas: texto livre digitado
pelo usuário (motivo da recusa, produto, consignee, cliente etc. —
nunca traduzido) e o nome do produto ("TrackCargo").

## i18n-infra — Passo 1 (correção de tradução em inglês) concluído

Antes do Bloco 3: `status.enviado`/`tabela.ultimoEnvio` no bundle
`en` usavam "Shipped"/"Last shipped" — em comércio exterior isso
significa **embarcado** (estado `EMBARCADO`, já traduzido como
"Shipped" em `estado.embarcado`), confundindo com "documento
enviado". Trocado pra "Sent"/"Last sent". `estado.embarcado`
continua "Shipped" (único lugar do bundle onde "embarque do navio"
de fato aparece). Revisão das demais chaves `en` com o mesmo olhar
(enviado/embarcado, recusado/rejeitado): sem outra colisão —
"Documentation sent"/"Original documents sent" já usavam "sent";
"Rejected"/"awaiting resend" já eram consistentes entre
`status.recusado` e `recusa.template`. Commit separado e pequeno
(`087e61a`), conforme pedido.

```
Tests run: 131, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

## i18n-infra — Bloco 3 (frontend) concluído

Último bloco da internacionalização. Cobre as 4 telas
(`App.tsx`/`ListaPedidos.tsx`/`CriarPedido.tsx`/`DetalhePedido.tsx`),
tradução de erro pelo código, `Intl` pra data/número/moeda, e o link
do PDF passando o idioma ativo. Detalhes completos em
`docs/SPEC.md`, seção "Frontend multilíngue — i18n-infra Bloco 3".

**Dependências novas:**
- `i18next`, `react-i18next`, `i18next-browser-languagedetector`
  (produção) — biblioteca de i18n decidida no Bloco 0.
- `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`
  (dev, só teste) — o projeto não tinha ambiente DOM configurado pro
  Vitest ainda; os testes de erro pedidos exigem renderização real,
  não só teste de função pura. `vite.config.ts` ganhou
  `test: { environment: 'jsdom', setupFiles: [...] }`.

**Resumo do que mudou:**
- `src/i18n/` novo: `index.ts` (init do i18next + detector de
  navegador + persistência em `localStorage`), `locales/{pt,en,es}.json`
  (mesma árvore de chaves nos 3), `erros.ts` (`traduzirErro()`,
  tradução de erro pelo código com interpolação), `intl.ts`
  (`formatarData`/`formatarNumero`/`formatarMoeda` via `Intl` nativo).
- `App.tsx`: seletor de idioma no header (`<select>` chamando
  `i18n.changeLanguage`, persistência automática via
  `i18next-browser-languagedetector`).
- `ListaPedidos.tsx`/`CriarPedido.tsx`/`DetalhePedido.tsx`: as ~67
  strings fixas + os 32 valores de enum (`PedidoEstado`,
  `TipoDocumento`, `Incoterm`, `FormaPagamento`, `Moeda`) viraram
  chave de tradução. Texto livre (motivo, produto, consignee, cliente)
  **não** foi tocado — continua exatamente como o usuário digita.
- `CriarPedido.tsx`/`DetalhePedido.tsx`: erro de validação aparece
  junto ao campo (`Campo`/`FormularioLogistica`/diálogos de motivo
  ganharam prop `erro?`), o resto no banner — usando o `campo`
  completo do backend (`condicoesComerciais.percentualParcial`) como
  chave de busca.
- `api/types.ts`: `ErrorResponse` com o shape novo do Bloco 1
  (`parametros`/`campos`, sem `estadoAtual`/`estadoSolicitado`
  top-level). `api/client.ts`: `urlStatusPdf(numeroPedido, idioma)`
  monta `?lang=`.
- `DetalhePedido.tsx`: link do PDF passa `i18n.language` como `lang`.

**Testes novos:** `paridadeDeChaves.test.ts` (compara as chaves dos 3
JSONs, achatadas); `erros.render.test.tsx` (2 testes de renderização
real com `@testing-library/react`, usando os JSONs **reais**
capturados da API nos Blocos 1/2 — colados abaixo, não reinventados
— um erro de validação com dois campos inválidos em `CriarPedido` e
um `TRANSICAO_INVALIDA` em `DetalhePedido`).

**Achado durante a implementação:** o jsdom do ambiente de teste
reporta `navigator.language` como `"en-US"` — sem `localStorage`
prévio, os testes detectavam `"en"` em vez do `"pt"` padrão da
aplicação. Os testes de renderização forçam
`i18n.changeLanguage('pt')` num `beforeEach` — comportamento de
ambiente de teste, não bug da detecção real (em um navegador de
verdade reflete o idioma de fato configurado ali, que é o
comportamento desejado).

## Evidência real — frontend (saída bruta, `frontend/`)

```
$ npx tsc -b --force
EXIT=0   (sem saída — limpo)

$ npm run lint
> oxlint
src/pages/DetalhePedido.tsx:410:5: warning react(set-state-in-effect): Calling setState synchronously within an effect can trigger cascading renders help: Effects should synchronize React with external systems. Calling setState synchronously inside an effect starts another render and is usually unnecessary. Derive the value during render, initialize state directly, or update it from the event that caused the change. Use an effect only when synchronizing with an external system.
EXIT=0

$ npm run build
> tsc -b && vite build
vite v8.3.0 building client environment for production...
✓ 130 modules transformed.
dist/index.html                   0.45 kB │ gzip:   0.29 kB
dist/assets/index-rXJ1zd8J.css   13.04 kB │ gzip:   3.46 kB
dist/assets/index-Ykx6_5Ut.js   348.22 kB │ gzip: 107.33 kB
✓ built in 618ms
EXIT=0

$ npm run test
> vitest run
 Test Files  3 passed (3)
      Tests  5 passed (5)
   Duration  1.86s
EXIT=0
```

**Backend (`mvn test`, sem mudança de código nesta parte — só
confirmando estado atual):**
```
Tests run: 131, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

**Validação visual não foi feita nesta sessão.** Tentei um smoke test
com Playwright (Chromium pré-instalado no ambiente), mas o pacote
Node `playwright` não está instalado no projeto nem globalmente — só
o binário do browser. Não adicionei a dependência só pra essa
checagem pontual (gasto de dependência sem uso contínuo). A cobertura
real de "isso renderiza certo" vem dos 2 testes de renderização com
JSON real (`erros.render.test.tsx`) + `tsc`/`build` limpos — a
confirmação visual final (seletor de idioma no header, layout dos
erros por campo, formatação de moeda) fica por conta do usuário.

## Estado de saída (i18n-infra completo — Blocos 0, 1, 2, 2b, Passo 1, 3)
Fechado: contrato de erro granular por código (backend), PDF
multilíngue com padrão de data explícito (backend), tradução em
inglês corrigida, frontend inteiro traduzido (pt/en/es) com detecção
de navegador + seletor manual persistido, erro por código com
interpolação e campo-vs-banner, `Intl` pra data/número/moeda, link do
PDF com idioma ativo. `docs/SPEC.md` atualizado (seção nova "Frontend
multilíngue"). Regra "nenhum texto de interface fixo" registrada
acima. **PR ainda não aberto** — abrindo a seguir, com a evidência
completa (backend + frontend) na descrição, sem merge.

---

## i18n-infra — Bloco 2b (padrão de data explícito) concluído

Ajuste pedido antes de liberar o Bloco 3: o Bloco 2 usava
`DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)`, que
delega ao dado de locale CLDR do JDK em execução — não é garantia
nossa, pode mudar de versão pra versão. Trocado por um **padrão
explícito guardado no próprio bundle** (chave `formato.data`):
`pt`/`es` = `dd/MM/yyyy HH:mm`, `en` = `dd MMM yyyy HH:mm` (mês
abreviado, sem a ambiguidade dia/mês do formato numérico puro em
inglês). O `Locale` só decide o nome do mês (`MMM`) quando o padrão
usa letras — não afeta `pt`/`es`, que são 100% numéricos.

`PdfStatusMessagesParityTest` cobre a chave nova automaticamente (o
teste compara `keySet()` dos 3 bundles, sem lista de chaves
hardcoded). Teste novo `PdfStatusServiceTest.dataFixaFormatadaComPadraoExplicitoPorIdiomaSemDependerDoLocaleDaJvm`
usa uma data fixa (`LocalDateTime.of(2026, 9, 21, 14, 34)`, não
`now()`) comparada contra a string exata esperada nos 3 idiomas —
testa o mecanismo (`ResourceBundle` + `DateTimeFormatter.ofPattern`)
diretamente, não através de `ChecklistDocumento`/`PedidoTransicao`,
porque nenhuma dessas entidades tem construtor pra data fixa (ambas
usam `LocalDateTime.now()` internamente).

**Confirmação pedida — contagem de `@Size` sem `message=` (Bloco 2):**
eram **12**, não 13 (a contagem de 13 do Bloco 0 incluía 2 linhas de
comentário que o grep pegou por engano, já corrigido na resposta do
Bloco 1). Nenhum dos 12 já tinha `message=` antes — confirmado via
`git show` do commit anterior ao Bloco 2 (`81c1b45`), grep no arquivo
inteiro de cada DTO.

## i18n-infra — Bloco 2 (PDF multilíngue) concluído

Segundo bloco da internacionalização (Bloco 1, contrato de erro
granular por código, já aprovado). Este bloco: só backend, só o PDF
de status. Nenhum texto de interface do frontend foi tocado ainda
(isso é Bloco 3).

**Acréscimo de manutenção incluído neste bloco:** os 12 `@Size` sem
`message=` (gap do Bloco 0/1 — caíam no texto padrão do Hibernate
Validator, em inglês) ganharam `message=` em português, coerente com
o resto das anotações. Não muda `codigo`/`parametros` do contrato de
erro (Bloco 1) — só o texto de depuração (`mensagem`).

**Backend (detalhes completos em `docs/SPEC.md`, seção "PDF de status
multilíngue"):**
- `PdfStatusService.gerar(...)` ganhou parâmetro `String idioma`;
  `GET /pedidos/{numero}/status.pdf?lang=` (default `pt`, valores
  aceitos `pt`/`en`/`es`; qualquer outro valor cai em `pt` sem lançar
  exceção).
- `ResourceBundle` (não `MessageSource`) — `PdfStatusService` não tem
  contexto Spring, injetar `MessageSource` só pra isso quebraria essa
  pureza. Arquivos `src/main/resources/i18n/pdf-status-messages_{pt,en,es}.properties`.
  ~20 strings fixas traduzidas + rótulo legível pros 9 estados de
  `PedidoEstado` (resolve o gap real da barra de progresso, que desde
  a Fase 4/F03 sempre mostrou `PedidoEstado.name()` cru).
  `chaveEstado(PedidoEstado)`/`chaveDocumento(TipoDocumento)`: `switch`
  sem `default`, estado/tipo novo sem chave vira erro de compilação.
- Datas via padrão explícito por idioma (`formato.data` no bundle) —
  ver Bloco 2b acima pra por que substituiu `ofLocalizedDateTime`.
- `PdfStatusMessagesParityTest` novo: compara `keySet()` dos 3
  bundles, trava chave esquecida num idioma antes de virar
  `MissingResourceException` em produção.

**Testes novos:** `PdfStatusServiceTest` (+4 com o Bloco 2b: geração
nos 3 idiomas com rótulos-chave, idioma desconhecido cai em `pt`,
selo de cancelamento traduzido, data fixa com padrão explícito) — os
13 testes existentes migrados pra nova assinatura de `gerar()`
(parâmetro `idioma`). `PdfStatusMessagesParityTest` novo (1).
`PedidoControllerTest` (+1: `?lang=` repassado ao service).

## Resultado da suíte completa (mvn test) — 2 rodadas (Bloco 2b)
```
Rodada 1: Tests run: 131, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
Rodada 2 (banco recriado do zero): Tests run: 131, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

## Evidência real — segunda metade do PDF (status calculado, recusa, selo de cancelado)

Dois pedidos reais via API rodando: `PO-EVID-MIX` (INVOICE recusado
com motivo, PACKING_LIST aceito, BL enviado, CERTIFICADO_SANITARIO
pendente) e `PO-EVID-CANCEL` (transicionado pra `CANCELADO`). Texto
extraído (PyMuPDF) de cada `GET /status.pdf?lang={pt,en,es}`:

```
=== mix pt ===
Desde: 21/09/2026 14:48
Documento / Status / Último envio / Aceito em
Invoice
Recusado, aguardando reenvio -
-
Recusado em 21/09/2026 14:48 (envio de 21/09/2026 14:48): Assinatura do responsavel ausente na ultima pagina
Packing list
Aceito
21/09/2026 14:48
21/09/2026 14:48
BL
Enviado
21/09/2026 14:48
-
Certificado sanitário
Pendente
-
-

=== mix en ===
Since: 21 Sep 2026 14:48
Document / Status / Last shipped / Accepted on
Invoice
Rejected, awaiting resend
-
-
Rejected on 21 Sep 2026 14:48 (sent on 21 Sep 2026 14:48): Assinatura do responsavel ausente na ultima pagina
Packing list
Accepted
21 Sep 2026 14:48
21 Sep 2026 14:48
BL
Shipped
21 Sep 2026 14:48
-
Sanitary certificate
Pending
-
-

=== mix es ===
Desde: 21/09/2026 14:48
Documento / Estado / Último envío / Aceptado el
Factura
Rechazado, esperando reenvío -
-
Rechazado el 21/09/2026 14:48 (enviado el 21/09/2026 14:48): Assinatura do responsavel ausente na ultima pagina
Lista de empaque
Aceptado
21/09/2026 14:48
21/09/2026 14:48
BL
Enviado
21/09/2026 14:48
-
Certificado sanitario
Pendiente
-
-

=== cancel pt ===  PEDIDO CANCELADO
=== cancel en ===  ORDER CANCELLED
=== cancel es ===  PEDIDO CANCELADO
```

Rótulos `Cliente:`/`Consignee:`/`Produto:`/`Incoterm:` (pt),
`Customer:`/`Consignee:`/`Product:`/`Incoterm:` (en),
`Cliente:`/`Consignee:`/`Producto:`/`Incoterm:` (es) confirmados nos
6 PDFs (`Consignee`/`Incoterm` não traduzem — nomes técnicos do
comércio exterior, iguais nos 3 idiomas por design). **Confirmado
também: o motivo da recusa (texto livre, digitado em PT pelo
usuário) não é traduzido em nenhum idioma** — comportamento correto,
texto livre nunca é traduzido (regra que vale desde já e será
reforçada no Bloco 3 pro frontend). A data (`21/09/2026 14:48`
pt/es, `21 Sep 2026 14:48` en) confirma o padrão explícito do Bloco
2b funcionando em produção, não só no teste unitário.

## Estado de saída (i18n-infra Bloco 2 + 2b)
Fechado: PDF de status multilíngue, endpoint com `?lang=`, gap dos
rótulos de estado resolvido, `@Size` com mensagem de depuração em PT,
padrão de data explícito por idioma (não mais dependente de
`ofLocalizedDateTime`/CLDR do JDK), suíte 131/131 em duas rodadas,
`docs/SPEC.md` atualizado. Commit em `feature/i18n-infra`, ainda sem
push/PR — aguardando confirmação antes do Bloco 3 (frontend:
i18next, tradução de erros por código, Intl pra data/número/moeda,
remoção de todo texto fixo de interface).

---

## Endurecimento de validação de campos — Bloco 1 (backend) concluído

## Endurecimento de validação de campos — Bloco 1 (backend) concluído

Motivação: pedidos aceitavam valores sem sentido (`moeda: "Yen"`,
`precoAcordado: 1` etc., `numeroContainer: "Plastico"`) — só existia
validação de presença/tamanho, nunca de domínio/formato. Sem dado de
produção, só dados de teste locais — endurecido direto, sem migração.

**Backend (detalhes completos em `docs/SPEC.md`, seção "Endurecimento
de validação de campos"):**
- `Moeda` (novo enum fechado `USD, EUR, BRL`) substitui `String` livre
  em `Pedido.moeda`/`CondicoesComerciaisRequest`/`PedidoResponse`
  (`@Enumerated(EnumType.STRING)`, coluna continua `VARCHAR(3)`).
- `@Positive` em `quantidade` e `precoAcordado`; `@DecimalMin("0")` +
  `@DecimalMax("100")` (inclusive) em `percentualParcial`.
- `Iso6346` (`pedido/validacao/`, classe pura sem Spring) implementa
  normalização + validação de formato e dígito verificador ISO 6346.
  `@NumeroContainerIso6346` (Bean Validation) no
  `AtualizarLogisticaRequest.numeroContainer` chama a mesma classe;
  `PedidoService.atualizarDadosLogisticos()` normaliza antes de
  persistir — uma única implementação do algoritmo pras duas partes.
  Confirmado que só o endpoint de logística edita esse campo (não há
  duplicação a resolver).
- Novo `@ExceptionHandler(HttpMessageNotReadableException.class)` em
  `GlobalExceptionHandler`: cobre enum inválido no body (`"Yen"`) e
  JSON malformado, gerando a mensagem por campo a partir de
  `Enum.values()` (ampliar um enum depois não exige tocar no handler)
  e uma mensagem genérica pra qualquer outro erro de parse — nunca
  expõe texto interno do Jackson. Mesmo formato de 400 já existente.

**Testes novos:** `Iso6346Test` (8, puro), +12 em `PedidoControllerTest`
(moeda/incoterm inválidos no body, JSON malformado, `@Positive`,
faixa de percentual, ISO 6346 no PATCH de logística), +1 em
`PedidoServiceTest` (normalização persistida), +1 cenário em
`FluxoPedidoE2ETest` (criar válido, depois cada valor inválido → 400
na API real). 6 arquivos de teste pré-existentes ajustados só pela
mudança de tipo de `moeda` (`String` → `Moeda`); 3 números de
container fixture pré-existentes trocados por valores ISO 6346
genuinamente válidos (os antigos tinham dígito verificador errado —
achado pela nova validação, não causado por ela).

## Resultado da suíte completa (mvn test) — 2 rodadas
**121/121 verde nas duas rodadas** (banco recriado do zero na
segunda, Flyway sem migration nova nesta feature — schema V1→V5
inalterado, só validação de aplicação).

**Bloco 1 aprovado com 2 confirmações extras:** os 3 fixtures de
container corrigidos (dígito verificador ISO 6346 errado) foram
listados um a um (valor antigo/novo/teste) e `Iso6346Test` ganhou
`referenciaExternaCsqu3054383ValidoCsqu3054384Invalido` — par de
referência externa da norma, como literais independentes
(`CSQU3054383`/`CSQU3054384`), não derivados nem calculados por esta
implementação.

## Bloco 2 (frontend) concluído

- **`moeda` vira `<select>`** com `USD`/`EUR`/`BRL` (`Moeda`/`MOEDAS`
  novos em `types.ts`), mesmo padrão de `incoterm`/`formaPagamento` —
  sem digitação livre, sem duplicar a regra do backend (só restringe
  a entrada à mesma lista fechada).
- **`CondicoesComerciaisRequest.moeda`/`PedidoResponse.moeda`**
  passaram de `string` pro tipo `Moeda` em `types.ts` — reflete o
  enum Java real, não um campo que o backend nunca mandou.
- **Campo de container continua texto livre** (não dá pra restringir
  a um `<select>`, é alfanumérico) — o erro do backend já aparecia no
  banner existente (`ApiError.message`, mecanismo anterior a esta
  feature, sem componente novo). Corrigido um bug real exposto por
  esta feature: `FormularioLogistica` guardava `numeroContainer` em
  `useState` inicializado só uma vez a partir da prop `pedido`, então
  depois de salvar um valor em minúsculas/com hífen o campo continuava
  mostrando o texto bruto digitado, não o valor normalizado que a API
  de fato gravou. Ganhou um `useEffect` resincronizando o estado local
  sempre que `pedido.ciaMaritima`/`pedido.numeroContainer` mudam (novo
  warning oxlint `set-state-in-effect`, mesma categoria dos 2
  pré-existentes já tolerados no projeto — é o padrão de sincronizar
  estado local de formulário com uma prop que muda por fetch externo).
- **Banner de erro**: nenhuma mudança de mecanismo — já exibia
  `ApiError.message` (o `mensagem` do `ErrorResponse`), que o backend
  já monta pronto como `"campo: mensagem; campo2: mensagem2"`. Não
  havia parsing por campo no cliente pra criar nem remover.
- **`types.ts`**: única mudança de shape foi `moeda: string` →
  `moeda: Moeda` nos dois DTOs que já tinham esse campo — nada
  adicionado que o backend não mande de fato.

## Resultado do frontend (tsc/lint/build/test)
```
npx tsc -b --force        → sem saída (limpo)
npm run lint (oxlint)     → 3 warnings set-state-in-effect (2 pré-existentes
                             + 1 novo do useEffect de sincronização acima,
                             mesma categoria já tolerada, não corrigidos)
npm run build             → vite build OK, 97 módulos, sem erro
npm run test (vitest)     → 2/2 passando (navegacaoNumeroPedido.test.ts,
                             sem teste novo pra esta feature)
```
**Validação visual não foi feita** — não abri navegador nesta sessão;
confirmar visualmente o `<select>` de moeda e o container normalizado
reaparecendo no campo fica por conta do usuário.

## Estado de saída (endurecimento de validação de campos — completo)
Fechado: Bloco 1 (backend) e Bloco 2 (frontend) implementados e
commitados em `feature/validacao-campos`, suíte backend 121/121 em
duas rodadas, frontend com `tsc`/`build`/`test` limpos (só warnings
de lint pré-existentes na mesma categoria). `docs/SPEC.md` atualizado
nas duas pontas (seção de validação + seção "Criar pedido"/banner de
erro). PR ainda não aberto — aguardando confirmação do usuário antes
de abrir.

---

## PDF de status mostra documentos e recusas

Consumindo a recusa de documento (PR #22, mergeado): o PDF de status
ao cliente (`GET /pedidos/{numero}/status.pdf`) agora tem uma seção
"Documentos" logo depois da barra de etapas, com status calculado
(Pendente/Enviado/Aceito/Recusado, aguardando reenvio) e o motivo de
cada recusa, em ordem cronológica, mesmo depois de reenviado e
aceito. **Só backend** — frontend não foi tocado.

**Backend:**
- `PdfStatusService.gerar(...)` ganhou 2 parâmetros novos: `List<ChecklistDocumento>
  checklist` e `Map<TipoDocumento, List<PedidoOcorrencia>> recusasPorDocumento`.
  Continua puro (sem repositório/service injetado).
- `ChecklistService.buscarRecusasPorDocumento(numeroPedido)` novo —
  reaproveita `buscarRecusas()` já existente (nenhuma query nova), só
  entram no mapa os tipos com pelo menos uma recusa.
- `PedidoController.statusPdf()` ganhou `ChecklistService` no
  construtor e compõe os 4 dados (pedido/histórico/checklist/recusas)
  antes de chamar `gerar()` — comentário no código aponta que a
  futura automação de e-mail vai precisar da mesma composição, e que
  vale extrair pra um método reutilizável quando isso existir (hoje
  só tem um chamador).
- Coluna "Documento" usa rótulos legíveis ("Invoice", "Packing list",
  "Certificado sanitário" etc.) via `switch` expression **sem
  `default`** — tipo de documento novo sem rótulo vira erro de
  compilação. Barra de etapas **não foi alterada** (continua com o
  nome do enum).
- Recusas aparecem numa célula com `colspan` total da tabela (não
  coluna estreita), uma por linha, em ordem cronológica.

**Achado importante durante os testes:** o `PdfTextExtractor` do
próprio OpenPDF 3.0.5 transforma qualquer acento/cedilha do português
em `"?"` ao extrair texto de uma fonte padrão não embutida (Helvetica)
sem CMap `ToUnicode` — confirmado com renderização real (PyMuPDF) que
**o PDF gerado está correto**, só a extração automatizada que falha.
Emoji é omitido silenciosamente; caractere de alfabeto não-latino
(cirílico/CJK) depende de fallback de fonte do sistema operacional
onde o PDF é gerado (não é garantia da biblioteca). Nenhuma mudança
de fonte/encoding foi feita em `PdfStatusService` pra "corrigir"
isso — embutir uma fonte Unicode de verdade resolveria mas é escopo
maior que esta feature pediu (fica registrado como follow-up).

Cheguei a adicionar o Apache PDFBox como dependência só de teste
achando que extrairia melhor, mas **comparei os dois extratores no
mesmo PDF antes de decidir manter isso — PDFBox tem exatamente a
mesma limitação** (também vira `"?"` pra acento/cedilha). Não
resolvia nada, então removi a dependência do `pom.xml`: os testes
usam só o `PdfTextExtractor` do OpenPDF, que já é dependência de
produção.

**Testes novos:** `PdfStatusServiceTest` novo (12 testes, PDF real
gerado e extraído via `PdfTextExtractor` do OpenPDF — sem Spring, sem
mock, sem dependência nova): status por
documento com as datas certas, recusa sobrevivendo a reenvio/aceite,
duas recusas em ordem, documentos ordenados pelo enum mesmo com lista
fora de ordem, rótulos legíveis, motivo de 500 caracteres sem
truncar, motivo com acentos (documenta a limitação de extração acima),
motivo com emoji/cirílico/CJK (documenta que não trava a geração e o
resto do texto continua legível, sem travar numa expectativa
dependente de ambiente), e um teste que gera
`target/sample-status.pdf` (não commitado) pra avaliação visual.
`PedidoControllerTest` atualizado pra nova assinatura de `gerar()` +
mock de `ChecklistService`. `FluxoPedidoE2ETest` (+1): enviar →
recusar → `GET status.pdf` (motivo aparece) → reenviar → `GET
status.pdf` de novo (status "Enviado", recusa antiga ainda listada).

## Resultado da suíte completa (mvn test) — 2 rodadas
**96/96 verde nas duas rodadas** (banco recriado do zero na segunda,
Flyway sem migration nova nesta feature — mesma V5 da recusa).

Validação visual: PDF de exemplo (`target/sample-status.pdf`, 2
documentos recusados, um com motivo de 500 caracteres) gerado e
enviado pro usuário avaliar — renderizado como imagem antes do envio
pra confirmar layout, espaçamento e quebra de linha da célula
colspan.

## Estado de saída (PDF de status mostra documentos e recusas)
Fechado: seção "Documentos" implementada (status calculado, rótulos
legíveis, recusas com motivo e data em ordem cronológica), suíte
completa 96/96 em duas rodadas, PDF de exemplo gerado e enviado pro
usuário, `docs/SPEC.md` atualizado (seção nova, tabela de correlação,
limitação de extração documentada). PR aberto, aguardando
revisão/merge — não faço merge sozinho.

**Próximo passo natural (fora de escopo, registrado pra não se
perder):** se a fricção de extração de acentos virar um problema
real (ex: outro consumidor automatizado do texto do PDF), a correção
é embutir uma fonte Unicode de verdade (TrueType + Identity-H) em vez
da Helvetica padrão — resolve a extração e amplia o alfabeto
suportado, mas exige bundle de arquivo de fonte no projeto.

---

## Recusa de documento do checklist

Nova feature: documento do checklist enviado mas não aceito pode ser
recusado com motivo — reaproveita o mecanismo já existente de
reabertura/`pedido_ocorrencia` (nenhum estado ou abstração nova, só
fricção real: o PDF de status ao cliente, tarefa futura e separada,
vai precisar desse histórico).

**Backend:**
- Migration `V5__recusa_documento.sql`: `pedido_ocorrencia` ganha
  `tipo_documento` e `envio_recusado_em` (nullable, só preenchidos em
  `RECUSA_DOCUMENTO`) — é o que o PDF futuro vai ler pra montar a
  lista de recusas por documento (data do envio recusado, data da
  recusa via `ocorrido_em` já existente, motivo via `descricao` já
  existente).
- `TipoOcorrencia.RECUSA_DOCUMENTO` novo. `ChecklistDocumento.recusar()`
  (pacote-privado, zera `enviadoEm`). `ChecklistService.recusar()`:
  exige `enviadoEm` preenchido e `aceitoEm` nulo, reusando
  `DocumentoNaoEnviadoException`/`DocumentoJaAceitoException` (mesma
  pré-condição já coberta por elas em `enviar()`/`aceitar()` — nenhuma
  exceção nova). `trim()` no motivo antes de salvar.
- **Decisão confirmada com o dono do domínio antes de implementar:
  `recusar()` nunca transiciona `pedido.estado`** — mesma lógica de
  "não há como desfazer um navio que já saiu" já aplicada a
  `reabrirAposAceite()` pós-embarque, só que mais direta aqui (a
  recusa nunca desfaz uma aceitação).
- `ChecklistService.buscarRecusas(numeroPedido, tipo)` (histórico
  cronológico por documento, via `PedidoOcorrenciaRepository`) —
  **sem endpoint REST próprio ainda**, decisão também confirmada: o
  único consumidor prático é o PDF futuro, expor uma rota agora seria
  antecipar necessidade que não existe.
- `PATCH /pedidos/{numero}/documentos/{tipo}/recusar`, body
  `{ "motivo": "..." }`, `204 No Content` (mesmo padrão de
  `enviar`/`aceitar`/`reabrir`).

**Frontend:**
- `recusarDocumento()` em `client.ts`, botão "Recusar" em
  `DetalhePedido.tsx` ao lado do "Aceitar" (mesma condição:
  `enviadoEm` preenchido, `aceitoEm` nulo) — espelha o
  `BotaoReabrir` já existente, com `maxLength=500` +
  validação de obrigatório no cliente. Erro do backend sobe pelo
  banner de erro já usado por todas as outras ações (nenhum mecanismo
  novo). `types.ts` **não mudou** — nem `ReabrirDocumentoRequest` era
  usado ali, então `recusar` seguiu o mesmo caminho sem tipo novo.
- Confirmado (investigação pedida antes do Bloco 2): a tela não expõe
  `TipoOcorrencia`/`PedidoOcorrencia` em lugar nenhum hoje — a seção
  "Histórico de transições" consome `GET /historico`
  (`pedido_transicao`, mudança de estado), não `pedido_ocorrencia`.
  Nenhuma lista de rótulos de tipo de ocorrência existia pra
  atualizar; decisão confirmada de não expor isso agora.

**Testes novos (backend):** `ChecklistServiceTest` (+6),
`ChecklistControllerTest` (+6), `PedidoOcorrenciaRepositoryTest` novo
(Postgres real — prova a query cronológica filtrando tipo/documento e
que recusas antigas sobrevivem a reenvio/aceite posterior),
`FluxoPedidoE2ETest` (+2: enviar→recusar→reenviar com nova data;
recusar documento já aceito → 409 real). Sem teste de frontend novo —
`recusarDocumento()` segue o mesmo contrato de `reabrirDocumento()`,
já sem cobertura própria antes desta feature.

## Resultado da suíte completa — 2 rodadas
**Backend (`mvn test`): 83/83 verde nas duas rodadas** (banco recriado
do zero na segunda, Flyway reaplicou V1→V5).

**Frontend (`npx tsc -b --force`, `npm run lint`, `npm run build`,
`npm run test`): tudo limpo** (só os 2 warnings pré-existentes de
`set-state-in-effect`, não relacionados).

**Validação do fluxo real (via `curl`, mesmo contrato HTTP que
`client.ts`/`DetalhePedido.tsx` usam — backend de pé, Postgres real,
sem mock):** criei um pedido, enviei o INVOICE (`enviadoEm` preenchido
→ coluna mostraria "Sim", botões Aceitar+Recusar visíveis), recusei
(`enviadoEm` volta a `null` → "Não", `pedido.estado` continuou
`DOCUMENTACAO_ENVIADA`, `GET /historico` sem nenhuma entrada nova —
confirma que a recusa não mexe em transição de estado nem na tela de
histórico), reenviei e aceitei, e confirmei os dois casos de erro que
o banner do frontend exibiria: recusar documento já aceito → 409
`DOCUMENTO_JA_ACEITO`, motivo vazio → 400 `VALIDACAO_INVALIDA`. **Não
abri navegador nenhum** — não cliquei no botão de verdade nem vi o
componente renderizado; essa validação visual final fica por conta do
usuário.

## Estado de saída (recusa de documento do checklist)
Fechado: os 2 blocos (backend + frontend) implementados e commitados
em `feature/recusa-documento`, suíte completa verde nos dois lados,
`docs/SPEC.md` atualizado (tabela `pedido_ocorrencia`, regra de
negócio da recusa, endpoint, mapeamento de exceção, tabela de
correlação). PR aberto, aguardando revisão/merge — não faço merge
sozinho.

**Próximo passo natural (fora de escopo desta feature, registrado pra
não se perder):** o PDF de status ao cliente (F03) vai precisar
consumir `ChecklistService.buscarRecusas()` — hoje só acessível via
service/repository, sem endpoint REST. Quando essa tarefa for aberta,
decidir se o PDF chama o service direto (mesmo padrão já usado por
`PdfStatusService`, que não passa por HTTP) ou se compensa expor um
endpoint de consulta antes disso.

---

## Numeração automática (sugerida) do número do pedido

Nova feature: `numeroPedido` pode seguir o padrão `NNNNN/AAAA` (5
dígitos com zero à esquerda, ano de 4 dígitos), sugerido automaticamente
na tela "Criar pedido" — campo continua editável.

**Backend:**
- `V4__pedido_sequencia.sql` — tabela `pedido_sequencia(ano INT PK,
  proximo_numero INT NOT NULL)`, uma linha por ano, criada sob demanda.
- `PedidoSequenciaService` novo: `sugerirProximoNumero()` só espia o
  contador (`SELECT`, nunca cria/incrementa) — usado por `GET
  /pedidos/proximo-numero` (endpoint novo). `reservarSeCorresponder(numeroPedido)`
  é chamado dentro de `PedidoService.criar()` (mesma transação) e faz
  um CAS atômico (`UPDATE ... WHERE ano = :ano AND proximo_numero =
  :numero`): só avança o contador se o número criado bater exatamente
  com o valor atual da sequência daquele ano. Efeito: número editado
  manualmente pelo usuário não mexe no contador; abandonar o formulário
  sem criar não pula número; duas criações concorrentes pro mesmo
  número não colidem (a que perder a corrida não afeta linha nenhuma,
  sem exception).
- `PedidoSequenciaRepository` (JPA, queries nativas):
  `buscarProximoNumero`, `garantirAno` (`INSERT ... ON CONFLICT DO
  NOTHING`), `incrementarSeCorresponder` (o `UPDATE` do CAS).
- `PedidoController` ganhou `GET /pedidos/proximo-numero` →
  `ProximoNumeroResponse(numeroPedidoSugerido)`.

**Frontend:**
- `CriarPedido.tsx`: `useEffect` no mount chama
  `buscarProximoNumeroSugerido()` (novo em `client.ts`) e pré-preenche
  `numeroPedido` — falha na chamada não impede o cadastro manual.

**Bug de navegação corrigido junto (pré-existente, exposto de vez pela
nova feature — não fazia sentido gerar automaticamente um número que
quebra a própria aplicação):** `numeroPedido` com barra virava dois
segmentos de rota em vez de um. Dois pontos client-side sem
`encodeURIComponent` (`client.ts` já fazia isso em toda chamada de API,
só a navegação React Router estava faltando): `ListaPedidos.tsx` (link
da lista) e `CriarPedido.tsx` (redirect pós-criação). **Isso sozinho
não bastava** — confirmado testando manualmente com `curl` que o
Tomcat embarcado rejeita `%2F` na URL com 400 por padrão. `WebConfig`
ganhou um `WebServerFactoryCustomizer<TomcatServletWebServerFactory>`
setando `encodedSolidusHandling=passthrough` no connector — Tomcat
repassa a URL codificada pro Spring sem decodificar antes do roteamento,
Spring casa a rota pelos segmentos originais e só decodifica o valor de
cada `@PathVariable` depois. Confirmado com `curl` antes/depois (400 →
200) em `GET /pedidos/00001%2F2026` e `.../historico`.

**Testes novos:**
- `PedidoSequenciaServiceTest` (7, integração contra Postgres real):
  sugestão sem histórico, sugestão não altera contador, reserva avança
  quando bate com o sugerido, número manual fora do padrão não cria
  sequência, número diferente do atual não avança, reset por ano
  (dois anos simulados avançando independentemente), duas reservas
  concorrentes pro mesmo número (`ExecutorService` + `CountDownLatch`)
  só uma avança o contador.
- `PedidoServiceTest`: `criarGeraChecklistZeradoETransicaoInicial`
  ganhou a verificação de que `criar()` chama
  `pedidoSequenciaService.reservarSeCorresponder(...)`.
- `PedidoControllerTest` (+1): `proximoNumeroRetorna200ComSugestaoDoService`.
- Frontend: `navegacaoNumeroPedido.test.ts` (Vitest, novo — primeiro
  teste do frontend, `npm run test`) prova o encode/decode de um único
  segmento de rota pra número com barra, sem precisar de jsdom/Testing
  Library. CI do frontend ganhou o passo `npm run test` antes do build.

`docs/SPEC.md` atualizado: tabela `pedido_sequencia` (V4), endpoint
novo, seção "Numeração automática do pedido" completa (design do CAS,
reset por ano, concorrência), nota do bug de navegação + fix de duas
camadas (frontend + Tomcat), seção "Criar pedido" (F02) menciona o
pré-preenchimento, tabela de correlação critério×teste com as linhas
novas.

## Resultado da suíte completa — 2 rodadas
**Backend (`mvn test`): 67/67 verde nas duas rodadas** (6
`ChecklistServiceTest` + 15 `PedidoServiceTest` + 3
`PedidoRepositoryTest` + 9 `ChecklistControllerTest` + 23
`PedidoControllerTest` + 7 `PedidoSequenciaServiceTest` (novo) + 4
`FluxoPedidoE2ETest`):
- Rodada 1: suíte completa normal.
- Rodada 2: banco recriado do zero (`DROP DATABASE` + `CREATE
  DATABASE`), forçando o Flyway a reaplicar V1→V4.

**Frontend (`npm run lint` + `npm run build` + `npm run test`): verde
nas duas rodadas** (2 testes novos em `navegacaoNumeroPedido.test.ts`).

Nota de ambiente: Postgres 16 nativo usado (mesmas credenciais/porta do
`application.yml`) — `docker compose` não testado nesta sessão, mesma
restrição de rede de sessões anteriores.

Validação manual extra (fora da suíte automatizada, via `curl` com o
backend de pé): `POST /pedidos` com `numeroPedido: "00001/2026"` →
`GET /pedidos/proximo-numero` confirmou avanço pra `00002/2026`;
`GET /pedidos/00001%2F2026` e `.../historico` confirmados 200 só depois
do ajuste do `WebConfig` (400 antes).

## Estado de saída (numeração automática + fix de navegação)
Fechado: endpoint novo, `PedidoSequenciaService` com CAS atômico
testado (incremento, reset por ano, concorrência), frontend
pré-preenchendo o campo, bug de navegação corrigido nas duas camadas
(frontend + Tomcat), `docs/SPEC.md`/`docs/STATUS.md` atualizados,
suíte completa (backend + frontend) verde em duas rodadas. PR aberto,
aguardando revisão/merge — não faço merge sozinho.

---

## Ajuste de CORS pra Codespaces

## Ajuste de CORS pra Codespaces

`WebConfig` trocou `allowedOrigins("http://localhost:5173")` por
`allowedOriginPatterns("http://localhost:5173",
"https://*.app.github.dev")` — Codespaces expõe a porta do Vite numa
URL pública que muda a cada sessão
(`https://<nome-aleatorio>-5173.app.github.dev`), e
`allowedOrigins` não aceita wildcard (só `allowedOriginPatterns`
resolve isso). `localhost:5173` continua liberado pra dev local fora
de Codespace.

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
