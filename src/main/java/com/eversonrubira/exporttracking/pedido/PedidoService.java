package com.eversonrubira.exporttracking.pedido;

import com.eversonrubira.exporttracking.pedido.exception.DocumentoAdicionalJaExisteException;
import com.eversonrubira.exporttracking.pedido.exception.PedidoNaoEncontradoException;
import com.eversonrubira.exporttracking.pedido.exception.TransicaoInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PedidoService {

    private static final List<TipoDocumento> CHECKLIST_INICIAL = List.of(
            TipoDocumento.INVOICE,
            TipoDocumento.PACKING_LIST,
            TipoDocumento.BL,
            TipoDocumento.CERTIFICADO_SANITARIO
    );

    private final PedidoRepository pedidoRepository;
    private final ChecklistDocumentoRepository checklistRepository;
    private final PedidoTransicaoRepository transicaoRepository;
    private final PedidoOcorrenciaRepository ocorrenciaRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                          ChecklistDocumentoRepository checklistRepository,
                          PedidoTransicaoRepository transicaoRepository,
                          PedidoOcorrenciaRepository ocorrenciaRepository) {
        this.pedidoRepository = pedidoRepository;
        this.checklistRepository = checklistRepository;
        this.transicaoRepository = transicaoRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
    }

    @Transactional
    public Pedido criar(Pedido pedido) {
        Pedido salvo = pedidoRepository.save(pedido);
        for (TipoDocumento tipo : CHECKLIST_INICIAL) {
            checklistRepository.save(new ChecklistDocumento(salvo, tipo));
        }
        transicaoRepository.save(new PedidoTransicao(salvo, null, PedidoEstado.CRIADO));
        return salvo;
    }

    @Transactional
    public void adicionarDocumentoAdicional(String numeroPedido, String descricao) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        if (checklistRepository.existsByPedidoIdAndTipoDocumento(pedido.getId(), TipoDocumento.DOCUMENTO_ADICIONAL)) {
            throw new DocumentoAdicionalJaExisteException(numeroPedido);
        }
        checklistRepository.save(new ChecklistDocumento(pedido, TipoDocumento.DOCUMENTO_ADICIONAL, descricao));
    }

    @Transactional
    public Pedido transicionar(String numeroPedido, PedidoEstado novoEstado) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        transicionarValidando(pedido, novoEstado);
        return pedido;
    }

    @Transactional
    public Pedido confirmarPagamentoParcial(String numeroPedido) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        transicionarValidando(pedido, PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO);
        pedido.confirmarPagamentoParcial();
        return pedido;
    }

    @Transactional
    public Pedido confirmarPagamentoSaldo(String numeroPedido) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        transicionarValidando(pedido, PedidoEstado.PAGAMENTO_SALDO_RECEBIDO);
        pedido.confirmarPagamentoSaldo();
        return pedido;
    }

    private void transicionarValidando(Pedido pedido, PedidoEstado novoEstado) {
        if (!pedido.getEstado().podeTransicionarManualmentePara(novoEstado)) {
            throw new TransicaoInvalidaException(pedido.getEstado(), novoEstado);
        }
        PedidoEstado anterior = pedido.getEstado();
        pedido.aplicarTransicao(novoEstado);
        transicaoRepository.save(new PedidoTransicao(pedido, anterior, novoEstado));
    }

    @Transactional
    public Pedido alterarConsignee(String numeroPedido, String novoConsignee, String motivo) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        pedido.aplicarConsignee(novoConsignee);
        ocorrenciaRepository.save(new PedidoOcorrencia(pedido, TipoOcorrencia.ALTERACAO_DADOS_PEDIDO, motivo));
        return pedido;
    }

    public Pedido buscarPorNumero(String numeroPedido) {
        return pedidoRepository.findByNumeroPedido(numeroPedido)
                .orElseThrow(() -> new PedidoNaoEncontradoException(numeroPedido));
    }

    public List<ChecklistDocumento> buscarChecklist(String numeroPedido) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        return checklistRepository.findByPedidoId(pedido.getId());
    }

    public List<PedidoTransicao> buscarHistorico(String numeroPedido) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        return transicaoRepository.findByPedidoIdOrderByOcorridoEmAsc(pedido.getId());
    }

    public List<Pedido> listar(PedidoEstado estado) {
        return estado == null ? pedidoRepository.findAll() : pedidoRepository.findByEstado(estado);
    }

    @Transactional
    public Pedido atualizarDadosLogisticos(String numeroPedido, String ciaMaritima, String numeroContainer) {
        Pedido pedido = buscarPorNumero(numeroPedido);
        pedido.aplicarDadosLogisticos(ciaMaritima, numeroContainer);
        return pedido;
    }
}
