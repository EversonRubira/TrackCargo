package com.eversonrubira.exporttracking.pedido;

import com.eversonrubira.exporttracking.pedido.exception.ChecklistDocumentoNaoEncontradoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoJaAceitoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoAceitoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoEnviadoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
        if (documento.getAceitoEm() == null) {
            throw new DocumentoNaoAceitoException(documento.getTipoDocumento());
        }
        documento.reabrir(motivo);

        Pedido pedido = documento.getPedido();
        ocorrenciaRepository.save(new PedidoOcorrencia(pedido, TipoOcorrencia.REABERTURA_DOCUMENTO, motivo));

        if (pedido.getEstado() == PedidoEstado.DOCUMENTACAO_ACEITA) {
            transicionarDireto(pedido, PedidoEstado.DOCUMENTACAO_ENVIADA);
        }
    }

    // Recusa: documento enviado mas nunca aceito volta a pendente
    // (enviadoEm nulo) - reusa as mesmas excecoes de enviar()/aceitar()
    // (nao enviado / ja aceito) em vez de criar uma nova, porque a
    // pre-condicao e identica (enviadoEm != null && aceitoEm == null).
    // Sem transicao de pedido.estado aqui (decisao registrada no SPEC.md):
    // um documento so pode estar "enviado, nao aceito" com o pedido ja
    // em DOCUMENTACAO_ACEITA (ou alem) em cenarios onde regredir o
    // estado nao faria sentido (documento adicional tardio, ou
    // reaberto/reenviado apos o embarque) - mesma logica de
    // "nao ha como desfazer um navio que ja saiu" ja aplicada a
    // reabrirAposAceite().
    @Transactional
    public void recusar(ChecklistDocumento documento, String motivo) {
        if (documento.getEnviadoEm() == null) {
            throw new DocumentoNaoEnviadoException(documento.getTipoDocumento());
        }
        if (documento.getAceitoEm() != null) {
            throw new DocumentoJaAceitoException(documento.getTipoDocumento());
        }
        LocalDateTime envioRecusado = documento.getEnviadoEm();
        String motivoTratado = motivo.trim();
        documento.recusar();

        ocorrenciaRepository.save(new PedidoOcorrencia(documento.getPedido(), TipoOcorrencia.RECUSA_DOCUMENTO,
                motivoTratado, documento.getTipoDocumento(), envioRecusado));
    }

    // Historico cronologico de recusas de um documento - sobrevive a
    // reenvios/aceites posteriores, ja que pedido_ocorrencia nunca e
    // sobrescrita. Consumido pelo PDF de status (tarefa futura), por
    // isso ainda sem endpoint REST proprio.
    public List<PedidoOcorrencia> buscarRecusas(String numeroPedido, TipoDocumento tipo) {
        return ocorrenciaRepository.findByPedido_NumeroPedidoAndTipoAndTipoDocumentoOrderByOcorridoEmAsc(
                numeroPedido, TipoOcorrencia.RECUSA_DOCUMENTO, tipo);
    }

    // Sobrecargas usadas pelo Controller - resolvem o documento a partir do
    // numero do pedido + tipo (sem precisar do PedidoRepository aqui, ja
    // que a busca navega "pedido.numeroPedido" pela propria associacao)
    // e delegam pro metodo que ja recebe a entidade carregada.
    @Transactional
    public void enviar(String numeroPedido, TipoDocumento tipo) {
        enviar(buscarDocumento(numeroPedido, tipo));
    }

    @Transactional
    public void aceitar(String numeroPedido, TipoDocumento tipo) {
        aceitar(buscarDocumento(numeroPedido, tipo));
    }

    @Transactional
    public void reabrirAposAceite(String numeroPedido, TipoDocumento tipo, String motivo) {
        reabrirAposAceite(buscarDocumento(numeroPedido, tipo), motivo);
    }

    @Transactional
    public void recusar(String numeroPedido, TipoDocumento tipo, String motivo) {
        recusar(buscarDocumento(numeroPedido, tipo), motivo);
    }

    private ChecklistDocumento buscarDocumento(String numeroPedido, TipoDocumento tipo) {
        return checklistRepository.findByPedido_NumeroPedidoAndTipoDocumento(numeroPedido, tipo)
                .orElseThrow(() -> new ChecklistDocumentoNaoEncontradoException(numeroPedido, tipo));
    }

    private void transicionarDireto(Pedido pedido, PedidoEstado novoEstado) {
        PedidoEstado anterior = pedido.getEstado();
        pedido.aplicarTransicao(novoEstado);
        transicaoRepository.save(new PedidoTransicao(pedido, anterior, novoEstado));
    }
}
