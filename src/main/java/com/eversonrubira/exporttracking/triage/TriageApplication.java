package com.eversonrubira.exporttracking.triage;

import com.eversonrubira.exporttracking.triage.config.TriageConfig;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Ponto de entrada do AI Test Triage. Java simples, sem Spring: corre no CI
 * depois de os testes falharem e nao precisa de base de dados.
 *
 * Regra de ouro (RF-08, ADR-003): o triage nunca altera o resultado dos testes.
 * Por isso {@link #run} devolve sempre 0 e main nunca termina com erro.
 *
 * T01: esqueleto. Ainda nao existe pipeline nem chamada ao Claude.
 */
public final class TriageApplication {

    static final String DEFAULT_CONFIG_FILE = "docs/ai-test-triage/triage.properties";

    private TriageApplication() {
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        try {
            Path file = Path.of(args.length > 0 ? args[0] : DEFAULT_CONFIG_FILE);
            TriageConfig config = TriageConfig.load(file, System.getProperties());
            run(config, out);
        } catch (RuntimeException e) {
            // Configuracao invalida nao pode esconder a falha original dos testes.
            out.println("AI TEST TRIAGE: skipped, invalid configuration: " + e.getMessage());
        }
    }

    /** Devolve sempre 0: o resultado do pipeline e decidido pelos testes. */
    public static int run(TriageConfig config, PrintStream out) {
        if (!config.enabled()) {
            out.println("AI TEST TRIAGE: disabled (triage.enabled=false)");
            return 0;
        }
        out.println("AI TEST TRIAGE: enabled, pipeline not wired yet");
        return 0;
    }
}
