package com.eversonrubira.exporttracking.triage.context;

import com.eversonrubira.exporttracking.triage.TestSupport;
import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.collector.Gap;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T04: pacote estavel e inspecionavel, com as ausencias declaradas.
class ContextBuilderTest {

    private static ContextBuilder builder(Map<String, String> overrides) {
        TriageConfig config = TestSupport.sampleConfig(overrides);
        return new ContextBuilder(config, new DeterministicRetriever(config, DocumentMap.load(config.docMapFile())));
    }

    @Test
    void multipleSourceFilesAndDocumentsAreAssembledByPriority() {
        ContextPackage pkg = builder(Map.of()).build(DeterministicRetrieverTest.failure(DeterministicRetrieverTest.STACK));

        assertEquals(List.of(ArtifactKind.TEST_SOURCE, ArtifactKind.CITED_SOURCE, ArtifactKind.RELATED_SOURCE,
                ArtifactKind.RELATED_SOURCE, ArtifactKind.DOCUMENT, ArtifactKind.DOCUMENT, ArtifactKind.DOCUMENT),
                pkg.artifacts().stream().map(Artifact::kind).toList());
        assertTrue(pkg.limitations().isEmpty(), pkg.limitations().toString());

        ContextPackage.Summary s = pkg.summary();
        assertEquals(1, s.artifactsByKind().get(ArtifactKind.TEST_SOURCE));
        assertEquals(2, s.artifactsByKind().get(ArtifactKind.RELATED_SOURCE));
        assertEquals(3, s.artifactsByKind().get(ArtifactKind.DOCUMENT));
        assertTrue(s.totalChars() > s.artifactChars());
    }

    @Test
    void inspectableTextIsStableAndShowsEverySection() {
        FailureInput f = DeterministicRetrieverTest.failure(DeterministicRetrieverTest.STACK);
        String first = builder(Map.of()).build(f).toInspectableText();
        assertEquals(first, builder(Map.of()).build(f).toInspectableText());
        assertTrue(first.contains("=== FAILURE ==="));
        assertTrue(first.contains("test: com.acme.shop.OrderServiceTest#shipRejectsUnpaid"));
        assertTrue(first.contains("--- stackTrace ---"));
        assertTrue(first.contains("=== CONTEXT ARTIFACTS (7) ==="));
        assertTrue(first.contains("--- [TEST_SOURCE] src/test/java/com/acme/shop/OrderServiceTest.java"));
        assertTrue(first.contains("=== LIMITATIONS (0) ==="));
    }

    @Test
    void emptyContextIsDeclaredNotHidden() {
        // Sem stack, sem log, e raizes de codigo e mapa que nao existem.
        ContextBuilder empty = builder(Map.of("triage.source-roots", "nowhere", "triage.doc-map", "nowhere.properties"));
        FailureInput f = new FailureInput("com.acme.shop.XTest", "t", "", "", "", "",
                List.of(Gap.MESSAGE_MISSING, Gap.STACK_TRACE_MISSING, Gap.LOG_MISSING));
        ContextPackage pkg = empty.build(f);
        assertTrue(pkg.artifacts().isEmpty());
        assertTrue(pkg.limitations().contains(Gap.STACK_TRACE_MISSING.description()));
        assertTrue(pkg.limitations().contains(Gap.LOG_MISSING.description()));
        assertTrue(pkg.limitations().stream().anyMatch(l -> l.startsWith("No source or documentation context")),
                pkg.limitations().toString());
        assertTrue(pkg.toInspectableText().contains("=== CONTEXT ARTIFACTS (0) ==="));
    }

    @Test
    void partialContextKeepsWhatWasFoundAndDeclaresWhatWasNot() {
        ContextPackage pkg = builder(Map.of("triage.doc-map", "docs/doc-map-unrelated.properties"))
                .build(DeterministicRetrieverTest.failure(DeterministicRetrieverTest.STACK));
        assertFalse(pkg.artifacts().isEmpty());
        assertTrue(pkg.artifacts().stream().noneMatch(a -> a.kind() == ArtifactKind.DOCUMENT));
        assertTrue(pkg.limitations().stream().anyMatch(l -> l.contains("No spec or ADR is mapped")));
    }

    @Test
    void longStackTracesAreCutAndDeclared() {
        ContextPackage pkg = builder(Map.of("triage.context.max-stack-lines", "2"))
                .build(DeterministicRetrieverTest.failure(DeterministicRetrieverTest.STACK));
        assertEquals(2, pkg.failure().stackTrace().lines().count());
        assertTrue(pkg.limitations().stream().anyMatch(l -> l.contains("first 2 of " + DeterministicRetrieverTest.STACK.lines().count() + " lines")), pkg.limitations().toString());
    }

    @Test
    void totalBudgetDropsLowestPriorityArtifactsAndSaysSo() {
        FailureInput f = DeterministicRetrieverTest.failure(DeterministicRetrieverTest.STACK);
        ContextPackage pkg = builder(Map.of("triage.context.max-total-chars", "1000")).build(f);
        int used = pkg.failure().stackTrace().length() + pkg.failure().logExcerpt().length()
                + pkg.failure().errorMessage().length();
        for (Artifact a : pkg.artifacts()) {
            used += a.content().length();
        }
        assertTrue(used <= 1000, "used " + used);
        assertTrue(pkg.artifacts().size() < 7);
        assertTrue(pkg.limitations().stream().anyMatch(l -> l.contains("total context limit of 1000")),
                pkg.limitations().toString());
    }
}
