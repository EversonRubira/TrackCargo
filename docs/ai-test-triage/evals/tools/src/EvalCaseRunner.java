import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Corre as classes de teste de um caso e escreve um TEST-*.xml no formato do Surefire.
 * Ordem dos metodos = a do JUnit 5 por omissao (hashCode do nome). Os frames desta classe
 * saem do stack trace (como o trimStackTrace do Surefire); o resto e o que a JVM produziu.
 */
public class EvalCaseRunner {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        for (int i = 1; i < args.length; i++) runClass(Class.forName(args[i]), out);
    }

    static void runClass(Class<?> c, Path out) throws Exception {
        List<Method> methods = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) if (m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) methods.add(m);
        methods.sort((a, b) -> { int r = Integer.compare(a.getName().hashCode(), b.getName().hashCode()); return r != 0 ? r : a.getName().compareTo(b.getName()); });
        StringBuilder cases = new StringBuilder();
        int failures = 0, errors = 0;
        PrintStream realOut = System.out;
        for (Method m : methods) {
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            long t0 = System.nanoTime();
            Throwable failure = null;
            try { Constructor<?> k = c.getDeclaredConstructor(); k.setAccessible(true); m.setAccessible(true); m.invoke(k.newInstance()); }
            catch (InvocationTargetException e) { failure = e.getCause(); }
            catch (Exception e) { throw e; }
            finally { System.setOut(realOut); }
            double secs = (System.nanoTime() - t0) / 1e9;
            cases.append(String.format(Locale.ROOT, "  <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\"", m.getName(), c.getName(), secs));
            String log = captured.toString(StandardCharsets.UTF_8);
            if (failure == null && log.isEmpty()) { cases.append("/>\n"); continue; }
            cases.append(">\n");
            if (failure != null) {
                boolean assertion = failure instanceof AssertionError;
                if (assertion) failures++; else errors++;
                String tag = assertion ? "failure" : "error";
                cases.append("    <").append(tag).append(" message=\"").append(esc(String.valueOf(failure.getMessage()))).append("\" type=\"")
                     .append(failure.getClass().getName()).append("\"><![CDATA[").append(trace(failure)).append("]]></").append(tag).append(">\n");
            }
            if (!log.isEmpty()) cases.append("    <system-out><![CDATA[").append(log).append("]]></system-out>\n");
            cases.append("  </testcase>\n");
        }
        String xml = String.format(Locale.ROOT, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"%s\" tests=\"%d\" errors=\"%d\" skipped=\"0\" failures=\"%d\">\n%s</testsuite>\n",
                c.getName(), methods.size(), errors, failures, cases);
        Files.writeString(out.resolve("TEST-" + c.getName() + ".xml"), xml, StandardCharsets.UTF_8);
    }

    static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        StringBuilder sb = new StringBuilder();
        for (String line : sw.toString().split("\n")) if (!line.contains("EvalCaseRunner.")) sb.append(line).append('\n');
        return sb.toString();
    }
    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "&#10;"); }
}
