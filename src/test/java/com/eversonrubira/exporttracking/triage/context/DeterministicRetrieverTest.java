package com.eversonrubira.exporttracking.triage.context;

import com.eversonrubira.exporttracking.triage.TestSupport;
import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T03: mesma falha, mesmo contexto; so o explicitamente relacionado; nada do resto.
class DeterministicRetrieverTest {

    static final String STACK = """
            com.acme.shop.InvalidTransitionException: Cannot ship order in status CREATED, expected OrderStatus PAID
            \tat com.acme.shop.OrderService.ship(OrderService.java:14)
            \tat com.acme.shop.OrderServiceTest.shipRejectsUnpaid(OrderServiceTest.java:22)
            \tat org.junit.jupiter.api.Assertions.fail(Assertions.java:55)
            \tat org.springframework.test.context.TestContextManager.run(TestContextManager.java:10)
            \tat java.base/java.lang.reflect.Method.invoke(Method.java:580)
            """;

    static FailureInput failure(String stack) {
        return new FailureInput("com.acme.shop.OrderServiceTest", "shipRejectsUnpaid",
                "com.acme.shop.InvalidTransitionException",
                "Cannot ship order in status CREATED, expected OrderStatus PAID", stack, "", List.of());
    }

    private static DeterministicRetriever retriever(Map<String, String> overrides) {
        TriageConfig config = TestSupport.sampleConfig(overrides);
        return new DeterministicRetriever(config, DocumentMap.load(config.docMapFile()));
    }

    private static List<String> paths(RetrievalResult r) {
        return r.artifacts().stream().map(Artifact::path).toList();
    }

    @Test
    void selectsExactlyTheRelatedArtifactsInAStableOrder() {
        RetrievalResult r = retriever(Map.of()).retrieve(failure(STACK));

        assertEquals(List.of(
                "src/test/java/com/acme/shop/OrderServiceTest.java",           // teste que falhou
                "src/main/java/com/acme/shop/OrderService.java",               // citado no stack
                "src/main/java/com/acme/shop/InvalidTransitionException.java", // tipo da excecao
                "src/main/java/com/acme/shop/OrderStatus.java",                // nome na mensagem
                "docs/spec.md",                                                // doc-map: pacote
                "docs/adr/ADR-010-order-lifecycle.md",                         // doc-map: classe citada
                "docs/spec.md"),                                               // doc-map: classe citada (outra seccao)
                paths(r));
        assertEquals(List.of(ArtifactKind.TEST_SOURCE, ArtifactKind.CITED_SOURCE, ArtifactKind.RELATED_SOURCE,
                ArtifactKind.RELATED_SOURCE, ArtifactKind.DOCUMENT, ArtifactKind.DOCUMENT, ArtifactKind.DOCUMENT),
                r.artifacts().stream().map(Artifact::kind).toList());
        // O ficheiro nao relacionado fica fora, e a ausencia de mapeamento nao e declarada aqui.
        assertFalse(paths(r).stream().anyMatch(p -> p.contains("Unrelated")));
        assertTrue(r.limitations().isEmpty(), r.limitations().toString());
    }

    @Test
    void documentSectionsStopAtTheNextHeadingOfTheSameLevel() {
        RetrievalResult r = retriever(Map.of()).retrieve(failure(STACK));
        Artifact pricing = r.artifacts().get(4);
        assertTrue(pricing.content().startsWith("## Pricing"));
        assertFalse(pricing.content().contains("Order lifecycle"));
        Artifact lifecycle = r.artifacts().get(6);
        assertTrue(lifecycle.content().startsWith("## Order lifecycle"));
        assertTrue(lifecycle.content().contains("### Detalhe"));   // sub-seccao fica dentro
        assertFalse(lifecycle.content().contains("Pricing"));
    }

    @Test
    void onlyProjectClassesCountAsCitedEvenWhenTheStackIsFullOfOthers() {
        RetrievalResult r = retriever(Map.of()).retrieve(failure(STACK));
        assertTrue(r.limitations().isEmpty(), r.limitations().toString());
        assertFalse(paths(r).stream().anyMatch(p -> p.contains("springframework") || p.contains("junit")));
    }

    @Test
    void citedClassesFollowTheOrderOfTheStackTrace() {
        String stack = """
                x.Y: m
                \tat com.acme.shop.OrderStatus.valueOf(OrderStatus.java:3)
                \tat com.acme.shop.OrderService.ship(OrderService.java:14)
                \tat com.acme.shop.InvalidTransitionException.<init>(InvalidTransitionException.java:5)
                \tat com.acme.shop.OrderServiceTest.t(OrderServiceTest.java:22)
                """;
        FailureInput f = new FailureInput("com.acme.shop.OrderServiceTest", "t", "", "", stack, "", List.of());
        RetrievalResult r = retriever(Map.of("triage.context.max-documents", "0")).retrieve(f);
        assertEquals(List.of(
                "src/test/java/com/acme/shop/OrderServiceTest.java",
                "src/main/java/com/acme/shop/OrderStatus.java",
                "src/main/java/com/acme/shop/OrderService.java",
                "src/main/java/com/acme/shop/InvalidTransitionException.java"), paths(r));
    }

    @Test
    void sameFailureAlwaysGivesTheSameContext() {
        RetrievalResult first = retriever(Map.of()).retrieve(failure(STACK));
        assertEquals(first, retriever(Map.of()).retrieve(failure(STACK)));
        DeterministicRetriever same = retriever(Map.of());
        assertEquals(same.retrieve(failure(STACK)), same.retrieve(failure(STACK)));
    }

    @Test
    void sourceRegionIsCenteredOnTheCitedLine() throws Exception {
        RetrievalResult r = retriever(Map.of("triage.context.region-radius-lines", "5")).retrieve(failure(STACK));
        Artifact service = r.artifacts().get(1);
        int total = Files.readAllLines(TestSupport.resource(
                "triage/sample-project/src/main/java/com/acme/shop/OrderService.java")).size();
        assertTrue(service.reason().contains("lines 9-19 of " + total), service.reason());
        assertTrue(service.content().contains("  14: "));
        assertFalse(service.content().contains("   8: "));
        assertFalse(service.content().contains("  20: "));
    }

    @Test
    void smallFilesAreIncludedWhole() {
        RetrievalResult r = retriever(Map.of()).retrieve(failure(STACK));
        Artifact status = r.artifacts().get(3);
        assertTrue(status.reason().contains("whole file"), status.reason());
        assertTrue(status.content().contains("CREATED, PAID, SHIPPED"));
    }

    @Test
    void contentIsCappedAtALineBoundaryAndFlagged() {
        RetrievalResult r = retriever(Map.of("triage.context.max-chars-per-artifact", "200")).retrieve(failure(STACK));
        Artifact service = r.artifacts().get(1);
        assertTrue(service.truncated());
        assertTrue(service.content().length() <= 200);
        assertFalse(r.artifacts().get(3).truncated()); // OrderStatus cabe nos 200
    }

    @Test
    void withoutStackTraceOnlyTheTestAndDirectlyIdentifiableTypesAreUsed() {
        RetrievalResult r = retriever(Map.of()).retrieve(failure(""));
        assertEquals("src/test/java/com/acme/shop/OrderServiceTest.java", paths(r).get(0));
        assertFalse(paths(r).contains("src/main/java/com/acme/shop/OrderService.java"));
        assertTrue(paths(r).contains("src/main/java/com/acme/shop/InvalidTransitionException.java"));
    }

    @Test
    void classNameInTheMessageIsOnlyUsedWhenItIsUnambiguous() {
        // Duplicated existe em dois pacotes: nao se adivinha qual deles e.
        FailureInput f = new FailureInput("com.acme.shop.OrderServiceTest", "t", "", "mentions Duplicated twice", "", "", List.of());
        RetrievalResult r = retriever(Map.of("triage.context.max-documents", "0")).retrieve(f);
        assertEquals(List.of("src/test/java/com/acme/shop/OrderServiceTest.java"), paths(r));
    }

    @Test
    void unresolvableCitedClassesAreDeclaredNotGuessed() {
        String stack = "x.Y: m\n\tat com.acme.shop.Ghost.boom(Ghost.java:3)\n";
        RetrievalResult r = retriever(Map.of()).retrieve(failure(stack));
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("com.acme.shop.Ghost")), r.limitations().toString());
    }

    @Test
    void missingTestSourceIsDeclared() {
        FailureInput f = new FailureInput("com.acme.shop.NoSuchTest", "t", "", "", "", "", List.of());
        RetrievalResult r = retriever(Map.of()).retrieve(f);
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("com.acme.shop.NoSuchTest")));
    }

    @Test
    void limitsOnSourceFilesAndDocumentsAreEnforcedAndDeclared() {
        RetrievalResult files = retriever(Map.of("triage.context.max-source-files", "1")).retrieve(failure(STACK));
        assertEquals(1, files.artifacts().stream().filter(a -> a.kind() != ArtifactKind.DOCUMENT).count());
        assertTrue(files.limitations().stream().anyMatch(l -> l.contains("omitted")), files.limitations().toString());

        RetrievalResult docs = retriever(Map.of("triage.context.max-documents", "1")).retrieve(failure(STACK));
        assertEquals(1, docs.artifacts().stream().filter(a -> a.kind() == ArtifactKind.DOCUMENT).count());
        assertTrue(docs.limitations().stream().anyMatch(l -> l.contains("mapped document(s) were omitted")));
    }

    @Test
    void unmappedDocumentationIsDeclaredAsALimitation() {
        RetrievalResult r = retriever(Map.of("triage.doc-map", "docs/doc-map-unrelated.properties")).retrieve(failure(STACK));
        assertTrue(r.artifacts().stream().noneMatch(a -> a.kind() == ArtifactKind.DOCUMENT));
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("No spec or ADR is mapped")), r.limitations().toString());
    }

    @Test
    void missingDocumentMapIsDeclaredAsALimitation() {
        RetrievalResult r = retriever(Map.of("triage.doc-map", "docs/nope.properties")).retrieve(failure(STACK));
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("document map was not found")));
    }

    @Test
    void mappedButMissingDocumentsAndSectionsAreDeclared() {
        String stack = "x.Y: m\n\tat com.acme.billing.Invoice.pay(Invoice.java:1)\n";
        FailureInput f = new FailureInput("com.acme.billing.InvoiceTest", "t", "", "", stack, "", List.of());
        TriageConfig config = TestSupport.sampleConfig(Map.of("triage.project-packages", "com.acme.shop,com.acme.billing"));
        RetrievalResult r = new DeterministicRetriever(config, DocumentMap.load(config.docMapFile())).retrieve(f);
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("docs/missing.md")), r.limitations().toString());
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("Nonexistent heading")), r.limitations().toString());
    }

    @Test
    void mostSpecificMapKeyWins() {
        TriageConfig config = TestSupport.sampleConfig(Map.of());
        DocumentMap map = DocumentMap.load(config.docMapFile());
        List<DocumentMap.DocRef> forService = map.resolve(List.of("com.acme.shop.OrderService"));
        assertEquals(List.of("docs/adr/ADR-010-order-lifecycle.md", "docs/spec.md#Order lifecycle"),
                forService.stream().map(DocumentMap.DocRef::label).toList());
        List<DocumentMap.DocRef> forOther = map.resolve(List.of("com.acme.shop.Other"));
        assertEquals(List.of("docs/spec.md#Pricing"), forOther.stream().map(DocumentMap.DocRef::label).toList());
        assertTrue(map.resolve(List.of("com.acme.shopfront.X")).isEmpty()); // prefixo so vale em fronteira de pacote
    }
}
