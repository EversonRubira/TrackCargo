package com.eversonrubira.exporttracking.triage;

import com.eversonrubira.exporttracking.triage.config.TriageConfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/** Utilitarios dos testes do triage. */
public final class TestSupport {

    private TestSupport() {
    }

    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    /** Pasta de recursos de teste, por exemplo "triage/surefire/complete". */
    public static Path resource(String name) {
        try {
            return Path.of(TestSupport.class.getResource("/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Configuracao apontada ao projeto de exemplo (pacote com.acme.shop). */
    public static TriageConfig sampleConfig(Map<String, String> overrides) {
        Properties p = new Properties();
        p.setProperty("triage.project-root", resource("triage/sample-project").toString());
        p.setProperty("triage.project-packages", "com.acme.shop");
        p.setProperty("triage.doc-map", "docs/doc-map.properties");
        overrides.forEach(p::setProperty);
        return TriageConfig.fromProperties(p);
    }

    public static void withTempDir(ThrowingConsumer<Path> body) throws Exception {
        Path dir = Files.createTempDirectory("triage-test-");
        try {
            body.accept(dir);
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
                // limpeza best-effort
            }
        }
    }
}
