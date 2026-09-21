package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.exception.DocumentoAdicionalJaExisteException;
import com.eversonrubira.exporttracking.pedido.web.dto.ErrorResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// DocumentoAdicionalJaExisteException nao e alcancavel via MockMvc hoje -
// adicionarDocumentoAdicional() nao tem endpoint REST proprio ainda (ver
// SPEC.md, "sem endpoint REST proprio ainda"). Testa o handler
// diretamente, sem inventar uma rota so pra cobrir isso via HTTP.
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void tratarDocumentoAdicionalJaExistePreencheParametrosComNumeroPedido() {
        ErrorResponse resposta = handler.tratarDocumentoAdicionalJaExiste(
                new DocumentoAdicionalJaExisteException("PO-0001"));

        assertThat(resposta.erro()).isEqualTo("DOCUMENTO_ADICIONAL_JA_EXISTE");
        assertThat(resposta.parametros()).containsEntry("numeroPedido", "PO-0001");
        assertThat(resposta.campos()).isNull();
    }
}
