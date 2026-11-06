package com.eversonrubira.exporttracking.triage.context;

import java.util.List;

public record RetrievalResult(List<Artifact> artifacts, List<String> limitations) {

    public RetrievalResult {
        artifacts = List.copyOf(artifacts);
        limitations = List.copyOf(limitations);
    }
}
