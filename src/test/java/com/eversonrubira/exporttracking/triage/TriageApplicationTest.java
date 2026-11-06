package com.eversonrubira.exporttracking.triage;

import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T01/T09: o ponto de entrada carrega com o triage ativado e desativado, nunca falha o pipeline
// e nunca olha para os relatorios reais do build em curso (usa sempre um projeto de exemplo).
class TriageApplicationTest {

    @Test
    void disabledPrintsOneLineAndNeverFailsThePipeline() {
        Properties p = new Properties();
        p.setProperty("triage.enabled", "false");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = TriageApplication.run(TriageConfig.fromProperties(p), Map.of(), new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertEquals(0, exit);
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("disabled"));
    }

    @Test
    void enabledWithoutReportsSaysThereIsNothingToAnalyzeAndNeverFailsThePipeline() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        TriageConfig config = TestSupport.sampleConfig(Map.of("triage.reports-dir", "no-such-reports-dir"));
        int exit = TriageApplication.run(config, Map.of(), new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertEquals(0, exit);
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("AI TEST TRIAGE"));
        assertTrue(text.contains("nothing to analyze"));
    }

    // T09: o stdout do Java segue a locale do sistema; o triage fixa UTF-8 para nao trocar acentos por '?'.
    @Test
    void outputIsEncodedAsUtf8RegardlessOfTheSystemLocale() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = TriageApplication.utf8(buffer);
        out.print("validação → estado inválido ✓");
        out.flush();
        assertEquals("validação → estado inválido ✓", buffer.toString(StandardCharsets.UTF_8));
        assertEquals("validação → estado inválido ✓".getBytes(StandardCharsets.UTF_8).length, buffer.size());
    }
}
