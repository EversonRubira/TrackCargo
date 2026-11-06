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
