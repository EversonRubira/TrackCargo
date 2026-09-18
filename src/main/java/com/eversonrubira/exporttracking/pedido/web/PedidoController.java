package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.ChecklistDocumento;
import com.eversonrubira.exporttracking.pedido.Pedido;
import com.eversonrubira.exporttracking.pedido.PedidoEstado;
import com.eversonrubira.exporttracking.pedido.PedidoService;
import com.eversonrubira.exporttracking.pedido.PedidoTransicao;
import com.eversonrubira.exporttracking.pedido.pdf.PdfStatusService;
import com.eversonrubira.exporttracking.pedido.web.dto.AtualizarLogisticaRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.CriarPedidoRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.PedidoResponse;
import com.eversonrubira.exporttracking.pedido.web.dto.PedidoTransicaoResponse;
import com.eversonrubira.exporttracking.pedido.web.dto.TransicionarRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final PdfStatusService pdfStatusService;

    public PedidoController(PedidoService pedidoService, PdfStatusService pdfStatusService) {
        this.pedidoService = pedidoService;
        this.pdfStatusService = pdfStatusService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse criar(@Valid @RequestBody CriarPedidoRequest request) {
        Pedido pedido = pedidoService.criar(request.paraPedido());
        return responder(pedido);
    }

    @GetMapping
    public List<PedidoResponse> listar(@RequestParam(required = false) PedidoEstado estado) {
        return pedidoService.listar(estado).stream()
                .map(this::responder)
                .toList();
    }

    @GetMapping("/{numeroPedido}")
    public PedidoResponse buscar(@PathVariable String numeroPedido) {
        return responder(pedidoService.buscarPorNumero(numeroPedido));
    }

    @PatchMapping("/{numeroPedido}/transicionar")
    public PedidoResponse transicionar(@PathVariable String numeroPedido,
                                        @Valid @RequestBody TransicionarRequest request) {
        Pedido pedido = pedidoService.transicionar(numeroPedido, request.novoEstado());
        return responder(pedido);
    }

    @PostMapping("/{numeroPedido}/pagamento-parcial")
    public PedidoResponse confirmarPagamentoParcial(@PathVariable String numeroPedido) {
        Pedido pedido = pedidoService.confirmarPagamentoParcial(numeroPedido);
        return responder(pedido);
    }

    @PostMapping("/{numeroPedido}/pagamento-saldo")
    public PedidoResponse confirmarPagamentoSaldo(@PathVariable String numeroPedido) {
        Pedido pedido = pedidoService.confirmarPagamentoSaldo(numeroPedido);
        return responder(pedido);
    }

    @GetMapping("/{numeroPedido}/historico")
    public List<PedidoTransicaoResponse> historico(@PathVariable String numeroPedido) {
        return pedidoService.buscarHistorico(numeroPedido).stream()
                .map(PedidoTransicaoResponse::de)
                .toList();
    }

    @PatchMapping("/{numeroPedido}/logistica")
    public PedidoResponse atualizarLogistica(@PathVariable String numeroPedido,
                                              @Valid @RequestBody AtualizarLogisticaRequest request) {
        Pedido pedido = pedidoService.atualizarDadosLogisticos(
                numeroPedido, request.ciaMaritima(), request.numeroContainer());
        return responder(pedido);
    }

    @GetMapping(value = "/{numeroPedido}/status.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> statusPdf(@PathVariable String numeroPedido) {
        Pedido pedido = pedidoService.buscarPorNumero(numeroPedido);
        List<PedidoTransicao> historico = pedidoService.buscarHistorico(numeroPedido);
        byte[] pdf = pdfStatusService.gerar(pedido, historico);
        return ResponseEntity.ok().body(pdf);
    }

    private PedidoResponse responder(Pedido pedido) {
        List<ChecklistDocumento> checklist = pedidoService.buscarChecklist(pedido.getNumeroPedido());
        return PedidoResponse.de(pedido, checklist);
    }
}
