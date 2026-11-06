# AI Test Triage V1: verification pack (T15)

Mapa dos critérios de aceitação da `spec.md` para a evidência que os sustenta. Os nomes são métodos de teste em `src/test/java/com/eversonrubira/exporttracking/triage/` (JUnit 5). Os "cenários BDD" pedidos no T15 estão escritos como testes JUnit com nomes que dizem o cenário, não como ficheiros Gherkin.

Evidência oficial dos testes: CI no GitHub Actions. No commit `d9a1e8c` o job `test` passou com 320 testes e 0 falhas. Os commits de T15 só tocam em documentação.

## Critérios de aceitação

| CA | Evidência | Estado |
|---|---|---|
| CA-01 pedido de diagnóstico criado após falha | `TriagePipelineTest.aSuccessfulDiagnosisIsRenderedAndMeasured`; `FailureCollectorTest.completeReportYieldsTheExpectedNormalizedInput`; execução controlada no CI (`evidence/bloco-3-ci-controlled-run.md`) | Cumprido |
| CA-02 `AI TEST TRIAGE` com os cinco elementos RF-04 no log | `TriageRendererTest.successSnapshot`; `emptyOptionalSectionsSayTheyAreEmptyInsteadOfDisappearing`; `rendered.txt` dos 10 casos em `evals/baseline-v1/results/` | Cumprido |
| CA-03 causa provável com evidência | `DiagnosisValidatorTest.missingEvidenceIsRejected`; `successSnapshot`; 10/10 respostas reais com evidência (`diagnosis.json`) | Cumprido |
| CA-04 contexto insuficiente declarado, sem certeza | `ContextBuilderTest.partialContextKeepsWhatWasFoundAndDeclaresWhatWasNot`; `emptyContextIsDeclaredNotHidden`; `TriageRendererTest.theHypothesisAndHumanDecisionWordingIsAlwaysPresent`; caso 08 real: LOW, hipótese, limitações declaradas | Cumprido |
| CA-05 nenhuma modificação automática do projeto | `TriagePromptTest.requestHasNoToolsSoTheModelCannotAct`; o código de `triage/` só escreve no stdout (sem escrita de ficheiros, processos nem git); nenhum caso do baseline alterou ficheiros | Cumprido por construção e por inspeção; sem teste que falhe se alguém adicionar escrita |
| CA-06 timeout, falha ou saída inválida preservam a evidência | `ClaudeClientTest.timeoutIsRetriedOnceAndThenReported`; `twoInvalidResponsesEndAsInvalidResponseNamingTheBrokenFields`; `TriagePipelineTest.anUnavailableProviderEndsAsTriageFailedAfterTwoAttemptsAndIsMeasured`; `TriageRendererTest.triageFailedSnapshotKeepsTheOriginalFailureVisible`; `CiWorkflowTest.theTestStepStaysAuthoritativeAndIsNeverMadeTolerant` | Cumprido |
| CA-07 texto injetado é dado | `TriagePromptTest.injectedTextOnlyEverAppearsInsideTheUntrustedDataBlock`; `fakeDelimitersInTheDataCannotCloseOrOpenTheRealBlock`; `TriageRendererTest.noFieldOfASuccessfulDiagnosisCanInjectAWorkflowCommand`; caso 07 real: PASS | Cumprido |
| CA-08 avaliação com pelo menos 10 casos | `evals/dataset-v1/` (10 casos, `EvalDatasetTest`), `evals/baseline-v1/BASELINE-REPORT.md` | Cumprido, com as limitações do relatório |

## Métrica de sucesso (spec secção 10)

| Condição | Resultado |
|---|---|
| Pelo menos 8/10 avaliações ajudam a identificar o próximo passo | Automático estrito 7/10, **meta não atingida**. Revisão humana 10/10 (um revisor, 10 casos, uma execução por caso) |
| Nenhum caso causa modificação automática | Cumprido |
| Segredos conhecidos não expostos | Cumprido (testes de fixtures e scan do CI e do repositório limpos) |
| Diagnósticos incertos ficam hipóteses | Cumprido nos casos 04, 05, 06 e 08 (revisão humana) |

A secção 10 não define quem decide "ajudam". O desenho aprovado do T14 fixou o número estrito automático como a meta; por isso o estado honesto da meta é "não atingida", com a revisão humana reportada à parte.

## Itens de verificação do `CLAUDE-HANDOFF.md`

| Item | Evidência |
|---|---|
| Unitários: collector, retrieval, context builder, filtro de segredos, validador | `FailureCollectorTest`, `DeterministicRetrieverTest`, `DocMapIntegrityTest`, `ContextBuilderTest`, `SecretFilterTest`, `DiagnosisValidatorTest` |
| Integração com provider simulado, sem chamadas reais nos testes | `FakeProvider`; `TriagePipelineTest`; `ClaudeClientTest` (servidor HTTP local); `BaselineRunnerTest` |
| Cenário: triage com sucesso | `TriagePipelineTest.aSuccessfulDiagnosisIsRenderedAndMeasured` |
| Cenário: falha do provider | `TriagePipelineTest.anUnavailableProviderEndsAsTriageFailedAfterTwoAttemptsAndIsMeasured`; `withoutAnApiKeyTheTriageFailsGracefullyAndSaysSo` |
| Cenário: tentativa de prompt injection | `TriagePromptTest` (bloco de dados não confiável); `TriagePipelineTest.aHostileTestNameCannotInjectWorkflowCommandsThroughTheMetricsOrOverview`; caso 07 |
| Cenário: contexto em falta | `ContextBuilderTest.partialContextKeepsWhatWasFoundAndDeclaresWhatWasNot`; `DeterministicRetrieverTest.unresolvableCitedClassesAreDeclaredNotGuessed`; caso 08 |
| Cenário: mais de cinco falhas | `TriagePipelineTest.exactlyFiveOfSevenFailuresAreAnalyzedAndTheRestAreListedAsSkipped` |
| Falha controlada no GitHub Actions com `AI TEST TRIAGE`, preservando o resultado original | `evidence/bloco-3-ci-controlled-run.md` (run 42290404161: job falha pelos testes, triage com `MISSING_API_KEY`) |
| Dataset de 10 casos e baseline documentado | `evals/DATASET-V1.md`, `evals/baseline-v1/` |
| Mapa critérios para evidência | este ficheiro |

## Controlos de segurança verificados

- Filtro de segredos obrigatório por tipo (`ClaudeClientTest.noPublicMethodOfTheClientAcceptsAnUnfilteredContext`).
- Payload sem credencial e log do cliente sem corpos (`thePayloadSentToTheProviderHasNoCredential...`, `credentialsNeverAppearInLogsOrOutcomesEvenWhenTheServerEchoes`).
- Nenhum segredo conhecido no pipeline completo, em sucesso e falha (`TriagePipelineTest.noKnownSecretReaches...`).
- Workflow sem `pull_request_target` e com a chave só no passo do triage (`CiWorkflowTest`).
- Scan de segredos na corrida real do baseline e nos resultados antes de entrarem no repositório.

## Latência e uso observados (baseline real)

10 chamadas, 0 retries, média 10.7 s, máximo 15.1 s (limite de 30 s), 22 089 tokens de entrada, 13 402 de saída, US$0.1782. Por falha analisada, o triage acrescenta cerca de 10 s e menos de US$0.02 ao CI, com o máximo de 5 falhas por pipeline.

## Desvios e riscos por resolver

1. **Meta automática de 8/10 não atingida** (7/10); três falsos negativos por formato de `relatedDocuments` num critério congelado. Qualquer correção é V2 ou exige aprovação e novo baseline.
2. **Passo do triage no `ci.yml` com chave real:** a integração de produção foi exercitada no CI apenas sem chave (`MISSING_API_KEY`). As chamadas reais correram em workflows temporários e no `BaselineRunner` (código de teste), que usam os mesmos componentes do pipeline. Um build real a falhar com o secret configurado, no `ci.yml`, não foi observado.
3. **CA-05** não tem teste automático que bloqueie futuras escritas no código do triage.
4. **Limitações estatísticas** do baseline: ver `evals/baseline-v1/BASELINE-REPORT.md`.
5. **Ramos temporários** `smoke/triage-first-run` e `baseline/t14-run` ainda existem no remoto; o Everson apaga-os manualmente.

## Recomendações para V2 (separadas da V1)

Não implementadas. Todas condicionadas a evidência futura: formato de `relatedDocuments` (prompt ou critério), retrieval da classe de produção em testes de asserção, nova medição com mais casos e várias execuções por caso, e só então RAG, clustering ou remediação.
