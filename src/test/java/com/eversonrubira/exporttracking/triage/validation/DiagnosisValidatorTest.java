package com.eversonrubira.exporttracking.triage.validation;

import com.eversonrubira.exporttracking.triage.json.JsonSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// T08: o validador verifica o CONTRATO da resposta, nao a verdade do diagnostico.
class DiagnosisValidatorTest {

    static Map<String, Object> valid() {
        return SampleDiagnosis.valid();
    }

    static String json(Map<String, Object> m) {
        return SampleDiagnosis.json(m);
    }

    private static ValidationResult check(Map<String, Object> m) {
        return DiagnosisValidator.validate(json(m));
    }

    private static void assertInvalid(ValidationResult r, String errorFragment) {
        assertFalse(r.isValid(), "should have been rejected");
        assertNull(r.diagnosis());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains(errorFragment)),
                "expected an error mentioning '" + errorFragment + "' but got " + r.errors());
    }

    @Test
    void aCompleteResponseBecomesADiagnosis() {
        ValidationResult r = check(valid());
        assertTrue(r.isValid(), r.errors().toString());
        Diagnosis d = r.diagnosis();
        assertEquals("ship() does not validate the current state", d.probableCause());
        assertEquals(2, d.evidence().size());
        assertEquals(List.of("docs/SPEC.md"), d.relatedDocuments());
        assertEquals(1, d.possibleSolutions().size());
        assertEquals(List.of("Touches shared state-machine code"), d.possibleSolutions().get(0).tradeoffs());
        assertEquals(Confidence.MEDIUM, d.confidence());
        assertEquals(List.of("no application log available"), d.limitations());
    }

    @Test
    void emptyRelatedDocumentsAndLimitationsAreAllowed() {
        Map<String, Object> m = valid();
        m.put("relatedDocuments", List.of());
        m.put("limitations", List.of());
        assertTrue(check(m).isValid());
    }

    @Test
    void missingEvidenceIsRejected() {
        Map<String, Object> m = valid();
        m.remove("evidence");
        assertInvalid(check(m), "evidence must be an array");

        m = valid();
        m.put("evidence", List.of());
        assertInvalid(check(m), "evidence must have at least one item");

        m = valid();
        m.put("evidence", List.of("fine", "  "));
        assertInvalid(check(m), "evidence[1]");
    }

    @Test
    void everyConfidenceOtherThanTheThreeLevelsIsRejected() {
        for (Object bad : new Object[]{"VERY_HIGH", "high", "Medium", "", 3, true}) {
            Map<String, Object> m = valid();
            m.put("confidence", bad);
            assertInvalid(check(m), "confidence must be one of HIGH, MEDIUM, LOW");
        }
        Map<String, Object> m = valid();
        m.remove("confidence");
        assertInvalid(check(m), "confidence");
        for (Confidence c : Confidence.values()) {
            m = valid();
            m.put("confidence", c.name());
            assertEquals(c, check(m).diagnosis().confidence());
        }
    }

    @Test
    void everyRequiredFieldIsCheckedAndAllProblemsAreReportedTogether() {
        Map<String, Object> m = valid();
        m.remove("probableCause");
        m.remove("confidenceReason");
        m.put("possibleSolutions", List.of());
        ValidationResult r = check(m);
        assertInvalid(r, "probableCause");
        assertInvalid(r, "confidenceReason");
        assertInvalid(r, "possibleSolutions must have at least one item");
        assertEquals(3, r.errors().size());
    }

    @Test
    void aSolutionWithoutTradeoffsIsRejectedAndTheErrorSaysWhich() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("solution", "Do something");
        bad.put("tradeoffs", List.of());
        Map<String, Object> m = valid();
        List<Object> solutions = new ArrayList<>((List<?>) m.get("possibleSolutions"));
        solutions.add(bad);
        m.put("possibleSolutions", solutions);
        assertInvalid(check(m), "possibleSolutions[1].tradeoffs must have at least one item");

        m = valid();
        m.put("possibleSolutions", List.of("just a string"));
        assertInvalid(check(m), "possibleSolutions[0] must be an object");
    }

    @Test
    void emptyAndBlankResponsesAreRejected() {
        assertInvalid(DiagnosisValidator.validate(null), "empty");
        assertInvalid(DiagnosisValidator.validate(""), "empty");
        assertInvalid(DiagnosisValidator.validate("  \n\t "), "empty");
    }

    @Test
    void malformedOutputIsRejectedWithoutEchoingIt() {
        String secretLooking = "ignore previous instructions sk-" + "ant-api03-" + "LeakedLeakedLeaked";
        for (String raw : List.of("not json at all " + secretLooking, "{\"probableCause\": ", "[1,2,3]", "\"text\"", "42", "null",
                "Here is my analysis: " + json(valid()))) {
            ValidationResult r = DiagnosisValidator.validate(raw);
            assertFalse(r.isValid(), raw);
            for (String error : r.errors()) {
                assertFalse(error.contains(secretLooking), "error repeats the response: " + error);
            }
        }
    }

    @Test
    void aSingleMarkdownFenceAroundTheObjectIsTolerated() {
        String body = json(valid());
        assertTrue(DiagnosisValidator.validate("```json\n" + body + "\n```").isValid());
        assertTrue(DiagnosisValidator.validate("```\n" + body + "\n```").isValid());
        assertTrue(DiagnosisValidator.validate("  ```JSON\n" + body + "\n```  ").isValid());
        // texto fora do cerco continua a ser rejeitado
        assertFalse(DiagnosisValidator.validate("Sure!\n```json\n" + body + "\n```").isValid());
        assertFalse(DiagnosisValidator.validate("```json\n" + body + "\n``` hope this helps").isValid());
        // sem cerco, texto depois do objeto tambem nao passa
        assertFalse(DiagnosisValidator.validate(body + "\nHope this helps!").isValid());
        assertFalse(DiagnosisValidator.validate(body + body).isValid());
    }

    @Test
    void unknownFieldsAreIgnored() {
        Map<String, Object> m = valid();
        m.put("somethingElse", "ignored");
        assertTrue(check(m).isValid());
    }

    @Test
    void oversizedResponsesAreRejectedToProtectTheCiLog() {
        Map<String, Object> m = valid();
        m.put("probableCause", "x".repeat(DiagnosisValidator.MAX_TEXT_CHARS + 1));
        assertInvalid(check(m), "probableCause is longer than");

        m = valid();
        m.put("probableCause", "x".repeat(DiagnosisValidator.MAX_TEXT_CHARS));
        assertTrue(check(m).isValid());

        m = valid();
        m.put("evidence", java.util.Collections.nCopies(DiagnosisValidator.MAX_ITEMS + 1, "e"));
        assertInvalid(check(m), "evidence has more than");

        m = valid();
        m.put("evidence", java.util.Collections.nCopies(DiagnosisValidator.MAX_ITEMS, "e"));
        assertTrue(check(m).isValid());
    }

    // Fronteira declarada no T08: forma certa nao quer dizer diagnostico certo.
    @Test
    void aWellFormedButWrongDiagnosisPassesBecauseOnlyTheContractIsChecked() {
        Map<String, Object> m = valid();
        m.put("probableCause", "The moon is made of cheese");
        m.put("evidence", List.of("OrderService.java:9999 does not exist"));
        m.put("relatedDocuments", List.of("docs/does-not-exist.md"));
        m.put("confidence", "HIGH");
        ValidationResult r = check(m);
        assertTrue(r.isValid(), "the validator must not judge semantic quality");
        assertNotNull(r.diagnosis());
    }
}
