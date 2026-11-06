package com.eversonrubira.exporttracking.triage.provider;

import com.eversonrubira.exporttracking.triage.json.JsonSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP local que faz de Claude nos testes. Nenhum teste automatico chama o
 * Claude real. Responde por guiao: a n-esima chamada recebe a n-esima resposta e a
 * ultima repete-se. Guarda o que recebeu para os testes inspecionarem.
 */
public final class FakeProvider implements AutoCloseable {

    public record Reply(int status, String body, long delayMillis) {
    }

    public record Seen(String method, String path, Map<String, String> headers, String body) {
    }

    private final HttpServer server;
    private final List<Reply> script;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();

    public FakeProvider(Reply... script) throws IOException {
        this.script = List.of(script);
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public List<Seen> seen() {
        return seen;
    }

    public int hits() {
        return seen.size();
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : List.of("x-api-key", "anthropic-version", "content-type")) {
            headers.put(name, exchange.getRequestHeaders().getFirst(name));
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int index = seen.size();
        seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));
        Reply reply = script.get(Math.min(index, script.size() - 1));
        try {
            if (reply.delayMillis() > 0) {
                Thread.sleep(reply.delayMillis());
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException | InterruptedException e) {
            // o cliente desistiu (timeout): normal nos testes de timeout
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- respostas prontas ----

    public static Reply ok(String assistantText) {
        return new Reply(200, envelope(assistantText, "end_turn"), 0);
    }

    public static Reply okCut(String assistantText) {
        return new Reply(200, envelope(assistantText, "max_tokens"), 0);
    }

    public static Reply status(int status, String body) {
        return new Reply(status, body, 0);
    }

    public static Reply slow(long delayMillis, String assistantText) {
        return new Reply(200, envelope(assistantText, "end_turn"), delayMillis);
    }

    /** Envelope da Messages API: content[] com blocos de texto, usage e stop_reason. */
    public static String envelope(String assistantText, String stopReason) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", assistantText);
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("input_tokens", 1234);
        usage.put("output_tokens", 321);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", "msg_test");
        envelope.put("type", "message");
        envelope.put("role", "assistant");
        envelope.put("content", new ArrayList<>(List.of(block)));
        envelope.put("stop_reason", stopReason);
        envelope.put("usage", usage);
        return JsonSupport.write(envelope);
    }
}
