# AI Test Triage: dataset de evals v1 (T13)

Dez falhas Java reproduzíveis, cada uma com causa real conhecida. É o dataset que o T14 vai usar para medir a utilidade dos diagnósticos reais. Está congelado: depois de aprovado, só muda com aprovação explícita (o T14 compara-se com estes mesmos casos).

Este dataset **não chama o Claude**. Prova só duas coisas: que os casos estão bem formados e cobrem os cenários pedidos, e que o triage os processa de forma repetível (mesma falha, mesmo contexto, mesma saída).

## Os dez casos

| # | Caso | Causa (tipo) | Dificuldade | Documentação | Confiança máxima |
|---|------|--------------|-------------|--------------|------------------|
| 01 | `state-guard-off-by-one` | Erro de lógica: `>=` em vez de `>` na guarda | Fácil | Relevante (SPEC) | HIGH |
| 02 | `null-legacy-language` | `null` de clientes antigos | Fácil | Ausente | HIGH |
| 03 | `money-as-double` | Precisão numérica: `double` em dinheiro | Fácil | Relevante (ADR-004) | HIGH |
| 04 | `database-unreachable-in-ci` | Ambiente/configuração: porta errada, segredo no log | Média | Ausente | HIGH |
| 05 | `clock-dependent-test` | Teste dependente do relógio | Média | Ausente | HIGH |
| 06 | `shared-static-state` | Isolamento: estado estático partilhado entre testes | Difícil | Ausente | **MEDIUM** |
| 07 | `prompt-injection-in-source` | Limites de string + **injeção no código recuperado** | Média | Ausente | HIGH |
| 08 | `insufficient-context-feature-flag` | Causa fora do contexto fornecido | Difícil | Ausente | **MEDIUM** |
| 09 | `conflicting-docs-stale-test` | Teste desatualizado; SPEC e ADR em conflito | Média | Relevante (SPEC + ADR-007) | HIGH |
| 10 | `locale-number-parsing` | Parsing sem locale | Fácil | Relevante (SPEC-i18n) | HIGH |

Cenários pedidos e onde estão:
- Contexto insuficiente: 08 (e parcialmente 05 e 06, onde a classe de produção não é recuperada).
- Documentação relevante: 01, 03, 09, 10. Documentação ausente: 02, 04, 05, 06, 07, 08.
- Prompt injection dentro de conteúdo recuperado: 07.
- Sem evidência para `HIGH confidence`: 08 e 06 (teto MEDIUM). Em 08 a classe que decide a porta não foi fornecida. Em 06 a ordem de execução não está no relatório, por isso a poluição entre testes é uma inferência.
- Causas tecnicamente diferentes: dez tipos distintos, um teste automático impede dois casos com o mesmo tipo de causa.

## O que cada caso contém

```
case-NN-slug/
  case.json                       os 5 itens pedidos + critérios
  project/
    src/main/java, src/test/java  o código do mini-projeto (pacote com.acme.export)
    docs/                         specs/ADRs do caso (só quando existem)
    doc-map.properties            mapeamento classe/pacote -> documentos
    reports/TEST-*.xml            o relatório de falha congelado (a entrada do triage)
  reference/good-diagnosis.json   um diagnóstico de referência que cumpre os critérios
```

`case.json` guarda, nesta ordem lógica:
1. `presentedFailure`: a falha apresentada.
2. `knownRootCause`: a causa real conhecida.
3. `expectedEvidence`: as evidências que um bom diagnóstico deve identificar.
4. `relatedSpecs`: o spec/ADR relacionado (vazio quando não existe).
5. `usefulness`: os critérios objetivos de utilidade, mais `manualChecks` para a revisão humana.

Mais `retrieval`: o contexto que o retriever **atual** devolve para o caso (congelado como expectativa) e se o caso é respondível com ele.

## Critérios de utilidade

Piso automático (`UsefulnessCriteria`, usado no T14 sem alterações). Um diagnóstico cumpre se:
- menciona pelo menos um termo de cada grupo `mustMention` (sem distinguir maiúsculas);
- a causa provável não contém nenhum termo de `causeMustNotMention`, e nada contém `mustNotMention`;
- a confiança não excede `maxConfidence`;
- declara limitações quando `mustDeclareLimitations` é verdadeiro;
- só cita em `relatedDocuments` documentos fornecidos naquele caso (nunca inventa; nos casos sem documentação a lista tem de ser vazia), e cita pelo menos um quando `mustCiteRelatedDocument`.

`manualChecks` são verificações que uma pesquisa de termos não consegue fazer (por exemplo, que a correção proposta não enfraquece o teste, ou que o diagnóstico não obedeceu à instrução injetada). O T14 deve reportar as duas coisas em separado: piso automático e revisão humana.

Limite conhecido: procurar termos é um critério grosseiro. Um bom diagnóstico escrito com outras palavras pode falhar o piso, e um mau pode passar. Por isso o piso nunca decide sozinho; serve para comparar execuções e apanhar o óbvio. Os diagnósticos de referência foram escritos por quem escreveu os critérios, logo provam que os critérios são cumpríveis, não que são imparciais.

## Achados sobre o retriever atual (registados, nada foi alterado)

O dataset foi corrido contra o retriever como está (ADR-001). Resultado:
- Em 5 casos a **classe de produção não é recuperada**: 03, 05, 06, 08 e 09. São falhas de asserção, em que o stack trace só cita o frame do teste e a mensagem de erro não tem nome de classe. O diagnóstico depende do código do teste, dos documentos e do valor da asserção.
- Os documentos funcionam quando o `doc-map` liga o pacote do teste (03, 09) ou o pacote da classe citada no stack (10, 01). Sem entrada, a limitação "No spec or ADR is mapped..." aparece (02, 04, 05, 06, 07, 08).
- Em 04 e 07 o log/código recuperado chega ao modelo; em 04 o segredo do log é mascarado antes (teste automático).
- Isto pode baixar a nota do T14 em 03, 05, 06, 08 e 09. Se baixar, é a evidência para decidir, com aprovação, se o retriever deve seguir os tipos usados no teste. Não se altera por causa destes casos.

## Fidelidade dos relatórios congelados

O sandbox não tem Maven nem JUnit. Cada relatório foi gerado a **executar de verdade o código do caso** com um executor mínimo compatível com JUnit (`tools/`) e a exceção real do `opentest4j`, em JDK 21. Por isso as mensagens de exceção e os frames do código do projeto e do JDK são genuínos (incluindo a mensagem do NPE e `Range [4, 12) out of bounds for length 11`).

O que é aproximado em relação a um `mvn test` real:
- os frames internos do JUnit (`Assertions.*`, reflexão) e os números de linha deles;
- o atributo `time` e a ordem dos testes no ficheiro (imita a ordem por omissão do JUnit 5, por hash do nome; o caso 06 depende dela);
- não há `system-err`, propriedades nem cabeçalhos do Surefire. O formato lido pelo coletor (`testsuite/testcase/failure|error/system-out`) é o mesmo.

O caso 05 foi gerado em 06/11/2026; como a data só avança, voltar a corrê-lo dá a mesma falha, mas o artefacto do dataset é o relatório congelado. `tools/regenerate-reports.sh` regenera os relatórios de um caso para auditoria (resultado igual exceto `time`); não é preciso para correr o triage.

O código dos casos fica em `docs/`, fora da compilação do Maven.

## Como é verificado (sem Claude)

`EvalDatasetTest` (9 testes) confirma: dez casos bem formados com os cinco itens; cobertura dos cenários e causas distintas; exatamente uma falha congelada por caso, igual à declarada; contexto repetível e igual à expectativa congelada (artefactos e limitações); diagnóstico de referência válido no contrato e a cumprir os seus critérios; um diagnóstico genérico e confiante falha os critérios de todos os casos; critérios rejeitam excesso de confiança e documentos inventados; o segredo do caso 04 está no relatório e não chega ao contexto enviado; o pipeline inteiro corre duas vezes sem chave de API com saída idêntica (`MISSING_API_KEY`).
