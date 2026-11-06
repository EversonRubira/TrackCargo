package com.eversonrubira.exporttracking.triage.observability;

import com.eversonrubira.exporttracking.triage.context.ArtifactKind;
import com.eversonrubira.exporttracking.triage.rendering.SafeText;
import com.eversonrubira.exporttracking.triage.security.SecretFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T12: os sinais operacionais pedidos estao no log, numa linha cada, sem conteudo sensivel.
class ObservabilityRecorderTest {

    static final String GITHUB_TOKEN = "ghp" + "_" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8";

    private final ObservabilityRecorder recorder = new ObservabilityRecorder(new SafeText(new SecretFilter(Map.of())));

    private static FailureSignal signal(String testId, boolean success, String reason) {
        return new FailureSignal(testId, success, reason, 2, 1500,
                Map.of(ArtifactKind.TEST_SOURCE, 1, ArtifactKind.DOCUMENT, 3), 4321, 1234, 321);
    }

    @Test
    void aFailureLineCarriesStatusReasonAttemptsDurationContextAndTokens() {
        assertEquals("| [triage-metrics] status=SUCCESS reason=none attempts=2 durationMs=1500 contextArtifacts=4"
                        + " context=TEST_SOURCE:1,CITED_SOURCE:0,RELATED_SOURCE:0,DOCUMENT:3 contextChars=4321"
                        + " tokensIn=1234 tokensOut=321 test=com.acme.A#t\n",
                recorder.failureLine(signal("com.acme.A#t", true, "none")));
        assertTrue(recorder.failureLine(signal("com.acme.A#t", false, "TIMEOUT"))
                .startsWith("| [triage-metrics] status=FAILED reason=TIMEOUT attempts=2 "));
    }

    @Test
    void missingContextKindsCountAsZeroAndUnknownTokensStayNegative() {
        FailureSignal s = new FailureSignal("t", false, "CONTEXT_ERROR", 0, 3, Map.of(), 0, -1, -1);
        assertEquals(0, s.contextArtifacts());
        assertTrue(recorder.failureLine(s).contains("context=TEST_SOURCE:0,CITED_SOURCE:0,RELATED_SOURCE:0,DOCUMENT:0 contextChars=0 tokensIn=-1 tokensOut=-1 "));
    }

    @Test
    void theSummaryStatusIsNoFailuresSuccessOrFailed() {
        assertEquals("| [triage-metrics] summary status=NO_FAILURES failures=0 analyzed=0 skipped=0 succeeded=0 failed=0 limit=5 durationMs=7\n",
                recorder.summaryLine(0, 0, 0, 0, 0, 5, 7));
        assertTrue(recorder.summaryLine(7, 5, 2, 5, 0, 5, 9).startsWith("| [triage-metrics] summary status=SUCCESS failures=7 analyzed=5 skipped=2 succeeded=5 failed=0 limit=5 "));
        assertTrue(recorder.summaryLine(7, 5, 2, 4, 1, 5, 9).contains("summary status=FAILED "));
    }

    @Test
    void theTestIdIsOneLineWithoutControlCharactersCommandsOrSecrets() {
        SecretFilter filter = new SecretFilter(Map.of());
        ObservabilityRecorder r = new ObservabilityRecorder(new SafeText(filter));
        String line = r.failureLine(signal("A#t\n::error::pwned\r\n##[error]x\u001B[31m " + GITHUB_TOKEN, false, "TIMEOUT"));
        assertEquals(1L, line.lines().count(), line);
        assertTrue(line.startsWith("| [triage-metrics] "));
        assertFalse(line.contains(GITHUB_TOKEN), line);
        assertTrue(line.contains(SecretFilter.MASK), line);
        assertFalse(line.contains("\u001B"), line);
    }
}
