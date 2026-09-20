package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.ChecklistService;
import com.eversonrubira.exporttracking.pedido.TipoDocumento;
import com.eversonrubira.exporttracking.pedido.web.dto.ReabrirDocumentoRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.RecusarDocumentoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Controller separado do PedidoController: espelha a separacao ja
// existente no service (PedidoService x ChecklistService), cada um
// dono do ciclo de vida de uma entidade. As rotas ficam aninhadas sob
// /pedidos/{numeroPedido} por estrutura de URL, nao pra forcar as duas
// entidades no mesmo controller.
@RestController
@RequestMapping("/pedidos/{numeroPedido}/documentos/{tipo}")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @PatchMapping("/enviar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviar(@PathVariable String numeroPedido, @PathVariable TipoDocumento tipo) {
        checklistService.enviar(numeroPedido, tipo);
    }

    @PatchMapping("/aceitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aceitar(@PathVariable String numeroPedido, @PathVariable TipoDocumento tipo) {
        checklistService.aceitar(numeroPedido, tipo);
    }

    @PatchMapping("/reabrir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reabrir(@PathVariable String numeroPedido, @PathVariable TipoDocumento tipo,
                         @Valid @RequestBody ReabrirDocumentoRequest request) {
        checklistService.reabrirAposAceite(numeroPedido, tipo, request.motivo());
    }

    @PatchMapping("/recusar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recusar(@PathVariable String numeroPedido, @PathVariable TipoDocumento tipo,
                         @Valid @RequestBody RecusarDocumentoRequest request) {
        checklistService.recusar(numeroPedido, tipo, request.motivo());
    }
}
