package com.eversonrubira.exporttracking.pedido.pdf;

import com.eversonrubira.exporttracking.pedido.Pedido;
import com.eversonrubira.exporttracking.pedido.PedidoEstado;
import com.eversonrubira.exporttracking.pedido.PedidoTransicao;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// Servico puro: recebe Pedido + historico e devolve os bytes do PDF,
// sem depender de HttpServletResponse (isso fica so no Controller).
// E o mesmo metodo que a futura automacao de e-mail (backlog v2) vai
// chamar direto, sem passar por HTTP - o endpoint e so uma forma de
// expor esse servico pro navegador, nao o unico consumidor dele.
@Service
public class PdfStatusService {

    private static final List<PedidoEstado> ETAPAS_CICLO_DE_VIDA = List.of(
            PedidoEstado.CRIADO,
            PedidoEstado.DOCUMENTACAO_ENVIADA,
            PedidoEstado.DOCUMENTACAO_ACEITA,
            PedidoEstado.PAGAMENTO_PARCIAL_RECEBIDO,
            PedidoEstado.EMBARCADO,
            PedidoEstado.PAGAMENTO_SALDO_RECEBIDO,
            PedidoEstado.DOCUMENTOS_ORIGINAIS_ENVIADOS,
            PedidoEstado.ENTREGUE
    );

    private static final DateTimeFormatter DATA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] gerar(Pedido pedido, List<PedidoTransicao> historico) {
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            Document documento = new Document();
            PdfWriter.getInstance(documento, saida);
            documento.open();

            documento.add(new Paragraph("Status do pedido " + pedido.getNumeroPedido(),
                    new Font(Font.HELVETICA, 18, Font.BOLD)));
            documento.add(new Paragraph(" "));

            documento.add(new Paragraph("Cliente: " + pedido.getCliente()));
            documento.add(new Paragraph("Consignee: " + pedido.getConsignee()));
            documento.add(new Paragraph("Produto: " + pedido.getProduto()));
            documento.add(new Paragraph("Incoterm: " + pedido.getIncoterm()));
            documento.add(new Paragraph(" "));

            documento.add(progressoEtapas(pedido.getEstado()));
            documento.add(new Paragraph(" "));

            if (pedido.getEstado() == PedidoEstado.CANCELADO) {
                documento.add(new Paragraph("PEDIDO CANCELADO",
                        new Font(Font.HELVETICA, 14, Font.BOLD, Color.RED)));
            } else {
                ultimaTransicao(historico).ifPresent(transicao ->
                        adicionarSilenciosamente(documento,
                                new Paragraph("Desde: " + transicao.getOcorridoEm().format(DATA_FORMATTER))));
            }

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "Falha ao gerar PDF de status do pedido " + pedido.getNumeroPedido(), e);
        }
    }

    private PdfPTable progressoEtapas(PedidoEstado estadoAtual) {
        PdfPTable tabela = new PdfPTable(ETAPAS_CICLO_DE_VIDA.size());
        int indiceAtual = ETAPAS_CICLO_DE_VIDA.indexOf(estadoAtual);
        for (int i = 0; i < ETAPAS_CICLO_DE_VIDA.size(); i++) {
            PdfPCell celula = new PdfPCell(new Paragraph(
                    ETAPAS_CICLO_DE_VIDA.get(i).name(), new Font(Font.HELVETICA, 6)));
            celula.setHorizontalAlignment(Element.ALIGN_CENTER);
            if (i <= indiceAtual) {
                celula.setBackgroundColor(new Color(200, 230, 200));
            }
            tabela.addCell(celula);
        }
        return tabela;
    }

    private Optional<PedidoTransicao> ultimaTransicao(List<PedidoTransicao> historico) {
        return historico.isEmpty() ? Optional.empty() : Optional.of(historico.get(historico.size() - 1));
    }

    private void adicionarSilenciosamente(Document documento, Paragraph paragrafo) {
        try {
            documento.add(paragrafo);
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao adicionar conteudo ao PDF de status", e);
        }
    }
}
