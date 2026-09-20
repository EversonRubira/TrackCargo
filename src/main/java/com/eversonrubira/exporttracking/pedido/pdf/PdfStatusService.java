package com.eversonrubira.exporttracking.pedido.pdf;

import com.eversonrubira.exporttracking.pedido.ChecklistDocumento;
import com.eversonrubira.exporttracking.pedido.Pedido;
import com.eversonrubira.exporttracking.pedido.PedidoEstado;
import com.eversonrubira.exporttracking.pedido.PedidoOcorrencia;
import com.eversonrubira.exporttracking.pedido.PedidoTransicao;
import com.eversonrubira.exporttracking.pedido.TipoDocumento;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    public byte[] gerar(Pedido pedido, List<PedidoTransicao> historico, List<ChecklistDocumento> checklist,
                         Map<TipoDocumento, List<PedidoOcorrencia>> recusasPorDocumento) {
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

            documento.add(new Paragraph("Documentos", new Font(Font.HELVETICA, 12, Font.BOLD)));
            documento.add(new Paragraph(" "));
            documento.add(secaoDocumentos(checklist, recusasPorDocumento));
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

    // Ordem estavel do enum (nao a ordem de retorno de findByPedidoId(),
    // que nao tem ORDER BY) - garante DOCUMENTO_ADICIONAL sempre por
    // ultimo mesmo tendo sido criado depois dos 4 obrigatorios.
    private PdfPTable secaoDocumentos(List<ChecklistDocumento> checklist,
                                       Map<TipoDocumento, List<PedidoOcorrencia>> recusasPorDocumento) {
        PdfPTable tabela = new PdfPTable(4);
        tabela.setWidthPercentage(100);

        for (String cabecalho : List.of("Documento", "Status", "Último envio", "Aceito em")) {
            PdfPCell celula = new PdfPCell(new Paragraph(cabecalho, new Font(Font.HELVETICA, 9, Font.BOLD)));
            celula.setBackgroundColor(new Color(230, 230, 230));
            tabela.addCell(celula);
        }

        List<ChecklistDocumento> ordenado = checklist.stream()
                .sorted(Comparator.comparingInt(d -> d.getTipoDocumento().ordinal()))
                .toList();

        for (ChecklistDocumento doc : ordenado) {
            List<PedidoOcorrencia> recusas = recusasPorDocumento.getOrDefault(doc.getTipoDocumento(), List.of());

            tabela.addCell(celulaTexto(nomeDocumento(doc)));
            tabela.addCell(celulaTexto(status(doc, !recusas.isEmpty())));
            tabela.addCell(celulaTexto(formatarData(doc.getEnviadoEm())));
            tabela.addCell(celulaTexto(formatarData(doc.getAceitoEm())));

            if (!recusas.isEmpty()) {
                tabela.addCell(celulaRecusas(recusas));
            }
        }
        return tabela;
    }

    // Colspan total (nao uma coluna estreita) - motivo tem ate 500
    // caracteres, escrito pro cliente ler, precisa de espaco pra
    // quebrar linha direito. PdfPCell/Paragraph ja quebram automatico
    // dentro da largura da celula, sem fatiar a string na mao.
    private PdfPCell celulaRecusas(List<PedidoOcorrencia> recusas) {
        PdfPCell celula = new PdfPCell();
        celula.setColspan(4);
        Font fonteRecusa = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.DARK_GRAY);
        for (PedidoOcorrencia recusa : recusas) {
            celula.addElement(new Paragraph(
                    "Recusado em %s (envio de %s): %s".formatted(
                            recusa.getOcorridoEm().format(DATA_FORMATTER),
                            recusa.getEnvioRecusadoEm().format(DATA_FORMATTER),
                            recusa.getDescricao()),
                    fonteRecusa));
        }
        return celula;
    }

    private PdfPCell celulaTexto(String texto) {
        return new PdfPCell(new Paragraph(texto, new Font(Font.HELVETICA, 9)));
    }

    private String status(ChecklistDocumento doc, boolean temRecusa) {
        if (doc.getAceitoEm() != null) {
            return "Aceito";
        }
        if (doc.getEnviadoEm() != null) {
            return "Enviado";
        }
        if (temRecusa) {
            return "Recusado, aguardando reenvio";
        }
        return "Pendente";
    }

    private String nomeDocumento(ChecklistDocumento doc) {
        String rotulo = rotulo(doc.getTipoDocumento());
        if (doc.getTipoDocumento() == TipoDocumento.DOCUMENTO_ADICIONAL && doc.getDescricao() != null) {
            return rotulo + " - " + doc.getDescricao();
        }
        return rotulo;
    }

    // Switch expression sem default de proposito: TipoDocumento novo
    // sem rotulo aqui vira erro de compilacao, nao um documento sem
    // nome legivel silenciosamente no PDF do cliente.
    private String rotulo(TipoDocumento tipo) {
        return switch (tipo) {
            case INVOICE -> "Invoice";
            case PACKING_LIST -> "Packing list";
            case BL -> "BL";
            case CERTIFICADO_SANITARIO -> "Certificado sanitário";
            case DOCUMENTO_ADICIONAL -> "Documento adicional";
        };
    }

    private String formatarData(LocalDateTime data) {
        return data == null ? "-" : data.format(DATA_FORMATTER);
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
