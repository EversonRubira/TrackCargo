package com.eversonrubira.exporttracking.triage.collector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Failure Collector (T02): le os relatorios TEST-*.xml do Surefire e devolve os
 * testes falhados normalizados. Deterministico: ficheiros por ordem alfabetica,
 * testes pela ordem do documento. Sem rede, sem escrita.
 *
 * Os relatorios sao dados nao confiaveis (RNF-01): o parser XML recusa DOCTYPE,
 * o que fecha a porta a entidades externas (XXE).
 */
public final class FailureCollector {

    private final int maxLogLines;

    public FailureCollector(int maxLogLines) {
        this.maxLogLines = maxLogLines;
    }

    public CollectionResult collect(Path reportsDir) {
        List<String> warnings = new ArrayList<>();
        Map<String, FailureInput> distinct = new LinkedHashMap<>();
        if (reportsDir == null || !Files.isDirectory(reportsDir)) {
            return new CollectionResult(List.of(), List.of());
        }
        for (Path file : reportFiles(reportsDir, warnings)) {
            try {
                for (FailureInput failure : parse(file)) {
                    // So se removem repeticoes exatas (mesma classe e nome). Sem agrupamento (ADR-004).
                    distinct.putIfAbsent(failure.testId(), failure);
                }
            } catch (Exception e) {
                warnings.add("Could not read test report " + file.getFileName() + ": " + e.getClass().getSimpleName());
            }
        }
        return new CollectionResult(new ArrayList<>(distinct.values()), warnings);
    }

    private static List<Path> reportFiles(Path dir, List<String> warnings) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("TEST-") && name.endsWith(".xml");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            warnings.add("Could not list test reports directory: " + e.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<FailureInput> parse(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler()); // erros viram excecao, sem escrever no stderr
        Document doc = builder.parse(file.toFile());

        List<FailureInput> failures = new ArrayList<>();
        NodeList cases = doc.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element testcase = (Element) cases.item(i);
            Element problem = firstChild(testcase, "failure");
            if (problem == null) {
                problem = firstChild(testcase, "error");
            }
            if (problem == null) {
                continue; // passou ou foi ignorado
            }
            failures.add(toInput(testcase, problem));
        }
        return failures;
    }

    private FailureInput toInput(Element testcase, Element problem) {
        String suiteName = "";
        if (testcase.getParentNode() instanceof Element suite) {
            suiteName = suite.getAttribute("name");
        }
        String testClass = firstNonBlank(testcase.getAttribute("classname"), suiteName);
        String testMethod = testcase.getAttribute("name");

        String stack = normalize(problem.getTextContent());
        String type = problem.getAttribute("type").trim();
        String message = normalize(problem.getAttribute("message"));

        // Sem atributo message, a primeira linha do stack ("tipo: mensagem") ainda a revela.
        if (!stack.isEmpty()) {
            String first = stack.lines().findFirst().orElse("");
            int colon = first.indexOf(": ");
            if (type.isEmpty()) {
                type = colon > 0 ? first.substring(0, colon).trim() : first.trim();
            }
            if (message.isEmpty() && colon > 0) {
                message = first.substring(colon + 2).trim();
            }
        }

        String log = logExcerpt(testcase);

        List<Gap> gaps = new ArrayList<>();
        if (message.isEmpty()) {
            gaps.add(Gap.MESSAGE_MISSING);
        }
        if (stack.isEmpty()) {
            gaps.add(Gap.STACK_TRACE_MISSING);
        }
        if (log.isEmpty()) {
            gaps.add(Gap.LOG_MISSING);
        }
        return new FailureInput(testClass, testMethod, type, message, stack, log, gaps);
    }

    /** Log do proprio teste; se nao houver, o log da suite. Fica com as ultimas linhas. */
    private String logExcerpt(Element testcase) {
        StringBuilder text = new StringBuilder();
        append(text, firstChild(testcase, "system-out"));
        append(text, firstChild(testcase, "system-err"));
        if (text.toString().isBlank() && testcase.getParentNode() instanceof Element suite) {
            text.setLength(0);
            append(text, firstChild(suite, "system-out"));
            append(text, firstChild(suite, "system-err"));
        }
        List<String> lines = normalize(text.toString()).lines().toList();
        if (lines.size() > maxLogLines) {
            lines = lines.subList(lines.size() - maxLogLines, lines.size());
        }
        return String.join("\n", lines).trim();
    }

    private static void append(StringBuilder target, Element element) {
        if (element != null) {
            target.append(element.getTextContent()).append('\n');
        }
    }

    private static Element firstChild(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }
}
