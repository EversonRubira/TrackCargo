package com.eversonrubira.exporttracking.pedido.pdf;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Trava divergencia entre os 3 arquivos de mensagens do PDF de status -
// uma chave nova adicionada so num idioma (esquecida nos outros dois)
// faria o MissingResourceException estourar em producao, so quando
// alguem pedisse o PDF naquele idioma especifico. Comparado aqui, uma
// vez, sem precisar gerar PDF nenhum.
class PdfStatusMessagesParityTest {

    private static final String BASE_NAME = "i18n.pdf-status-messages";

    @Test
    void asTresChavesDeMensagensSaoIdenticasEmPtEnEEs() {
        Set<String> chavesPt = chaves("pt");
        Set<String> chavesEn = chaves("en");
        Set<String> chavesEs = chaves("es");

        assertThat(chavesPt).isNotEmpty();
        assertThat(chavesEn).containsExactlyInAnyOrderElementsOf(chavesPt);
        assertThat(chavesEs).containsExactlyInAnyOrderElementsOf(chavesPt);
    }

    private Set<String> chaves(String idioma) {
        return ResourceBundle.getBundle(BASE_NAME, Locale.of(idioma)).keySet();
    }
}
