# AI Test Triage - notas de implementação

Registo das decisões aprovadas pelo Everson em 06/11/2026 antes do Bloco 1. Não alteram `spec.md`, `architecture.md` nem os ADRs: são escolhas de implementação dentro deles.

## Decisões aprovadas
1. Nome: "Pontis" só nos textos deste pacote. Sem renomeação geral do código ou do repositório.
2. ADRs do triage ficam em `adr/` desta pasta e citam-se como "ADR-00X do AI Test Triage".
3. Código em Java simples, sem Spring, no pacote `com.eversonrubira.exporttracking.triage` (em `src/main`, sem beans). Um teste ArchUnit (`IsolamentoTriageTest`) impede dependências entre `triage` e `conta`, `pedido` e `config`, e impede `triage` de usar Spring. "Triage ativado/desativado" (T01) é a flag `triage.enabled` lida pelo ponto de entrada.
4. CI: passo com `if: failure()` no mesmo job do `mvn test`, para reaproveitar os relatórios Surefire. Chave `ANTHROPIC_API_KEY` criada pelo Everson.
5. Sem dependências novas: `java.net.http.HttpClient`, Jackson e o parser XML do JDK. Modelo por propriedade, default `claude-sonnet-5-5`. Sem tool use forçado (a resposta é JSON em texto, validado).
6. Latência: arquitetura mantida. Pior caso sequencial: 5 falhas x 2 tentativas x 30 s, ou seja, cerca de 5 minutos de espera do fornecedor. Sem timeout global na V1. O T12 deve fornecer os dados para rever esta decisão.
7. `doc-map.properties`: mapeamento explícito classe ou pacote para documentos, com limite configurável de conteúdo por artefacto. Documentação relevante sem mapeamento gera uma limitação visível no contexto.
8. Convenções: identificadores do pacote `triage` em inglês (como em `tasks.md`), saída `AI TEST TRIAGE` / `TRIAGE FAILED` em inglês, comentários e documentação em português europeu.

## Detalhes de implementação dentro do aprovado
- Uma entrada de `doc-map.properties` pode terminar em `#Titulo` para escolher só uma secção de um ficheiro Markdown (o `docs/SPEC.md` tem 89 KB; sem isto o limite de caracteres cortaria quase tudo).
- "Falhas distintas": identificadas por classe + nome do teste, tal como vêm do Surefire. Só se removem repetições exatas. Não há agrupamento (ADR-004).

## Limitações do ambiente de desenvolvimento (06/11/2026)
- O sandbox bloqueia o Maven Central (403) e não tem daemon Docker. Não houve `mvn test` local. A evidência oficial dos testes é o CI do PR. Localmente só foi compilado o código com `javac` e executado um shim mínimo do JUnit, que não conta como evidência.
- Os logs do GitHub Actions não são legíveis a partir do sandbox, só o resultado de cada job.

## Progresso
- Bloco 1 (T01 a T05): implementado em 06/11/2026. Código em `src/main/java/com/eversonrubira/exporttracking/triage/`, testes em `src/test/java/.../triage/` e `.../arquitetura/IsolamentoTriageTest.java`.
- Bloco 2 (T06 a T09): implementado em 06/11/2026, sem nenhuma chamada ao Claude real (os testes usam um servidor HTTP local). Prompt versionado com fronteira de confiança (`provider/TriagePrompt`), cliente com a política do ADR-003 (`provider/ClaudeClient`), validação do contrato (`validation/`) e renderer (`rendering/TriageRenderer`).
- Pendentes: Bloco 3 (T10 a T12), Bloco 4 (T13 a T15).

## Decisões de detalhe do Bloco 2 (dentro do aprovado)
- Segredos: `ClaudeClient.diagnose` só aceita `SanitizedContext`, que só o `SecretFilter` cria. O log do cliente só tem estado HTTP, nomes de campo e contagens; nunca corpo de pedido nem de resposta. Fecha o ponto pendente do T05 para o *provider input*; o *observability output* fica para o T12.
- Falha de ligação ao provider (recusada, DNS, corte) conta como erro transitório e repete-se, como o ADR-003 pede para "provider indisponível".
- `stop_reason=max_tokens` conta como resposta estruturalmente inválida (repete-se uma vez), mesmo que o JSON apareça completo por acaso.
- O validador aceita um único cerco ```json à volta do objeto e ignora campos desconhecidos. Texto antes ou depois do objeto é rejeitado (verificado em `JsonSupport`, sem depender do default do Jackson). Limites: 10 itens por lista, 2000 caracteres por texto, para proteger o log do CI.
- O validador só verifica a forma. Uma resposta bem formada com a causa errada passa; isso mede-se nos evals (T13/T14). Há um teste a documentar esta fronteira.
- Config nova: `triage.provider.base-url` (só https, ou http para localhost), `max-output-tokens` (2000), `api-key-env` (ANTHROPIC_API_KEY) e `retry-delay-millis` (1000).
- Renderer: toda a linha começa por `| `; o runner reconhece `::cmd::` e `##[cmd]` mesmo depois de tirar espaços iniciais, e o prefixo impede que o texto do modelo seja lido como comando. Caracteres de controlo e invisíveis são removidos antes de mascarar segredos.
- `TriageApplication.utf8(...)`: o stdout do Java segue a locale do sistema; fixa-se UTF-8 para acentos não saírem como `?` (decisão aprovada sobre o encoding, tratada no T09).
