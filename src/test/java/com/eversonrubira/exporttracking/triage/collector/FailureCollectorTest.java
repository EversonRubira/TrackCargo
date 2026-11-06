package com.eversonrubira.exporttracking.triage.collector;

import com.eversonrubira.exporttracking.triage.TestSupport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T02: relatorios Surefire -> entrada normalizada, de forma deterministica.
class FailureCollectorTest {

    private final FailureCollector collector = new FailureCollector(100);

    @Test
    void completeReportYieldsTheExpectedNormalizedInput() {
        CollectionResult result = collector.collect(TestSupport.resource("triage/surefire/complete"));

        // O teste que passou e o que foi ignorado ficam de fora; failure e error entram.
        assertEquals(2, result.failures().size());
        assertTrue(result.warnings().isEmpty());

        FailureInput assertion = result.failures().get(0);
        assertEquals("com.acme.shop.OrderServiceTest", assertion.testClass());
        assertEquals("shipRejectsUnpaid", assertion.testMethod());
        assertEquals("com.acme.shop.OrderServiceTest#shipRejectsUnpaid", assertion.testId());
        assertEquals("com.acme.shop.InvalidTransitionException", assertion.errorType());
        assertEquals("Cannot ship order in status CREATED, expected OrderStatus PAID", assertion.errorMessage());
        assertTrue(assertion.stackTrace().startsWith("com.acme.shop.InvalidTransitionException: Cannot ship"));
        assertTrue(assertion.stackTrace().contains("at com.acme.shop.OrderService.ship(OrderService.java:14)"));
        assertEquals("10:00:01 INFO  OrderService - shipping order 42\n10:00:01 ERROR OrderService - transition refused",
                assertion.logExcerpt());
        assertTrue(assertion.gaps().isEmpty());

        FailureInput error = result.failures().get(1);
        assertEquals("nullCustomer", error.testMethod());
        assertEquals("java.lang.NullPointerException", error.errorType());
        assertEquals("Cannot invoke \"String.length()\" because \"name\" is null", error.errorMessage());
        assertEquals("", error.logExcerpt());
        assertEquals(List.of(Gap.LOG_MISSING), error.gaps());
    }

    @Test
    void partialReportsAreFlaggedNotInvented() {
        CollectionResult result = collector.collect(TestSupport.resource("triage/surefire/partial"));

        // Ordem alfabetica dos ficheiros: NoLogTest antes de PartialTest. A repeticao exata de "noLog" desaparece.
        assertEquals(List.of(
                "com.acme.shop.NoLogTest#noLog",
                "com.acme.shop.PartialTest#noStack",
                "com.acme.shop.PartialTest#noMessage"),
                result.failures().stream().map(FailureInput::testId).toList());

        FailureInput noLog = result.failures().get(0);
        assertEquals("no log here", noLog.errorMessage());      // a primeira ocorrencia ganha
        assertEquals(List.of(Gap.LOG_MISSING), noLog.gaps());

        // Sem stack trace: fica vazio e declarado. O log da suite serve de alternativa.
        FailureInput noStack = result.failures().get(1);
        assertEquals("boom", noStack.errorMessage());
        assertEquals("", noStack.stackTrace());
        assertEquals("suite level line A\nsuite level line B", noStack.logExcerpt());
        assertEquals(List.of(Gap.STACK_TRACE_MISSING), noStack.gaps());

        // Sem atributo message: deduz-se da primeira linha do stack.
        FailureInput noMessage = result.failures().get(2);
        assertEquals("derived message", noMessage.errorMessage());
        assertEquals("java.lang.IllegalStateException", noMessage.errorType());
        assertTrue(noMessage.gaps().isEmpty());
    }

    @Test
    void missingOrEmptyReportsDirectoryMeansNoFailures() throws Exception {
        assertTrue(collector.collect(Path.of("no/such/dir")).failures().isEmpty());
        assertTrue(collector.collect(null).failures().isEmpty());
        TestSupport.withTempDir(dir -> assertTrue(collector.collect(dir).failures().isEmpty()));
    }

    @Test
    void unreadableReportsBecomeWarningsAndNeverStopTheOthers() {
        CollectionResult result = collector.collect(TestSupport.resource("triage/surefire/malformed"));
        assertEquals(1, result.failures().size());
        assertEquals("com.acme.shop.OkTest#fails", result.failures().get(0).testId());
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("TEST-broken.xml"));
        // Qualquer DOCTYPE e recusado (fecha a porta a entidades externas, XXE): o relatorio nao e lido.
        assertTrue(result.warnings().get(1).contains("TEST-doctype.xml"));
        assertTrue(result.failures().stream().noneMatch(f -> f.errorMessage().contains("from-a-doctype")));
    }

    @Test
    void logExcerptKeepsOnlyTheLastLines() throws Exception {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 150; i++) {
            log.append("line ").append(i).append('\n');
        }
        String xml = "<testsuite name=\"s\"><testcase name=\"t\" classname=\"c.C\"><failure message=\"m\" type=\"t\">t: m</failure>"
                + "<system-out><![CDATA[" + log + "]]></system-out></testcase></testsuite>";
        TestSupport.withTempDir(dir -> {
            Files.writeString(dir.resolve("TEST-c.C.xml"), xml);
            String excerpt = collector.collect(dir).failures().get(0).logExcerpt();
            assertEquals(100, excerpt.lines().count());
            assertTrue(excerpt.startsWith("line 51"));
            assertTrue(excerpt.endsWith("line 150"));
        });
    }

    @Test
    void sameReportsGiveTheSameResultEveryTime() {
        Path dir = TestSupport.resource("triage/surefire/complete");
        assertEquals(collector.collect(dir), new FailureCollector(100).collect(dir));
    }

    @Test
    void nonReportFilesAreIgnored() throws Exception {
        TestSupport.withTempDir(dir -> {
            Files.writeString(dir.resolve("notes.xml"), "<testsuite/>");
            Files.writeString(dir.resolve("com.acme.C.txt"), "x");
            assertTrue(collector.collect(dir).failures().isEmpty());
            assertTrue(collector.collect(dir).warnings().isEmpty());
        });
    }
}
