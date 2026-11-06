package com.eversonrubira.exporttracking.triage;

import com.eversonrubira.exporttracking.triage.config.TriageConfig;
import com.eversonrubira.exporttracking.triage.rendering.SafeText;
import com.eversonrubira.exporttracking.triage.security.SecretFilter;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Ponto de entrada do AI Test Triage. Java simples, sem Spring: corre no CI
 * depois de os testes falharem e nao precisa de base de dados.
 *
 * Regra de ouro (RF-08, ADR-003): o triage nunca altera o resultado dos testes.
 * Por isso {@link #run} devolve sempre 0 e main nunca termina com erro.
 *
 * O trabalho esta em {@link TriagePipeline}; aqui ficam so a configuracao e a saida UTF-8.
 */
public final class TriageApplication {

    static final String DEFAULT_CONFIG_FILE = "docs/ai-test-triage/triage.properties";

    private TriageApplication() {
    }

    public static void main(String[] args) {
        PrintStream out = utf8(new FileOutputStream(FileDescriptor.out));
        try {
            Path file = Path.of(args.length > 0 ? args[0] : DEFAULT_CONFIG_FILE);
            TriageConfig config = TriageConfig.load(file, System.getProperties());
            run(config, System.getenv(), out);
        } catch (RuntimeException e) {
            // Configuracao invalida nao pode esconder a falha original dos testes.
            // A mensagem pode repetir o valor configurado: passa pela mesma limpeza do resto da saida.
            String message = new SafeText(SecretFilter.fromSystemEnvironment()).clean(e.getMessage()).replace('\n', ' ');
            out.println("AI TEST TRIAGE: skipped, invalid configuration: " + message);
        }
    }

    /**
     * Saida sempre em UTF-8. O stdout do Java segue a locale do sistema e, num runner com
     * locale POSIX, acentos e outros caracteres sairiam como '?'. O diagnostico cita nomes e
     * mensagens reais do projeto (pt/en), por isso a codificacao e fixada aqui.
     */
    static PrintStream utf8(OutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    /** Devolve sempre 0: o resultado do pipeline e decidido pelos testes. */
    public static int run(TriageConfig config, Map<String, String> environment, PrintStream out) {
        return new TriagePipeline(config, environment, out).run();
    }
}
