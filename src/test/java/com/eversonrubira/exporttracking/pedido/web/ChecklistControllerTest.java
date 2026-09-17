package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.ChecklistService;
import com.eversonrubira.exporttracking.pedido.TipoDocumento;
import com.eversonrubira.exporttracking.pedido.exception.ChecklistDocumentoNaoEncontradoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoJaAceitoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoAceitoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoEnviadoException;
import com.eversonrubira.exporttracking.pedido.web.dto.ReabrirDocumentoRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChecklistController.class)
class ChecklistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChecklistService checklistService;

    @Test
    void enviarRetorna204() throws Exception {
        doNothing().when(checklistService).enviar("PO-0001", TipoDocumento.INVOICE);

        mockMvc.perform(patch("/pedidos/PO-0001/documentos/INVOICE/enviar"))
                .andExpect(status().isNoContent());
    }

    @Test
    void enviarDocumentoJaAceitoRetorna409() throws Exception {
        doThrow(new DocumentoJaAceitoException(TipoDocumento.INVOICE))
                .when(checklistService).enviar("PO-0001", TipoDocumento.INVOICE);

        mockMvc.perform(patch("/pedidos/PO-0001/documentos/INVOICE/enviar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("DOCUMENTO_JA_ACEITO"));
    }

    @Test
    void enviarDocumentoInexistenteRetorna404() throws Exception {
        doThrow(new ChecklistDocumentoNaoEncontradoException("PO-0001", TipoDocumento.DOCUMENTO_ADICIONAL))
                .when(checklistService).enviar("PO-0001", TipoDocumento.DOCUMENTO_ADICIONAL);

        mockMvc.perform(patch("/pedidos/PO-0001/documentos/DOCUMENTO_ADICIONAL/enviar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("CHECKLIST_DOCUMENTO_NAO_ENCONTRADO"));
    }

    @Test
    void aceitarRetorna204() throws Exception {
        doNothing().when(checklistService).aceitar("PO-0001", TipoDocumento.INVOICE);

        mockMvc.perform(patch("/pedidos/PO-0001/documentos/INVOICE/aceitar"))
                .andExpect(status().isNoContent());
    }

    @Test
    void aceitarDocumentoNaoEnviadoRetorna409() throws Exception {
        doThrow(new DocumentoNaoEnviadoException(TipoDocumento.INVOICE))
                .when(checklistService).aceitar("PO-0001", TipoDocumento.INVOICE);

        mockMvc.perform(patch("/pedidos/PO-0001/documentos/INVOICE/aceitar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("DOCUMENTO_NAO_ENVIADO"));
    }

    @Test
    void reabrirRetorna204() throws Exception {
        doNothing().when(checklistService).reabrirAposAceite("PO-0001", TipoDocumento.INVOICE, "Motivo valido");

        mockMvc.perform(patch("/pedidos/PO-0001/documentos/INVOICE/reabrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReabrirDocumentoRequest("Motivo valido"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void reabrirDocumentoNaoAceitoRetorna409() throws Exception {
        doThrow(new DocumentoNaoAceitoException(TipoDocumento.INVOICE))
                .when(checklistService).reabrirAposAceite("PO-0001", TipoDocumento.INVOICE, "Motivo valido");

        mockMvc.perform(patch("/pedidos/PO-0001/documentos/INVOICE/reabrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReabrirDocumentoRequest("Motivo valido"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("DOCUMENTO_NAO_ACEITO"));
    }

    @Test
    void reabrirSemMotivoRetorna400() throws Exception {
        mockMvc.perform(patch("/pedidos/PO-0001/documentos/INVOICE/reabrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReabrirDocumentoRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"));
    }

    @Test
    void tipoDocumentoInvalidoNaRotaRetorna400() throws Exception {
        mockMvc.perform(patch("/pedidos/PO-0001/documentos/NAO_EXISTE/enviar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("PARAMETRO_INVALIDO"));
    }
}
