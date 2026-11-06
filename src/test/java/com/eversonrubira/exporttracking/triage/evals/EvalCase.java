package com.eversonrubira.exporttracking.triage.evals;

import com.eversonrubira.exporttracking.triage.json.JsonSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Um caso do dataset congelado (T13), lido do case.json. So dados: nao chama nada. */
record EvalCase(
        Path dir,
        String id,
        String title,
        String difficulty,
        List<String> categories,
        String failedTest,
        String presentedFailure,
        String knownRootCause,
        List<String> expectedEvidence,
        List<String> relatedSpecs,
        List<String> expectedArtifacts,
        List<String> expectedLimitationsContain,
        String answerability,
        Usefulness usefulness,
        String secretFixture) {

    /** Criterios objetivos de utilidade (o piso automatico; a revisao humana fica em manualChecks). */
    record Usefulness(
            String maxConfidence,
            List<List<String>> mustMention,
            List<String> causeMustNotMention,
            List<String> mustNotMention,
            boolean mustDeclareLimitations,
            boolean mustCiteRelatedDocument,
            List<String> manualChecks) {
    }

    static final Path DATASET_DIR = Path.of("docs/ai-test-triage/evals/dataset-v1");

    Path projectDir() {
        return dir.resolve("project");
    }

    Path referenceDiagnosis() {
        return dir.resolve("reference/good-diagnosis.json");
    }

    static List<EvalCase> loadAll() throws IOException {
        List<Path> dirs;
        try (Stream<Path> s = Files.list(DATASET_DIR)) {
            dirs = s.filter(Files::isDirectory).filter(p -> p.getFileName().toString().startsWith("case-")).sorted().toList();
        }
        List<EvalCase> cases = new ArrayList<>();
        for (Path dir : dirs) {
            cases.add(load(dir));
        }
        return cases;
    }

    @SuppressWarnings("unchecked")
    static EvalCase load(Path dir) throws IOException {
        Map<String, Object> m = (Map<String, Object>) JsonSupport.read(Files.readString(dir.resolve("case.json")));
        Map<String, Object> retrieval = (Map<String, Object>) m.get("retrieval");
        Map<String, Object> u = (Map<String, Object>) m.get("usefulness");
        List<List<String>> groups = new ArrayList<>();
        for (Object group : (List<Object>) u.get("mustMention")) {
            groups.add((List<String>) group);
        }
        Usefulness usefulness = new Usefulness(
                (String) u.get("maxConfidence"),
                groups,
                (List<String>) u.get("causeMustNotMention"),
                (List<String>) u.get("mustNotMention"),
                (Boolean) u.get("mustDeclareLimitations"),
                (Boolean) u.get("mustCiteRelatedDocument"),
                (List<String>) u.get("manualChecks"));
        return new EvalCase(dir,
                (String) m.get("id"), (String) m.get("title"), (String) m.get("difficulty"),
                (List<String>) m.get("categories"), (String) m.get("failedTest"),
                (String) m.get("presentedFailure"), (String) m.get("knownRootCause"),
                (List<String>) m.get("expectedEvidence"), (List<String>) m.get("relatedSpecs"),
                (List<String>) retrieval.get("expectedArtifacts"),
                (List<String>) retrieval.get("expectedLimitationsContain"),
                (String) retrieval.get("answerability"),
                usefulness, (String) m.get("secretFixture"));
    }
}
