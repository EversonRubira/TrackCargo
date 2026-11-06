package com.eversonrubira.exporttracking.triage.security;

import com.eversonrubira.exporttracking.triage.collector.FailureInput;
import com.eversonrubira.exporttracking.triage.context.Artifact;
import com.eversonrubira.exporttracking.triage.context.ContextPackage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Secret Filter (T05, RNF-06): mascara credenciais antes de qualquer envio ao
 * Claude e antes de qualquer telemetria. Tem de ser o ultimo passo antes de sair
 * do processo; o resto do codigo assume que texto nao filtrado nao sai.
 *
 * Tres camadas, por ordem:
 *  1. valores conhecidos: o valor de variaveis de ambiente com nome de segredo
 *     (ANTHROPIC_API_KEY, GITHUB_TOKEN, DB_PASSWORD...) e mascarado onde quer que apareca;
 *  2. formatos conhecidos: chaves de fornecedores, Bearer/Basic, JWT, blocos PEM, user:senha@ em URLs;
 *  3. atribuicoes: chave que TERMINA em palavra de segredo seguida de : ou = (password=x, "apiKey": "x").
 *     Exigir o fim da chave evita mascarar nomes de classes como SenhaCurtaException.
 *
 * E conservador de proposito: prefere mascarar a mais a deixar passar. Idempotente.
 */
public final class SecretFilter {

    public static final String MASK = "[REDACTED]";

    private static final int MIN_ENV_VALUE_LENGTH = 8;

    private static final Pattern SECRET_ENV_NAME = Pattern.compile(
            "(?i).*(password|passwd|secret|token|api[_-]?key|credential|private[_-]?key|access[_-]?key)$");

    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", Pattern.DOTALL);
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern VENDOR_TOKENS = Pattern.compile(
            "sk-ant-[A-Za-z0-9_-]{10,}"
                    + "|sk-[A-Za-z0-9_-]{20,}"
                    + "|gh[pousr]_[A-Za-z0-9]{20,}"
                    + "|github_pat_[A-Za-z0-9_]{20,}"
                    + "|AKIA[0-9A-Z]{16}"
                    + "|xox[baprs]-[A-Za-z0-9-]{10,}"
                    + "|eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "(?i)([a-z][a-z0-9+.-]*://[^\\s/:@]+:)[^\\s/@]+(@)");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "(?i)(\\b[\\w.-]*(?:password|passwd|pwd|senha|secret|token|api[_-]?key|credentials?|private[_-]?key|access[_-]?key|authorization)[_.-]?\\d*[\"']?\\s*[:=]\\s*)"
                    // Ignora valores ja mascarados e o esquema Bearer/Basic (esse e tratado acima).
                    + "(?!\\[REDACTED\\]|(?i:Bearer|Basic)\\s)"
                    + "(\"[^\"\\n]*\"|'[^'\\n]*'|[^\\s,;&\"'}\\]]+)");

    private final List<String> knownValues;

    /** {@code environment}: nome -> valor. Guarda so os valores de variaveis com nome de segredo. */
    public SecretFilter(Map<String, String> environment) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.length() >= MIN_ENV_VALUE_LENGTH
                    && SECRET_ENV_NAME.matcher(entry.getKey()).matches()) {
                values.add(value);
            }
        }
        // Mais longos primeiro: um valor que contem outro nao deixa sobras.
        values.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        this.knownValues = List.copyOf(values);
    }

    public static SecretFilter fromSystemEnvironment() {
        return new SecretFilter(System.getenv());
    }

    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String result = text;
        for (String value : knownValues) {
            result = result.replace(value, MASK);
        }
        result = PEM.matcher(result).replaceAll(MASK);
        result = AUTH_SCHEME.matcher(result).replaceAll("$1 " + MASK);
        result = VENDOR_TOKENS.matcher(result).replaceAll(MASK);
        result = URL_CREDENTIALS.matcher(result).replaceAll("$1" + MASK + "$2");
        result = ASSIGNMENT.matcher(result).replaceAll("$1" + MASK);
        return result;
    }

    /** Unico caminho para obter um {@link SanitizedContext}, o unico tipo que o provider aceita. */
    public SanitizedContext sanitize(ContextPackage pkg) {
        return new SanitizedContext(mask(pkg));
    }

    public ContextPackage mask(ContextPackage pkg) {
        FailureInput f = pkg.failure();
        FailureInput maskedFailure = new FailureInput(
                mask(f.testClass()), mask(f.testMethod()), mask(f.errorType()), mask(f.errorMessage()),
                mask(f.stackTrace()), mask(f.logExcerpt()), f.gaps());
        List<Artifact> artifacts = new ArrayList<>();
        for (Artifact a : pkg.artifacts()) {
            artifacts.add(new Artifact(a.kind(), mask(a.path()), mask(a.reason()), mask(a.content()), a.truncated()));
        }
        List<String> limitations = new ArrayList<>();
        for (String l : pkg.limitations()) {
            limitations.add(mask(l));
        }
        return new ContextPackage(maskedFailure, artifacts, limitations);
    }
}
