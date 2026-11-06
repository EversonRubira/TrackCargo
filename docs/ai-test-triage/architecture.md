# AI Test Triage - Architecture

**Status:** Approved design for V1  
**Project:** Pontis

## 1. Architectural goal
Implement the approved spec as a read-only CI diagnostic capability. Test execution remains authoritative; AI triage is an auxiliary analysis layer.

## 2. Component decomposition
1. **Java Test Runner** - runs Java tests and preserves the real PASS/FAIL result.
2. **Failure Collector** - normalizes failed test name, error, stack trace, and relevant log excerpt.
3. **Context Builder** - assembles the smallest useful context package.
4. **Deterministic Retriever** - selects explicitly related test/source files and deterministically mapped specs/ADRs.
5. **Secret Filter** - masks credentials/tokens before provider input or telemetry.
6. **Claude Client** - calls Claude with trusted instructions plus untrusted evidence/context.
7. **Response Validator** - validates the logical structured-output contract.
8. **Triage Renderer** - prints a human-readable `AI TEST TRIAGE` section in GitHub Actions.
9. **Observability Recorder** - records operational signals without sensitive data.

## 3. Main flow
1. GitHub Actions runs Java tests.
2. On failure, Failure Collector extracts evidence.
3. Context Builder invokes deterministic retrieval.
4. Secret Filter sanitizes the resulting package.
5. Claude Client sends trusted task instructions plus sanitized failure/context data.
6. Response Validator checks the structured response.
7. On valid output, Triage Renderer prints probable cause, evidence, related documents, solutions, trade-offs, confidence/reason, and limitations.
8. The pipeline remains failed because the original Java test failed. A human decides what happens next.

## 4. Deterministic context retrieval
V1 starts from explicit evidence: failed test name, files/classes in stack trace, and directly identifiable relationships. It does not send the entire repository. Search/RAG/embeddings are intentionally deferred until baseline evals demonstrate that deterministic retrieval misses material context.

Example: `PedidoServiceTest` -> test source -> stack trace mentions `PedidoService.java` -> relevant source region -> directly identifiable `PedidoEstado.java`, plus a spec/ADR only when an explicit deterministic mapping exists.

## 5. Claude integration and output
Claude is the V1 provider. Provider credentials come from GitHub Actions secrets. Provider implementation must not expose secret values in logs. The returned payload must implement the logical contract in `spec.md` and be validated before presentation.

## 6. Validation and failure handling
- maximum 2 attempts per triage;
- initial timeout: 30 seconds per attempt;
- retry timeout, transient provider errors, or structurally invalid response;
- do not retry missing log, insufficient context, or invalid credentials unless the underlying condition changes;
- after final failure, emit `TRIAGE FAILED` with an operational reason and preserve original test evidence;
- triage failure never changes the original test outcome.

## 7. Security
### Trust boundary
Logs, code, specs, ADRs, and other recovered text are untrusted data. They do not gain instruction authority merely because they are placed in LLM context.

### Least privilege
AI Test Triage is read-only. No shell, editing, commit, pull request, deploy, or remediation tools are available to it.

### Secret handling
Mask secrets before provider input and observability output. Claude API credentials must be supplied by protected CI secret management and never committed.

## 8. GitHub Actions integration
Test execution and AI triage are separate concerns. Triage runs conditionally after Java test failure. The original test step/job remains the source of pipeline failure. Triage prints either the structured diagnosis or `TRIAGE FAILED` without suppressing original evidence.

## 9. Multiple failures
Analyze at most 5 distinct failed tests per pipeline execution. Report total failures, analyzed failures, and failures skipped due to the V1 limit. No clustering/deduplication in V1.

## 10. Observability
Record:
- triage SUCCESS/FAILED;
- total duration;
- attempt used;
- context categories/approximate quantity;
- operational failure reason;
- token/cost usage when exposed by the provider.
Never duplicate unmasked sensitive content for telemetry.

## 11. Evals
Create a baseline of 10 reproducible Java failures whose root causes are known. Evaluate probable-cause usefulness, evidence correctness, related spec/ADR relevance, solution plausibility, trade-off coherence, and unsupported claims. Initial target: at least 8/10 useful analyses. Reuse this baseline when evaluating future retrieval changes.

## 12. APIs and persistence
V1 requires no public application API and no dedicated database. Its external provider integration is Claude, and its user-facing output is the GitHub Actions log. Persistent storage is deferred until an explicit audit/history/analytics requirement exists.

## 13. Scalability/cost controls
The maximum of 5 analyzed failures, deterministic retrieval, and minimal context bound initial cost/latency. Failure clustering/deduplication and hybrid/RAG retrieval are future optimizations justified by observed data, not prerequisites.

## 14. ADR index
- ADR-001: Deterministic Context Retrieval for V1
- ADR-002: Read-only and Human-in-the-loop
- ADR-003: Graceful Degradation, Retry, and Timeout
- ADR-004: Failure Limit Now; Clustering/Deduplication Later
