package com.eversonrubira.exporttracking.triage.context;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Mapeamento explicito classe/pacote para specs e ADRs (ADR-001). Sem mapeamento
 * nao ha documento: nunca se adivinha por semelhanca de nomes.
 *
 * Formato (docs/ai-test-triage/doc-map.properties):
 *   com.exemplo.pacote.Classe=docs/a.md, docs/b.md#Titulo da seccao
 * A chave mais especifica ganha: o nome exato da classe, ou o prefixo de pacote
 * mais longo. Um valor pode terminar em #Titulo para escolher so essa seccao.
 */
public final class DocumentMap {

    public record DocRef(String path, String heading) {
        public String label() {
            return heading == null ? path : path + "#" + heading;
        }
    }

    private final Map<String, List<DocRef>> entries;
    private final boolean available;

    private DocumentMap(Map<String, List<DocRef>> entries, boolean available) {
        this.entries = entries;
        this.available = available;
    }

    public static DocumentMap empty() {
        return new DocumentMap(Map.of(), false);
    }

    /** Ficheiro em falta devolve um mapa indisponivel (o retriever declara a limitacao). */
    public static DocumentMap load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            return empty();
        }
        Map<String, List<DocRef>> parsed = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            List<DocRef> refs = new ArrayList<>();
            for (String item : properties.getProperty(key).split(",")) {
                String trimmed = item.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int hash = trimmed.indexOf('#');
                if (hash < 0) {
                    refs.add(new DocRef(trimmed, null));
                } else {
                    String heading = trimmed.substring(hash + 1).trim();
                    refs.add(new DocRef(trimmed.substring(0, hash).trim(), heading.isEmpty() ? null : heading));
                }
            }
            parsed.put(key.trim(), refs);
        }
        return new DocumentMap(parsed, true);
    }

    public boolean isAvailable() {
        return available;
    }

    /** Todas as referencias do mapa (usado para verificar que o mapa real nao aponta para o vazio). */
    public List<DocRef> allReferences() {
        List<DocRef> all = new ArrayList<>();
        entries.values().forEach(all::addAll);
        return all;
    }

    /** Para cada classe, os documentos da chave mais especifica. Sem repeticoes, ordem estavel. */
    public List<DocRef> resolve(List<String> classNames) {
        Map<String, DocRef> ordered = new LinkedHashMap<>();
        for (String className : classNames) {
            String best = null;
            for (String key : entries.keySet()) {
                boolean matches = key.equals(className) || className.startsWith(key + ".");
                if (matches && (best == null || key.length() > best.length())) {
                    best = key;
                }
            }
            if (best != null) {
                for (DocRef ref : entries.get(best)) {
                    ordered.putIfAbsent(ref.label(), ref);
                }
            }
        }
        return new ArrayList<>(ordered.values());
    }
}
