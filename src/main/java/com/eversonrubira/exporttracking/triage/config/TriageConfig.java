package com.eversonrubira.exporttracking.triage.config;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuracao do AI Test Triage. Todas as chaves e valores por omissao estao
 * documentados em docs/ai-test-triage/triage.properties.
 *
 * Os limites vindos dos ADRs sao tetos duros, nao apenas valores por omissao:
 * ADR-003 (no maximo 2 tentativas) e ADR-004 (no maximo 5 falhas analisadas).
 * Configurar acima disso e um erro, para a V1 nao divergir dos ADRs sem querer.
 */
public record TriageConfig(
        boolean enabled,
        String model,
        int timeoutSeconds,
        int maxAttempts,
        URI baseUrl,
        int maxOutputTokens,
        int retryDelayMillis,
        String apiKeyEnv,
        int maxFailures,
        Path projectRoot,
        Path reportsDir,
        List<Path> sourceRoots,
        List<String> projectPackages,
        Path docMapFile,
        int maxLogLines,
        int maxStackLines,
        int maxCharsPerArtifact,
        int regionRadiusLines,
        int maxSourceFiles,
        int maxDocuments,
        int maxTotalChars) {

    /** Teto do ADR-003. */
    public static final int HARD_MAX_ATTEMPTS = 2;
    /** Teto do ADR-004. */
    public static final int HARD_MAX_FAILURES = 5;

    public TriageConfig {
        sourceRoots = List.copyOf(sourceRoots);
        projectPackages = List.copyOf(projectPackages);
    }

    public static TriageConfig defaults() {
        return fromProperties(new Properties());
    }

    /**
     * Le o ficheiro (se existir) e aplica por cima as chaves de {@code overrides}
     * (por exemplo as propriedades de sistema -Dtriage.enabled=false).
     */
    public static TriageConfig load(Path file, Properties overrides) {
        Properties merged = new Properties();
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                merged.load(reader);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read triage configuration file " + file + ": " + e.getMessage(), e);
            }
        }
        if (overrides != null) {
            for (String key : overrides.stringPropertyNames()) {
                if (key.startsWith("triage.")) {
                    merged.setProperty(key, overrides.getProperty(key));
                }
            }
        }
        return fromProperties(merged);
    }

    public static TriageConfig fromProperties(Properties p) {
        Path root = Path.of(text(p, "triage.project-root", ".")).normalize();
        List<Path> sourceRoots = new ArrayList<>();
        for (String item : list(p, "triage.source-roots", "src/test/java,src/main/java")) {
            sourceRoots.add(root.resolve(item).normalize());
        }
        return new TriageConfig(
                bool(p, "triage.enabled", true),
                nonBlank(p, "triage.provider.model", "claude-sonnet-5-5"),
                number(p, "triage.provider.timeout-seconds", 30, 1, 600),
                number(p, "triage.provider.max-attempts", HARD_MAX_ATTEMPTS, 1, HARD_MAX_ATTEMPTS),
                baseUrl(p),
                number(p, "triage.provider.max-output-tokens", 2000, 256, 8000),
                number(p, "triage.provider.retry-delay-millis", 1000, 0, 60_000),
                nonBlank(p, "triage.provider.api-key-env", "ANTHROPIC_API_KEY"),
                number(p, "triage.max-failures", HARD_MAX_FAILURES, 1, HARD_MAX_FAILURES),
                root,
                root.resolve(text(p, "triage.reports-dir", "target/surefire-reports")).normalize(),
                sourceRoots,
                list(p, "triage.project-packages", "com.eversonrubira.exporttracking"),
                root.resolve(text(p, "triage.doc-map", "docs/ai-test-triage/doc-map.properties")).normalize(),
                number(p, "triage.collector.max-log-lines", 100, 1, 2000),
                number(p, "triage.context.max-stack-lines", 80, 1, 2000),
                number(p, "triage.context.max-chars-per-artifact", 8000, 200, 100_000),
                number(p, "triage.context.region-radius-lines", 30, 1, 500),
                number(p, "triage.context.max-source-files", 6, 1, 50),
                number(p, "triage.context.max-documents", 3, 0, 20),
                number(p, "triage.context.max-total-chars", 40_000, 1000, 400_000));
    }

    /**
     * A chave de API viaja neste endereco, por isso so se aceita https, ou http para
     * a propria maquina (servidor local dos testes). Nunca http para fora.
     */
    private static URI baseUrl(Properties p) {
        String value = text(p, "triage.provider.base-url", "https://api.anthropic.com");
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("triage.provider.base-url is not a valid URL: '" + value + "'");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
        if (!(scheme.equals("https") || (scheme.equals("http") && loopback))) {
            throw new IllegalArgumentException(
                    "triage.provider.base-url must use https (http is only accepted for localhost), was '" + value + "'");
        }
        return uri;
    }

    private static String text(Properties p, String key, String fallback) {
        String value = p.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String nonBlank(Properties p, String key, String fallback) {
        return text(p, key, fallback);
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim().toLowerCase();
        if (v.equals("true")) {
            return true;
        }
        if (v.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false, was '" + value + "'");
    }

    private static int number(Properties p, String key, int fallback, int min, int max) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer, was '" + value + "'");
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ", was " + parsed);
        }
        return parsed;
    }

    private static List<String> list(Properties p, String key, String fallback) {
        List<String> items = new ArrayList<>();
        for (String part : text(p, key, fallback).split(",")) {
            if (!part.isBlank()) {
                items.add(part.trim());
            }
        }
        return items;
    }
}
