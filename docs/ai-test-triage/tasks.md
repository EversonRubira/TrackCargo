# AI Test Triage - Tasks

**Project:** Pontis  
**Phase:** Task Decomposition  
**Scope:** V1  
**Implementation owner:** Claude / development agent, with human approval

## Execution rule

Implement tasks in order. Do not expand V1 scope. The AI Test Triage remains read-only and must never modify application code, create commits/PRs, execute remediation, or change the original test result.

## T01 - Create triage module skeleton

**Objective:** Create the structural boundary for AI Test Triage without implementing provider logic.

**Deliverables:**
- package/module structure for collector, context, security, provider, validation, rendering and observability;
- configuration placeholders for provider timeout, retry and maximum failures.

**Tests:** Application/module loads with triage enabled and disabled.

**Definition of Done:** Structure compiles, configuration is documented, and no Claude call exists yet.

## T02 - Implement Failure Collector

**Objective:** Convert a failed Java test result into a normalized triage input.

**Must capture:**
- failed test name;
- error message;
- stack trace;
- relevant log excerpt.

**Tests:** Unit tests for complete, partial and missing stack traces/logs.

**Definition of Done:** Given a known failed-test fixture, the collector produces the expected normalized input deterministically.

## T03 - Implement deterministic Context Retrieval

**Objective:** Recover only context explicitly related to the failure.

**Rules:**
- start from failed test name;
- resolve classes/files cited by the stack trace;
- include directly identifiable related source types when deterministic;
- include mapped spec/ADR only when an explicit deterministic relationship exists;
- never send the full repository.

**Tests:** Fixtures proving the same failure returns the same selected context and unrelated files are excluded.

**Definition of Done:** Retrieval is deterministic, bounded and passes selection/exclusion tests.

## T04 - Build Context Builder

**Objective:** Assemble failure evidence and retrieved artifacts into the context package sent to the LLM.

**Tests:** Empty context, partial context, multiple source files and missing documentation.

**Definition of Done:** Context Builder produces a stable, inspectable package and reports limitations when relevant material is absent.

## T05 - Add secret filtering

**Objective:** Prevent known credentials/tokens from being sent to Claude or duplicated in triage logs.

**Tests:** Fixtures containing example API keys, bearer tokens and secret-like environment values must be masked.

**Definition of Done:** Secret fixtures do not appear unmasked in provider input or observability output.

## T06 - Define trusted prompt boundary

**Objective:** Separate trusted instructions from untrusted logs, source code and documentation.

**Requirements:**
- recovered content is explicitly treated as data;
- instructions found inside recovered data have no authority;
- prompt requires evidence-grounded analysis and explicit uncertainty.

**Tests:** Prompt-injection fixture such as "ignore previous instructions" remains data and does not change the requested output contract.

**Definition of Done:** Security tests demonstrate the trust boundary and the prompt is versioned with the implementation.

## T07 - Implement Claude provider client

**Objective:** Integrate the V1 with Claude using CI-managed credentials.

**Requirements:**
- 30-second timeout per attempt;
- maximum 2 attempts;
- retry only for transient provider errors, timeout or structurally invalid response;
- no secret value in logs.

**Tests:** Mocked success, timeout, transient error, invalid credential and unavailable-provider scenarios.

**Definition of Done:** Provider behavior matches ADR-003 without calling real Claude in automated unit tests.

## T08 - Implement structured response validation

**Objective:** Validate Claude output before presenting it as a diagnosis.

**Required logical fields:**
- probableCause;
- evidence;
- relatedDocuments;
- possibleSolutions with trade-offs;
- confidence: HIGH | MEDIUM | LOW;
- confidenceReason;
- limitations.

**Tests:** Valid schema, missing evidence, invalid confidence, malformed output and empty response.

**Definition of Done:** Invalid responses are rejected and routed through the failure/retry policy.

## T09 - Implement AI TEST TRIAGE renderer

**Objective:** Render a concise diagnosis in the GitHub Actions log.

**Requirements:**
- clearly labeled `AI TEST TRIAGE` section;
- show diagnosis, evidence, related documents, solutions, trade-offs, confidence/reason and limitations;
- on triage failure, show `TRIAGE FAILED` plus operational reason;
- never suppress the original test failure.

**Tests:** Snapshot/text assertions for success and triage-failure outputs.

**Definition of Done:** Output is human-readable and preserves the original failure context.

## T10 - Integrate with GitHub Actions

**Objective:** Trigger triage only after Java test failure.

**Requirements:**
- test execution remains authoritative for pipeline PASS/FAIL;
- triage is a separate conditional step/job;
- triage failure does not replace the original failure;
- Claude credential comes from GitHub Actions secrets.

**Tests:** CI workflow validation plus controlled failing-test run.

**Definition of Done:** A Java failure triggers triage and the pipeline remains failed for the original test reason.

## T11 - Enforce maximum five failures per pipeline

**Objective:** Bound V1 latency and provider cost.

**Requirements:**
- analyze at most 5 distinct failed tests;
- report total failures, analyzed failures and skipped failures;
- no clustering/deduplication in V1.

**Tests:** Fixture/run with more than five failures.

**Definition of Done:** Exactly the configured maximum is analyzed and remaining failures are explicitly reported as skipped.

## T12 - Add observability

**Objective:** Make operational behavior measurable without leaking sensitive data.

**Record:**
- triage SUCCESS/FAILED;
- duration;
- attempt number;
- context categories/count;
- operational failure reason;
- token/cost data when returned by Claude.

**Tests:** Success/failure metric assertions and secret-redaction checks.

**Definition of Done:** Required signals are available in the CI log or chosen telemetry sink and contain no known secret fixture.

## T13 - Build initial eval dataset

**Objective:** Create 10 reproducible Java failure cases with documented known causes.

**Each case records:**
- failure input;
- known root cause;
- expected useful evidence;
- relevant spec/ADR when applicable;
- evaluation notes.

**Definition of Done:** 10 cases can be executed repeatedly against the same triage implementation.

## T14 - Run baseline evals

**Objective:** Measure deterministic-retrieval V1 before considering RAG.

**Evaluate:**
- useful probable cause;
- correct evidence;
- relevant spec/ADR;
- plausible solutions;
- coherent trade-offs;
- unsupported claims.

**Target:** At least 8 of 10 analyses help identify the next step without restarting investigation from zero.

**Definition of Done:** Results are documented per case. Any proposal to add RAG must reference observed retrieval failures rather than preference alone.

## T15 - Verification pack

**Objective:** Demonstrate compliance with spec, architecture and ADRs before V1 is considered complete.

**Must include:**
- unit tests for collector, retrieval, context builder, filtering and validator;
- integration tests with mocked Claude provider;
- BDD scenarios for successful triage, Claude failure, prompt injection, missing context and more than five failures;
- GitHub Actions validation;
- checklist mapping acceptance criteria to evidence.

**Definition of Done:** All automated tests pass, the controlled CI failure produces the expected triage output, and acceptance-criteria evidence is documented.

## Explicitly deferred from V1

- RAG/embeddings or hybrid retrieval;
- failure clustering/deduplication;
- automatic code modification;
- shell/remediation tools;
- automatic commits or pull requests;
- frontend/Docker/deploy triage;
- persistent agent memory.
