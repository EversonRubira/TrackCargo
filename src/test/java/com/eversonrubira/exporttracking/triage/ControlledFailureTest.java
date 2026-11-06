package com.eversonrubira.exporttracking.triage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

// TEMPORARIO (T10/T11): falhas deliberadas para provar o caminho do triage no CI. Este ficheiro
// existe apenas num commit de validacao e e revertido a seguir.
class ControlledFailureTest {
    @Test void controlledFailure1() { fail("CONTROLLED-TRIAGE-VALIDATION-FAILURE 1"); }
    @Test void controlledFailure2() { fail("CONTROLLED-TRIAGE-VALIDATION-FAILURE 2"); }
    @Test void controlledFailure3() { fail("CONTROLLED-TRIAGE-VALIDATION-FAILURE 3"); }
    @Test void controlledFailure4() { fail("CONTROLLED-TRIAGE-VALIDATION-FAILURE 4"); }
    @Test void controlledFailure5() { fail("CONTROLLED-TRIAGE-VALIDATION-FAILURE 5"); }
    @Test void controlledFailure6() { fail("CONTROLLED-TRIAGE-VALIDATION-FAILURE 6"); }
    @Test void controlledFailure7() { fail("CONTROLLED-TRIAGE-VALIDATION-FAILURE 7"); }
}
