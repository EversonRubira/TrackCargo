package com.eversonrubira.exporttracking.triage.provider;

import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import com.eversonrubira.exporttracking.triage.json.JsonSupport;
import com.eversonrubira.exporttracking.triage.security.SanitizedContext;
import com.eversonrubira.exporttracking.triage.validation.DiagnosisValidator;
import com.eversonrubira.exporttracking.triage.validation.ValidationResult;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Cliente da Messages API do Claude (T07, ADR-003). Uma chamada, sem tool use.
 *
 * Politica: no maximo {@code maxAttempts} tentativas (teto 2), {@code timeoutSeconds} por
 * tentativa (30 s por omissao). Repete erro transitorio, timeout e resposta invalida
 * (T08); nao repete credencial em falta/invalida nem pedido rejeitado. Nunca lanca por
 * falha do provider: devolve sempre um {@link ProviderOutcome}, para o pipeline degradar
 * em vez de falhar (ADR-003).
 *
 * Segredos: so aceita {@link SanitizedContext}, por isso o corpo enviado ja passou pelo
 * Secret Filter. A chave de API vai so no cabecalho e nunca e escrita no log. O log recebe
 * apenas estado HTTP, nomes de campos e contagens; nunca o corpo do pedido nem o da resposta,
 * porque um servidor (ou proxy) pode devolver o que recebeu.
 */
public final class ClaudeClient {

    static final String API_VERSION = "2023-06-01";
    static final String PATH = "/v1/messages";

    private final TriageConfig config;
    private final String apiKey;
    private final Consumer<String> log;
    private final HttpClient http;

    /**
     * @param apiKey valor da chave (pode ser null/vazio: devolve MISSING_API_KEY sem chamar a rede)
     * @param log    destino de linhas de log seguras (por exemplo System.out)
     */
    public ClaudeClient(TriageConfig config, String apiKey, Consumer<String> log) {
        this.config = config;
        this.apiKey = apiKey;
        this.log = log;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .build();
    }

    public ProviderOutcome diagnose(SanitizedContext context) {
        long started = System.nanoTime();
        if (apiKey == null || apiKey.isBlank()) {
            log.accept("provider: no API key in " + config.apiKeyEnv() + ", request not sent");
            return outcome(null, FailureReason.MISSING_API_KEY, config.apiKeyEnv() + " is not set", 0, started, -1, -1);
        }
        String body = TriagePrompt.requestBody(config.model(), config.maxOutputTokens(), context);

        // O limite de tentativas vive so em willRetry: e a unica condicao que decide repetir.
        Attempt last;
        int attempt = 0;
        while (true) {
            attempt++;
            last = send(body);
            if (last.diagnosisResult != null) {
                log.accept("provider: attempt " + attempt + "/" + config.maxAttempts() + " ok");
                return outcome(last.diagnosisResult, null, "ok", attempt, started, last.inputTokens, last.outputTokens);
            }
            boolean willRetry = last.reason.retryable() && attempt < config.maxAttempts();
            log.accept("provider: attempt " + attempt + "/" + config.maxAttempts() + " failed: " + last.reason
                    + " (" + last.detail + ")" + (willRetry ? ", retrying" : ""));
            if (!willRetry) {
                break;
            }
            if (!pause()) {
                return outcome(null, FailureReason.INTERRUPTED, "interrupted while waiting to retry", attempt, started,
                        last.inputTokens, last.outputTokens);
            }
        }
        return outcome(null, last.reason, last.detail, attempt, started, last.inputTokens, last.outputTokens);
    }

    /** Pedido HTTP de uma tentativa. Package-private para os testes verificarem cabecalhos e timeout. */
    HttpRequest buildRequest(String body) {
        return HttpRequest.newBuilder(endpoint())
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private URI endpoint() {
        String base = config.baseUrl().toString();
        return URI.create(base.endsWith("/") ? base.substring(0, base.length() - 1) + PATH : base + PATH);
    }

    private Attempt send(String body) {
        HttpResponse<String> response;
        try {
            response = http.send(buildRequest(body), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            return Attempt.failed(FailureReason.TIMEOUT, "no response within " + config.timeoutSeconds() + " s");
        } catch (ConnectException e) {
            return Attempt.failed(FailureReason.PROVIDER_ERROR, "could not connect to the provider");
        } catch (IOException e) {
            // e.getMessage() fica de fora do log: pode trazer partes do endereco ou da resposta
            return Attempt.failed(FailureReason.PROVIDER_ERROR, "connection error (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Attempt.failed(FailureReason.INTERRUPTED, "interrupted");
        }
        int status = response.statusCode();
        if (status == 200) {
            return parseSuccess(response.body());
        }
        if (status == 401 || status == 403) {
            return Attempt.failed(FailureReason.INVALID_CREDENTIALS, "HTTP " + status);
        }
        if (status == 408 || status == 429 || status >= 500) {
            return Attempt.failed(FailureReason.PROVIDER_ERROR, "HTTP " + status);
        }
        return Attempt.failed(FailureReason.REQUEST_REJECTED, "HTTP " + status);
    }

    /** Extrai o texto da resposta da Messages API e valida-o (T08). */
    private Attempt parseSuccess(String responseBody) {
        Object parsed;
        try {
            parsed = JsonSupport.read(responseBody);
        } catch (IllegalArgumentException e) {
            return Attempt.failed(FailureReason.INVALID_RESPONSE, "provider envelope is not valid JSON");
        }
        if (!(parsed instanceof Map<?, ?> envelope)) {
            return Attempt.failed(FailureReason.INVALID_RESPONSE, "provider envelope is not a JSON object");
        }
        int inTokens = -1;
        int outTokens = -1;
        if (envelope.get("usage") instanceof Map<?, ?> usage) {
            inTokens = intOrUnknown(usage.get("input_tokens"));
            outTokens = intOrUnknown(usage.get("output_tokens"));
        }
        if ("max_tokens".equals(envelope.get("stop_reason"))) {
            return Attempt.failed(FailureReason.INVALID_RESPONSE, "response cut by the output token limit", inTokens, outTokens);
        }
        StringBuilder text = new StringBuilder();
        if (envelope.get("content") instanceof List<?> blocks) {
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> b && "text".equals(b.get("type")) && b.get("text") instanceof String t) {
                    text.append(t);
                }
            }
        }
        ValidationResult validation = DiagnosisValidator.validate(text.toString());
        if (!validation.isValid()) {
            // as mensagens do validador so falam de campos e limites, nunca do conteudo
            return Attempt.failed(FailureReason.INVALID_RESPONSE,
                    "response does not meet the contract: " + String.join("; ", validation.errors()), inTokens, outTokens);
        }
        return new Attempt(validation, null, "ok", inTokens, outTokens);
    }

    private static int intOrUnknown(Object value) {
        return value instanceof Number n ? n.intValue() : -1;
    }

    private boolean pause() {
        if (config.retryDelayMillis() <= 0) {
            return true;
        }
        try {
            Thread.sleep(config.retryDelayMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ProviderOutcome outcome(ValidationResult ok, FailureReason reason, String detail, int attempts, long startedNanos,
                                    int inTokens, int outTokens) {
        long elapsed = (System.nanoTime() - startedNanos) / 1_000_000;
        return new ProviderOutcome(ok == null ? null : ok.diagnosis(), reason, detail, attempts, elapsed, inTokens, outTokens);
    }

    private record Attempt(ValidationResult diagnosisResult, FailureReason reason, String detail, int inputTokens, int outputTokens) {
        static Attempt failed(FailureReason reason, String detail) {
            return new Attempt(null, reason, detail, -1, -1);
        }

        static Attempt failed(FailureReason reason, String detail, int inputTokens, int outputTokens) {
            return new Attempt(null, reason, detail, inputTokens, outputTokens);
        }
    }
}
