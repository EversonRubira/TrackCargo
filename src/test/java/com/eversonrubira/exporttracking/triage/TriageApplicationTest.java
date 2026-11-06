package com.eversonrubira.exporttracking.triage;

import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T01: o modulo carrega com o triage ativado e desativado, sem Spring e sem Claude.
class TriageApplicationTest {

    @Test
    void disabledPrintsOneLineAndNeverFailsThePipeline() {
        Properties p = new Properties();
        p.setProperty("triage.enabled", "false");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = TriageApplication.run(TriageConfig.fromProperties(p), new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertEquals(0, exit);
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("disabled"));
    }

    @Test
    void enabledLoadsAndNeverFailsThePipeline() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit = TriageApplication.run(TriageConfig.defaults(), new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertEquals(0, exit);
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("AI TEST TRIAGE"));
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
