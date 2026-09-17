package com.eversonrubira.exporttracking.pedido;

import com.eversonrubira.exporttracking.pedido.exception.DocumentoJaAceitoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoEnviadoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistService {

    private final ChecklistDocumentoRepository checklistRepository;
    private final PedidoTransicaoRepository transicaoRepository;
    private final PedidoOcorrenciaRepository ocorrenciaRepository;

    public ChecklistService(ChecklistDocumentoRepository checklistRepository,
                             PedidoTransicaoRepository transicaoRepository,
                             PedidoOcorrenciaRepository ocorrenciaRepository) {
        this.checklistRepository = checklistRepository;
        this.transicaoRepository = transicaoRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
    }

    @Transactional
    public void enviar(ChecklistDocumento documento) {
        if (documento.getAceitoEm() != null) {
            throw new DocumentoJaAceitoException(documento.getTipoDocumento());
        }
        documento.marcarEnviado();

        Pedido pedido = documento.getPedido();
        if (pedido.getEstado() == PedidoEstado.CRIADO) {
            transicionarDireto(pedido, PedidoEstado.DOCUMENTACAO_ENVIADA);
        }
    }

    @Transactional
    public void aceitar(ChecklistDocumento documento) {
        if (documento.getEnviadoEm() == null) {
            throw new DocumentoNaoEnviadoException(documento.getTipoDocumento());
        }
        if (documento.getAceitoEm() != null) {
            throw new DocumentoJaAceitoException(documento.getTipoDocumento());
        }
        documento.marcarAceito();

        Pedido pedido = documento.getPedido();
        boolean todosAceitos = checklistRepository.findByPedidoId(pedido.getId()).stream()
                .allMatch(d -> d.getAceitoEm() != null);

        if (todosAceitos && pedido.getEstado() == PedidoEstado.DOCUMENTACAO_ENVIADA) {
            transicionarDireto(pedido, PedidoEstado.DOCUMENTACAO_ACEITA);
        }
    }

    @Transactional
    public void reabrirAposAceite(ChecklistDocumento documento, String motivo) {
        documento.reabrir(motivo);

        Pedido pedido = documento.getPedido();
        ocorrenciaRepository.save(new PedidoOcorrencia(pedido, TipoOcorrencia.REABERTURA_DOCUMENTO, motivo));

        if (pedido.getEstado() == PedidoEstado.DOCUMENTACAO_ACEITA) {
            transicionarDireto(pedido, PedidoEstado.DOCUMENTACAO_ENVIADA);
        }
    }

    private void transicionarDireto(Pedido pedido, PedidoEstado novoEstado) {
        PedidoEstado anterior = pedido.getEstado();
        pedido.aplicarTransicao(novoEstado);
        transicaoRepository.save(new PedidoTransicao(pedido, anterior, novoEstado));
    }
}
