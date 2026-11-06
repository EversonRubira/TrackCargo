package com.eversonrubira.exporttracking.triage.context;

import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guarda contra deriva: o mapa real nao pode apontar para ficheiros ou seccoes que ja nao existem.
// Corre a partir da raiz do projeto (diretoria de trabalho do Maven e do IntelliJ).
class DocMapIntegrityTest {

    @Test
    void everyMappedDocumentAndSectionExists() throws Exception {
        TriageConfig config = TriageConfig.defaults();
        DocumentMap map = DocumentMap.load(config.docMapFile());
        assertTrue(map.isAvailable(), "doc-map.properties not found at " + config.docMapFile());
        assertFalse(map.allReferences().isEmpty());
        for (DocumentMap.DocRef ref : map.allReferences()) {
            Path file = config.projectRoot().resolve(ref.path());
            assertTrue(Files.isRegularFile(file), "mapped document does not exist: " + ref.path());
            if (ref.heading() != null) {
                List<String> lines = Files.readAllLines(file);
                boolean found = lines.stream().anyMatch(l -> l.startsWith("#") && l.replaceFirst("^#+\\s+", "").strip().equals(ref.heading()));
                assertTrue(found, "section '" + ref.heading() + "' not found in " + ref.path());
            }
        }
    }
}
