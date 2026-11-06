package com.eversonrubira.exporttracking.triage.config;

import com.eversonrubira.exporttracking.triage.TestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T01: configuracao com os limites dos ADRs como tetos duros.
class TriageConfigTest {

    @Test
    void defaultsFollowTheAcceptedAdrs() {
        TriageConfig c = TriageConfig.defaults();
        assertTrue(c.enabled());
        assertEquals("claude-sonnet-5-5", c.model());
        assertEquals(30, c.timeoutSeconds());   // ADR-003
        assertEquals(2, c.maxAttempts());       // ADR-003
        assertEquals(5, c.maxFailures());       // ADR-004
        assertEquals("https://api.anthropic.com", c.baseUrl().toString());
        assertEquals("ANTHROPIC_API_KEY", c.apiKeyEnv());
        assertEquals(Path.of("target/surefire-reports"), c.reportsDir());
        assertEquals(List.of(Path.of("src/test/java"), Path.of("src/main/java")), c.sourceRoots());
        assertEquals(List.of("com.eversonrubira.exporttracking"), c.projectPackages());
    }

    @Test
    void systemPropertiesOverrideTheFile() throws Exception {
        TestSupport.withTempDir(dir -> {
            Path file = dir.resolve("triage.properties");
            Files.writeString(file, "triage.enabled=false\ntriage.provider.timeout-seconds=45\n");
            Properties overrides = new Properties();
            overrides.setProperty("triage.provider.timeout-seconds", "10");
            overrides.setProperty("unrelated.key", "ignored");
            TriageConfig c = TriageConfig.load(file, overrides);
            assertFalse(c.enabled());
            assertEquals(10, c.timeoutSeconds());
        });
    }

    @Test
    void missingFileFallsBackToDefaults() {
        TriageConfig c = TriageConfig.load(Path.of("does-not-exist.properties"), new Properties());
        assertEquals(30, c.timeoutSeconds());
    }

    @Test
    void relativePathsResolveAgainstProjectRoot() {
        Properties p = new Properties();
        p.setProperty("triage.project-root", "/work/app");
        TriageConfig c = TriageConfig.fromProperties(p);
        assertEquals(Path.of("/work/app/target/surefire-reports"), c.reportsDir());
        assertEquals(Path.of("/work/app/docs/ai-test-triage/doc-map.properties"), c.docMapFile());
    }

    @Test
    void adrCeilingsCannotBeExceeded() {
        assertRejected("triage.provider.max-attempts", "3");
        assertRejected("triage.max-failures", "6");
    }

    @Test
    void invalidValuesAreRejectedWithTheKeyInTheMessage() {
        assertRejected("triage.provider.timeout-seconds", "0");
        assertRejected("triage.provider.timeout-seconds", "fast");
        assertRejected("triage.enabled", "maybe");
    }

    // T07: a chave de API viaja neste endereco, por isso nunca em texto simples para fora.
    @Test
    void baseUrlMustBeHttpsOrLocalhost() {
        assertRejected("triage.provider.base-url", "http://api.anthropic.com");
        assertRejected("triage.provider.base-url", "http://evil.example.com:8080");
        assertRejected("triage.provider.base-url", "ftp://localhost");
        assertRejected("triage.provider.base-url", "not a url");
        Properties p = new Properties();
        p.setProperty("triage.provider.base-url", "http://localhost:8089");
        assertEquals("http://localhost:8089", TriageConfig.fromProperties(p).baseUrl().toString());
        p.setProperty("triage.provider.base-url", "http://127.0.0.1:8089");
        assertEquals("http://127.0.0.1:8089", TriageConfig.fromProperties(p).baseUrl().toString());
        p.setProperty("triage.provider.base-url", "https://proxy.example.com");
        assertEquals("https://proxy.example.com", TriageConfig.fromProperties(p).baseUrl().toString());
    }

    @Test
    void lowerLimitsThanTheCeilingAreAccepted() {
        Properties p = new Properties();
        p.setProperty("triage.provider.max-attempts", "1");
        p.setProperty("triage.max-failures", "2");
        TriageConfig c = TriageConfig.fromProperties(p);
        assertEquals(1, c.maxAttempts());
        assertEquals(2, c.maxFailures());
    }

    private static void assertRejected(String key, String value) {
        Properties p = new Properties();
        p.setProperty(key, value);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TriageConfig.fromProperties(p));
        assertTrue(e.getMessage().contains(key), "message should name the key: " + e.getMessage());
    }
}
