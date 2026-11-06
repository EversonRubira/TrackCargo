package com.eversonrubira.exporttracking.triage.context;

import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.config.TriageConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Retriever deterministico (T03, ADR-001). Parte so de evidencia explicita:
 *
 *  1. o codigo da classe de teste que falhou;
 *  2. as classes do projeto citadas no stack trace, pela ordem em que aparecem;
 *  3. tipos diretamente identificaveis: o tipo da excecao e nomes de classe
 *     escritos na mensagem de erro, se existir um unico ficheiro com esse nome;
 *  4. specs/ADRs, so quando o doc-map liga explicitamente essas classes a eles.
 *
 * Nunca envia o repositorio inteiro. Mesma falha, mesmo contexto: sem relogio,
 * sem aleatoriedade, listagens sempre ordenadas.
 */
public final class DeterministicRetriever {

    private static final Pattern FRAME =
            Pattern.compile("^\\s*at\\s+([\\w.$]+)\\.[\\w$<>]+\\([\\w$]+\\.java:(\\d+)\\)");
    private static final Pattern TOKEN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]{3,}\\b");

    private final TriageConfig config;
    private final DocumentMap docMap;
    private Map<String, List<Path>> sourceIndex;

    public DeterministicRetriever(TriageConfig config, DocumentMap docMap) {
        this.config = config;
        this.docMap = docMap;
    }

    public RetrievalResult retrieve(FailureInput failure) {
        List<Artifact> artifacts = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        Set<Path> included = new HashSet<>();

        String testClass = outerClass(failure.testClass());
        Map<String, Integer> cited = citedClasses(failure.stackTrace());

        // 1. Codigo do teste que falhou.
        Optional<Path> testPath = locate(testClass);
        if (testPath.isPresent()) {
            addSource(ArtifactKind.TEST_SOURCE, testPath.get(), "failed test class",
                    cited.getOrDefault(testClass, 0), artifacts, limitations, included);
        } else {
            limitations.add("Source of the failed test class " + testClass + " was not found in the configured source roots.");
        }

        // 2. Classes do projeto citadas no stack trace.
        int omitted = 0;
        for (Map.Entry<String, Integer> entry : cited.entrySet()) {
            if (entry.getKey().equals(testClass)) {
                continue;
            }
            if (artifacts.size() >= config.maxSourceFiles()) {
                omitted++;
                continue;
            }
            Optional<Path> path = locate(entry.getKey());
            if (path.isPresent()) {
                addSource(ArtifactKind.CITED_SOURCE, path.get(), "class cited in the stack trace",
                        entry.getValue(), artifacts, limitations, included);
            } else {
                limitations.add("Class cited in the stack trace was not found in the configured source roots: " + entry.getKey());
            }
        }
        if (omitted > 0) {
            limitations.add(omitted + " further class(es) cited in the stack trace were omitted (limit of "
                    + config.maxSourceFiles() + " source files).");
        }

        // 3. Tipos diretamente identificaveis.
        for (Candidate candidate : relatedCandidates(failure)) {
            if (artifacts.size() >= config.maxSourceFiles()) {
                break;
            }
            if (!included.contains(candidate.path())) {
                addSource(ArtifactKind.RELATED_SOURCE, candidate.path(), candidate.reason(),
                        0, artifacts, limitations, included);
            }
        }

        // 4. Specs/ADRs mapeados explicitamente.
        addDocuments(testClass, cited, artifacts, limitations);

        return new RetrievalResult(artifacts, limitations);
    }

    // ---------- codigo ----------

    private void addSource(ArtifactKind kind, Path path, String reason, int centerLine,
                           List<Artifact> artifacts, List<String> limitations, Set<Path> included) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            limitations.add("Could not read source file " + display(path) + ".");
            return;
        }
        included.add(path);
        int total = lines.size();
        int from;
        int to;
        if (total <= 2 * config.regionRadiusLines() + 1) {
            from = 1;
            to = total;
        } else if (centerLine > 0) {
            from = Math.max(1, centerLine - config.regionRadiusLines());
            to = Math.min(total, centerLine + config.regionRadiusLines());
        } else {
            from = 1;
            to = 2 * config.regionRadiusLines() + 1;
        }
        StringBuilder text = new StringBuilder();
        for (int n = from; n <= to; n++) {
            text.append(String.format("%4d: %s%n", n, lines.get(n - 1)));
        }
        Capped capped = cap(text.toString());
        String region = (from == 1 && to == total) ? "whole file" : "lines " + from + "-" + to + " of " + total;
        artifacts.add(new Artifact(kind, display(path), reason + " (" + region + ")", capped.text(), capped.truncated()));
    }

    /** Classes do projeto no stack trace, ordem de aparecimento, com a primeira linha citada. */
    private Map<String, Integer> citedClasses(String stackTrace) {
        Map<String, Integer> cited = new LinkedHashMap<>();
        for (String line : stackTrace.split("\n")) {
            Matcher m = FRAME.matcher(line);
            if (!m.find()) {
                continue;
            }
            String className = outerClass(m.group(1));
            if (isProjectClass(className)) {
                cited.putIfAbsent(className, Integer.parseInt(m.group(2)));
            }
        }
        return cited;
    }

    private record Candidate(Path path, String reason) {
    }

    private List<Candidate> relatedCandidates(FailureInput failure) {
        List<Candidate> candidates = new ArrayList<>();
        String type = outerClass(failure.errorType());
        if (isProjectClass(type)) {
            locate(type).ifPresent(p -> candidates.add(new Candidate(p, "exception type of the failure")));
        }
        Set<String> seenTokens = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(failure.errorMessage());
        while (m.find()) {
            seenTokens.add(m.group());
        }
        for (String token : seenTokens) {
            List<Path> matches = index().getOrDefault(token, List.of());
            if (matches.size() == 1) {
                candidates.add(new Candidate(matches.get(0), "class name '" + token + "' mentioned in the error message"));
            }
        }
        return candidates;
    }

    private Optional<Path> locate(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        String relative = className.replace('.', '/') + ".java";
        for (Path root : config.sourceRoots()) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Nome simples -> ficheiros .java, construido uma vez e sempre ordenado. */
    private Map<String, List<Path>> index() {
        if (sourceIndex == null) {
            Map<String, List<Path>> built = new TreeMap<>();
            for (Path root : config.sourceRoots()) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                        String name = file.getFileName().toString();
                        built.computeIfAbsent(name.substring(0, name.length() - 5), k -> new ArrayList<>()).add(file);
                    }
                } catch (IOException e) {
                    // raiz ilegivel: fica fora do indice
                }
            }
            sourceIndex = built;
        }
        return sourceIndex;
    }

    private boolean isProjectClass(String className) {
        if (className == null) {
            return false;
        }
        for (String pkg : config.projectPackages()) {
            if (className.startsWith(pkg + ".")) {
                return true;
            }
        }
        return false;
    }

    /** Remove classes internas e proxies: Foo$Bar, Foo$$SpringCGLIB$$0 -> Foo. */
    static String outerClass(String className) {
        if (className == null) {
            return "";
        }
        int dollar = className.indexOf('$');
        return dollar < 0 ? className : className.substring(0, dollar);
    }

    // ---------- documentos ----------

    private void addDocuments(String testClass, Map<String, Integer> cited,
                              List<Artifact> artifacts, List<String> limitations) {
        if (!docMap.isAvailable()) {
            limitations.add("The document map was not found (" + display(config.docMapFile())
                    + "), so no spec or ADR could be mapped to this failure.");
            return;
        }
        List<String> classes = new ArrayList<>();
        classes.add(testClass);
        classes.addAll(cited.keySet());
        List<DocumentMap.DocRef> refs = docMap.resolve(classes);
        if (refs.isEmpty()) {
            limitations.add("No spec or ADR is mapped to " + testClass
                    + " or to the classes cited in the stack trace; related documentation may exist but was not found.");
            return;
        }
        int added = 0;
        for (DocumentMap.DocRef ref : refs) {
            if (added >= config.maxDocuments()) {
                limitations.add((refs.size() - added) + " mapped document(s) were omitted (limit of "
                        + config.maxDocuments() + " documents).");
                break;
            }
            Path file = config.projectRoot().resolve(ref.path()).normalize();
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                limitations.add("Mapped document was not found or unreadable: " + ref.path());
                continue;
            }
            String body;
            String reason;
            if (ref.heading() == null) {
                body = String.join("\n", lines);
                reason = "document mapped in the document map";
            } else {
                Optional<String> section = section(lines, ref.heading());
                if (section.isEmpty()) {
                    limitations.add("Section '" + ref.heading() + "' was not found in mapped document " + ref.path());
                    continue;
                }
                body = section.get();
                reason = "section '" + ref.heading() + "' mapped in the document map";
            }
            Capped capped = cap(body);
            artifacts.add(new Artifact(ArtifactKind.DOCUMENT, ref.path(), reason, capped.text(), capped.truncated()));
            added++;
        }
    }

    /** Da linha de titulo ate ao proximo titulo do mesmo nivel (ou superior). */
    private static Optional<String> section(List<String> lines, String heading) {
        int start = -1;
        int level = 0;
        for (int i = 0; i < lines.size(); i++) {
            int l = headingLevel(lines.get(i));
            if (l > 0 && lines.get(i).substring(l).strip().equals(heading)) {
                start = i;
                level = l;
                break;
            }
        }
        if (start < 0) {
            return Optional.empty();
        }
        int end = lines.size();
        for (int i = start + 1; i < lines.size(); i++) {
            int l = headingLevel(lines.get(i));
            if (l > 0 && l <= level) {
                end = i;
                break;
            }
        }
        return Optional.of(String.join("\n", lines.subList(start, end)));
    }

    private static int headingLevel(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == '#') {
            n++;
        }
        return (n > 0 && n <= 6 && n < line.length() && line.charAt(n) == ' ') ? n : 0;
    }

    // ---------- util ----------

    private record Capped(String text, boolean truncated) {
    }

    /** Corta ao limite de caracteres por artefacto, sempre numa fronteira de linha. */
    private Capped cap(String text) {
        int max = config.maxCharsPerArtifact();
        if (text.length() <= max) {
            return new Capped(text, false);
        }
        int cut = text.lastIndexOf('\n', max);
        return new Capped(text.substring(0, cut > 0 ? cut : max), true);
    }

    private String display(Path path) {
        Path root = config.projectRoot();
        Path relative = path.startsWith(root) ? root.relativize(path) : path;
        return relative.toString().replace('\\', '/');
    }
}
