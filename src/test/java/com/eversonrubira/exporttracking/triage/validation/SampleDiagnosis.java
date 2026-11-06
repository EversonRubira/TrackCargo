package com.eversonrubira.exporttracking.triage.validation;

import com.eversonrubira.exporttracking.triage.json.JsonSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resposta valida de exemplo, partilhada pelos testes do validador e do cliente. */
public final class SampleDiagnosis {

    private SampleDiagnosis() {
    }

    /** Mapa mutavel de uma resposta valida; cada teste estraga o campo que quer provar. */
    public static Map<String, Object> valid() {
        Map<String, Object> solution = new LinkedHashMap<>();
        solution.put("solution", "Check the state transition guard in OrderService.ship");
        solution.put("tradeoffs", List.of("Touches shared state-machine code"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("probableCause", "ship() does not validate the current state");
        m.put("evidence", List.of("expected SHIPPED but was CREATED", "OrderService.java:14"));
        m.put("relatedDocuments", List.of("docs/SPEC.md"));
        m.put("possibleSolutions", List.of(solution));
        m.put("confidence", "MEDIUM");
        m.put("confidenceReason", "stack trace points to one class, no log from the call");
        m.put("limitations", List.of("no application log available"));
        return m;
    }

    public static String json(Map<String, Object> m) {
        return JsonSupport.write(m);
    }
}
