# Evidência: execução controlada do triage no CI (Bloco 3)

Commit de validação 308cc92 (já revertido): 7 testes que falham de propósito, sem `ANTHROPIC_API_KEY`.
Job `test`: https://github.com/EversonRubira/TrackCargo/actions/runs/42290404161/job/128142030780

Resultado dos passos do job (API do GitHub):

| Passo | Conclusão |
|---|---|
| Rodar suite completa (unitario + API + E2E) | failure (`Tests run: 302, Failures: 7`, `BUILD FAILURE`) |
| AI Test Triage - preparar classpath | success |
| AI Test Triage - diagnostico | success |
| Job `test` | **failure**, pela razão original (os testes) |

Saída do passo de diagnóstico, tal como está no log (timestamps do runner removidos):

```
| ==================== AI TEST TRIAGE ====================
| Failed tests found: 7
| Analyzed: 5 (V1 limit: 5 distinct failed tests per pipeline)
| Skipped: 2 (not analyzed because of the V1 limit)
|   - com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure6
|   - com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure7
| ==================== END AI TEST TRIAGE ====================
| provider: no API key in ANTHROPIC_API_KEY, request not sent
| ==================== AI TEST TRIAGE ====================
| TRIAGE FAILED
| Test: com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure1
| Reason: No API key is configured for the AI provider.
| Detail: ANTHROPIC_API_KEY is not set
| Provider attempts: 0
| The original test failure is unchanged and still decides the build result. Only the AI analysis is missing.
| ==================== END AI TEST TRIAGE ====================
| [triage-metrics] status=FAILED reason=MISSING_API_KEY attempts=0 durationMs=47 contextArtifacts=2 context=TEST_SOURCE:1,CITED_SOURCE:0,RELATED_SOURCE:0,DOCUMENT:1 contextChars=7427 tokensIn=-1 tokensOut=-1 test=com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure1
| provider: no API key in ANTHROPIC_API_KEY, request not sent
| ==================== AI TEST TRIAGE ====================
| TRIAGE FAILED
| Test: com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure2
| Reason: No API key is configured for the AI provider.
| Detail: ANTHROPIC_API_KEY is not set
| Provider attempts: 0
| The original test failure is unchanged and still decides the build result. Only the AI analysis is missing.
| ==================== END AI TEST TRIAGE ====================
| [triage-metrics] status=FAILED reason=MISSING_API_KEY attempts=0 durationMs=8 contextArtifacts=2 context=TEST_SOURCE:1,CITED_SOURCE:0,RELATED_SOURCE:0,DOCUMENT:1 contextChars=7427 tokensIn=-1 tokensOut=-1 test=com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure2
| provider: no API key in ANTHROPIC_API_KEY, request not sent
| ==================== AI TEST TRIAGE ====================
| TRIAGE FAILED
| Test: com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure3
| Reason: No API key is configured for the AI provider.
| Detail: ANTHROPIC_API_KEY is not set
| Provider attempts: 0
| The original test failure is unchanged and still decides the build result. Only the AI analysis is missing.
| ==================== END AI TEST TRIAGE ====================
| [triage-metrics] status=FAILED reason=MISSING_API_KEY attempts=0 durationMs=5 contextArtifacts=2 context=TEST_SOURCE:1,CITED_SOURCE:0,RELATED_SOURCE:0,DOCUMENT:1 contextChars=7427 tokensIn=-1 tokensOut=-1 test=com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure3
| provider: no API key in ANTHROPIC_API_KEY, request not sent
| ==================== AI TEST TRIAGE ====================
| TRIAGE FAILED
| Test: com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure4
| Reason: No API key is configured for the AI provider.
| Detail: ANTHROPIC_API_KEY is not set
| Provider attempts: 0
| The original test failure is unchanged and still decides the build result. Only the AI analysis is missing.
| ==================== END AI TEST TRIAGE ====================
| [triage-metrics] status=FAILED reason=MISSING_API_KEY attempts=0 durationMs=6 contextArtifacts=2 context=TEST_SOURCE:1,CITED_SOURCE:0,RELATED_SOURCE:0,DOCUMENT:1 contextChars=7427 tokensIn=-1 tokensOut=-1 test=com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure4
| provider: no API key in ANTHROPIC_API_KEY, request not sent
| ==================== AI TEST TRIAGE ====================
| TRIAGE FAILED
| Test: com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure5
| Reason: No API key is configured for the AI provider.
| Detail: ANTHROPIC_API_KEY is not set
| Provider attempts: 0
| The original test failure is unchanged and still decides the build result. Only the AI analysis is missing.
| ==================== END AI TEST TRIAGE ====================
| [triage-metrics] status=FAILED reason=MISSING_API_KEY attempts=0 durationMs=5 contextArtifacts=2 context=TEST_SOURCE:1,CITED_SOURCE:0,RELATED_SOURCE:0,DOCUMENT:1 contextChars=7427 tokensIn=-1 tokensOut=-1 test=com.eversonrubira.exporttracking.triage.ControlledFailureTest#controlledFailure5
| [triage-metrics] summary status=FAILED failures=7 analyzed=5 skipped=2 succeeded=0 failed=5 limit=5 durationMs=572
```
