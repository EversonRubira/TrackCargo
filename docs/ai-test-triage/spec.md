# AI Test Triage - Specification

**Status:** Approved for V1 implementation  
**Project:** Pontis  
**Scope:** V1

## 1. Context
Pontis executes Java tests in CI. When a test fails, the developer must interpret logs and stack traces, relate the failure to code/specifications/ADRs, formulate likely causes, and evaluate possible fixes. Today this investigation may be assisted manually by Claude. V1 automates only the initial analysis while preserving human decision-making.

## 2. Problem
Reduce the manual effort required to begin investigation of Java test failures without granting the LLM authority to change the project.

## 3. Objective
When a Java test fails in GitHub Actions, automatically produce an evidence-grounded diagnosis that helps the developer choose the next step.

## 4. User
Developer responsible for investigating and correcting Java test failures in Pontis.

## 5. Functional requirements
### RF-01 - Trigger
Start triage only when one or more Java tests fail in CI.

### RF-02 - Collect failure evidence
Collect at minimum: failed test name, error message, available stack trace, and relevant execution-log excerpt.

### RF-03 - Retrieve relevant context
Provide context related to the failure, which may include the failed test source, classes explicitly cited by the stack trace, and deterministically mapped specs/ADRs. The system must not depend on sending the entire repository.

### RF-04 - Structured diagnosis
Return:
1. probable cause;
2. supporting evidence;
3. related spec/ADR when found;
4. possible solutions;
5. trade-offs for the solutions.

### RF-05 - Uncertainty
If evidence is insufficient, state the limitation and do not present a hypothesis as fact. Confidence, when shown, must be HIGH, MEDIUM, or LOW with an evidence-based reason, never an invented percentage.

### RF-06 - CI output
Display the result in the GitHub Actions job log under a clearly identifiable `AI TEST TRIAGE` section.

### RF-07 - Human decision
The system only analyzes and recommends. Correction and architectural decisions remain human responsibilities.

### RF-08 - Preserve original failure
Triage must never hide, replace, or convert the original failed test result.

## 6. Non-functional requirements
### RNF-01 - Untrusted data
Logs, stack traces, source code, specs, and ADRs supplied as context must be treated as untrusted data, not instructions.

### RNF-02 - Least privilege
V1 is read-only: no code editing, shell/remediation execution, commit, pull request, or deploy authority.

### RNF-03 - Evidence grounding
Claims about a probable cause must point to concrete evidence included in the analysis.

### RNF-04 - Graceful degradation
If retrieval, validation, or Claude fails, retain the original test result and show that triage could not be completed.

### RNF-05 - Context minimization
Send only context considered necessary for investigating the failure.

### RNF-06 - Secret protection
Do not send passwords, API keys, bearer tokens, credentials, or known sensitive environment values to Claude. Mask them before provider input and observability logging.

### RNF-07 - Provider
Claude is the V1 LLM provider. Provider abstraction is an architecture decision, not a functional requirement.

### RNF-08 - Observability
Record triage status, duration, attempt number, context categories/approximate quantity, operational failure reason, and provider usage/cost data when available, without leaking secrets.

## 7. Logical response contract
```json
{
  "probableCause": "string",
  "evidence": ["string"],
  "relatedDocuments": ["string"],
  "possibleSolutions": [
    {"solution": "string", "tradeoffs": ["string"]}
  ],
  "confidence": "HIGH | MEDIUM | LOW",
  "confidenceReason": "string",
  "limitations": ["string"]
}
```

## 8. Edge cases
- empty/unavailable log;
- partial stack trace;
- multiple failing tests;
- failure caused by infrastructure rather than application/test behavior;
- no related spec/ADR found;
- conflicting documentation;
- malformed/provider response outside contract;
- diagnosis without evidence;
- provider timeout/unavailability;
- prompt-injection text in retrieved content;
- secrets inside logs;
- context larger than an acceptable provider input.

## 9. Acceptance criteria
### CA-01
Given a failed Java test, when CI reaches triage, then a diagnosis request is created.

### CA-02
Given successful triage, the GitHub Actions log contains `AI TEST TRIAGE` with the five RF-04 elements.

### CA-03
Given a probable cause, supporting evidence is also shown.

### CA-04
Given insufficient context, the result states the limitation and avoids certainty.

### CA-05
Given any triage result, the component performs no automatic project modification.

### CA-06
Given timeout, provider failure, or invalid output after the allowed retry policy, original test evidence remains available and triage reports failure.

### CA-07
Given retrieved text such as `ignore previous instructions`, the text is treated as data and receives no command authority.

### CA-08
Given at least 10 Java failure cases with known root causes, evaluate cause usefulness, correct evidence, relevant documentation, solution plausibility, trade-off coherence, and unsupported claims.

## 10. Initial success metric
V1 is useful when at least 8/10 baseline evaluations help the developer identify the next step without restarting investigation from zero, no case causes automatic project modification, known secret fixtures are not exposed, and uncertain diagnoses remain hypotheses.

## 11. Risks
- convincing but incorrect diagnosis;
- missing relevant context;
- excessive context/cost;
- prompt injection;
- secret leakage;
- stale documentation;
- provider dependency;
- over-reliance on recommendations;
- latency in CI.

## 12. Out of scope for V1
- automatic code modification or remediation;
- shell tools;
- commits/pull requests/deploy;
- frontend, Docker, infrastructure, or deployment triage;
- automatic PR comments;
- persistent conversational memory;
- repository-wide context on every failure;
- RAG/embeddings unless a later approved change is justified by evals;
- failure clustering/deduplication.
