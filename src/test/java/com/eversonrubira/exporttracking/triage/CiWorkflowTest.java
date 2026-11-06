package com.eversonrubira.exporttracking.triage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T10: validacao do workflow. A execucao controlada (teste a falhar no CI) completa esta prova.
// Le o ci.yml como texto: o objetivo e fixar as garantias, nao validar YAML.
class CiWorkflowTest {

    private static final String STEP_START = "      - name:";
    private static final String CONDITION = "if: failure() && steps.tests.outcome == 'failure'";

    private static String workflow() throws IOException {
        // o Maven corre os testes na raiz do projeto
        return Files.readString(Path.of(".github/workflows/ci.yml"));
    }

    /** Passos do job "test", cada um como o seu bloco de texto. */
    private static List<String> testJobSteps() throws IOException {
        String text = workflow();
        int start = text.indexOf("\n  test:");
        int end = text.indexOf("\n  frontend:");
        assertTrue(start >= 0 && end > start, "job test not found");
        List<String> steps = new ArrayList<>();
        StringBuilder current = null;
        for (String line : text.substring(start, end).split("\n")) {
            if (line.trim().startsWith("#")) {
                continue;   // comentarios nao sao configuracao
            }
            if (line.startsWith(STEP_START)) {
                if (current != null) {
                    steps.add(current.toString());
                }
                current = new StringBuilder();
            }
            if (current != null) {
                current.append(line).append('\n');
            }
        }
        steps.add(current.toString());
        return steps;
    }

    private static String stepNamed(List<String> steps, String fragment) {
        return steps.stream().filter(s -> s.lines().findFirst().orElse("").contains(fragment)).findFirst().orElse(null);
    }

    @Test
    void theTestStepStaysAuthoritativeAndIsNeverMadeTolerant() throws IOException {
        String tests = stepNamed(testJobSteps(), "Rodar suite completa");
        assertNotNull(tests);
        assertTrue(tests.contains("id: tests"), "the triage steps refer to this step by id");
        assertFalse(tests.contains("continue-on-error"), "the test step must be able to fail the job");
        assertFalse(tests.contains("|| true"), "the test step must not swallow failures");
        assertFalse(tests.contains("ANTHROPIC_API_KEY"), "the key must not exist while the tests run");
    }

    @Test
    void bothTriageStepsRunOnlyAfterATestFailureAndNeverFailTheJob() throws IOException {
        List<String> steps = testJobSteps();
        int testsIndex = steps.indexOf(stepNamed(steps, "Rodar suite completa"));
        List<String> triage = steps.stream().filter(s -> s.lines().findFirst().orElse("").contains("AI Test Triage")).toList();
        assertEquals(2, triage.size());
        for (String step : triage) {
            assertTrue(step.contains(CONDITION), "step must run only when the test step failed:\n" + step);
            assertTrue(step.contains("continue-on-error: true"), "triage failure must not replace the original failure:\n" + step);
            assertTrue(steps.indexOf(step) > testsIndex, "triage must come after the tests");
        }
    }

    @Test
    void theApiKeyComesFromSecretsAndIsOnlyInTheStepThatRunsTheTriage() throws IOException {
        String text = workflow();
        assertEquals(1, text.split("secrets\\.ANTHROPIC_API_KEY", -1).length - 1, "the secret must be referenced exactly once");
        List<String> steps = testJobSteps();
        String run = stepNamed(steps, "diagnostico");
        assertTrue(run.contains("ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}"), run);
        assertTrue(run.contains("com.eversonrubira.exporttracking.triage.TriageApplication"), run);
        for (String other : steps) {
            if (other != run) {
                assertFalse(other.contains("ANTHROPIC_API_KEY"), "the key leaks into another step:\n" + other);
            }
        }
        // a chave nunca aparece num comando (echo, curl, argumentos)
        assertFalse(run.lines().filter(l -> !l.trim().startsWith("ANTHROPIC_API_KEY:")).anyMatch(l -> l.contains("ANTHROPIC_API_KEY")));
        assertFalse(run.contains("set -x") || run.contains("echo \"$ANTHROPIC"), run);
    }

    @Test
    void theWorkflowNeverRunsOnPullRequestTargetBecauseThatWouldGiveSecretsToForkCode() throws IOException {
        assertFalse(workflow().contains("pull_request_target"));
    }

    @Test
    void theMainClassNamedInTheWorkflowExists() throws Exception {
        assertEquals("com.eversonrubira.exporttracking.triage.TriageApplication", TriageApplication.class.getName());
        Class.forName("com.eversonrubira.exporttracking.triage.TriageApplication");
    }
}
