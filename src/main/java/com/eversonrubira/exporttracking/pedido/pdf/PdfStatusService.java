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
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

// Servico puro: recebe Pedido + historico e devolve os bytes do PDF,
// sem depender de HttpServletResponse (isso fica so no Controller).
// E o mesmo metodo que a futura automacao de e-mail (backlog v2) vai
// chamar direto, sem passar por HTTP - o endpoint e so uma forma de
// expor esse servico pro navegador, nao o unico consumidor dele.
//
// i18n (pt/en/es): ResourceBundle em vez de MessageSource - o servico
// nao tem contexto Spring nenhum (nao injeta repositorio/service, so
// recebe dados prontos como parametro), MessageSource exigiria
// injetar um bean so pra isso. ResourceBundle.getBundle(Locale) e
// puro Java, testavel sem subir contexto nenhum - mesmo principio ja
// aplicado ao resto da classe. Arquivos em
// src/main/resources/i18n/pdf-status-messages_{pt,en,es}.properties.
@Service
public class PdfStatusService {

    private static final String BASE_NAME = "i18n.pdf-status-messages";
    private static final Set<String> IDIOMAS_SUPORTADOS = Set.of("pt", "en", "es");
    private static final String IDIOMA_PADRAO = "pt";

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

    public byte[] gerar(Pedido pedido, List<PedidoTransicao> historico, List<ChecklistDocumento> checklist,
                         Map<TipoDocumento, List<PedidoOcorrencia>> recusasPorDocumento, String idioma) {
        Locale locale = localeDoIdioma(idioma);
        ResourceBundle textos = ResourceBundle.getBundle(BASE_NAME, locale);
        // Padrao explicito por idioma (chave "formato.data" no proprio
        // bundle), nao ofLocalizedDateTime - esse metodo delega ao dado
        // de locale CLDR do JDK em execucao, que pode mudar de versao
        // pra versao (o formato exato de "en"/"es" nao e garantia
        // nossa). O Locale so entra pra escrever o nome do mes (MMM)
        // no idioma certo quando o padrao usa letras em vez de numero.
        DateTimeFormatter dataFormatter = DateTimeFormatter.ofPattern(textos.getString("formato.data"), locale);

        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            Document documento = new Document();
            PdfWriter.getInstance(documento, saida);
            documento.open();

            documento.add(new Paragraph(
                    MessageFormat.format(textos.getString("titulo.statusPedido"), pedido.getNumeroPedido()),
                    new Font(Font.HELVETICA, 18, Font.BOLD)));
            documento.add(new Paragraph(" "));

            documento.add(new Paragraph(textos.getString("rotulo.cliente") + " " + pedido.getCliente()));
            documento.add(new Paragraph(textos.getString("rotulo.consignee") + " " + pedido.getConsignee()));
            documento.add(new Paragraph(textos.getString("rotulo.produto") + " " + pedido.getProduto()));
            documento.add(new Paragraph(textos.getString("rotulo.incoterm") + " " + pedido.getIncoterm()));
            documento.add(new Paragraph(" "));

            documento.add(progressoEtapas(pedido.getEstado(), textos));
            documento.add(new Paragraph(" "));

            documento.add(new Paragraph(textos.getString("secao.documentos"), new Font(Font.HELVETICA, 12, Font.BOLD)));
            documento.add(new Paragraph(" "));
            documento.add(secaoDocumentos(checklist, recusasPorDocumento, textos, dataFormatter));
            documento.add(new Paragraph(" "));

            if (pedido.getEstado() == PedidoEstado.CANCELADO) {
                documento.add(new Paragraph(textos.getString("selo.pedidoCancelado"),
                        new Font(Font.HELVETICA, 14, Font.BOLD, Color.RED)));
            } else {
                ultimaTransicao(historico).ifPresent(transicao ->
                        adicionarSilenciosamente(documento, new Paragraph(MessageFormat.format(
                                textos.getString("rotulo.desde"), transicao.getOcorridoEm().format(dataFormatter)))));
            }

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "Falha ao gerar PDF de status do pedido " + pedido.getNumeroPedido(), e);
        }
    }

    // Normaliza pra um dos 3 idiomas suportados - nulo, vazio ou
    // qualquer outro valor cai no padrao (pt), nunca lanca excecao por
    // idioma desconhecido nem exige um bundle "raiz" separado so de
    // fallback (o controller nunca chama ResourceBundle.getBundle com
    // um idioma fora deste conjunto).
    private Locale localeDoIdioma(String idioma) {
        String normalizado = idioma == null ? IDIOMA_PADRAO : idioma.toLowerCase(Locale.ROOT);
        return Locale.of(IDIOMAS_SUPORTADOS.contains(normalizado) ? normalizado : IDIOMA_PADRAO);
    }

    private PdfPTable progressoEtapas(PedidoEstado estadoAtual, ResourceBundle textos) {
        PdfPTable tabela = new PdfPTable(ETAPAS_CICLO_DE_VIDA.size());
        int indiceAtual = ETAPAS_CICLO_DE_VIDA.indexOf(estadoAtual);
        for (int i = 0; i < ETAPAS_CICLO_DE_VIDA.size(); i++) {
            PdfPCell celula = new PdfPCell(new Paragraph(
                    textos.getString(chaveEstado(ETAPAS_CICLO_DE_VIDA.get(i))), new Font(Font.HELVETICA, 6)));
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
                                       Map<TipoDocumento, List<PedidoOcorrencia>> recusasPorDocumento,
                                       ResourceBundle textos, DateTimeFormatter dataFormatter) {
        PdfPTable tabela = new PdfPTable(4);
        tabela.setWidthPercentage(100);

        for (String cabecalho : List.of(textos.getString("tabela.documento"), textos.getString("tabela.status"),
                textos.getString("tabela.ultimoEnvio"), textos.getString("tabela.aceitoEm"))) {
            PdfPCell celula = new PdfPCell(new Paragraph(cabecalho, new Font(Font.HELVETICA, 9, Font.BOLD)));
            celula.setBackgroundColor(new Color(230, 230, 230));
            tabela.addCell(celula);
        }

        List<ChecklistDocumento> ordenado = checklist.stream()
                .sorted(Comparator.comparingInt(d -> d.getTipoDocumento().ordinal()))
                .toList();

        for (ChecklistDocumento doc : ordenado) {
            List<PedidoOcorrencia> recusas = recusasPorDocumento.getOrDefault(doc.getTipoDocumento(), List.of());

            tabela.addCell(celulaTexto(nomeDocumento(doc, textos)));
            tabela.addCell(celulaTexto(status(doc, !recusas.isEmpty(), textos)));
            tabela.addCell(celulaTexto(formatarData(doc.getEnviadoEm(), textos, dataFormatter)));
            tabela.addCell(celulaTexto(formatarData(doc.getAceitoEm(), textos, dataFormatter)));

            if (!recusas.isEmpty()) {
                tabela.addCell(celulaRecusas(recusas, textos, dataFormatter));
            }
        }
        return tabela;
    }

    // Colspan total (nao uma coluna estreita) - motivo tem ate 500
    // caracteres, escrito pro cliente ler, precisa de espaco pra
    // quebrar linha direito. PdfPCell/Paragraph ja quebram automatico
    // dentro da largura da celula, sem fatiar a string na mao.
    private PdfPCell celulaRecusas(List<PedidoOcorrencia> recusas, ResourceBundle textos,
                                    DateTimeFormatter dataFormatter) {
        PdfPCell celula = new PdfPCell();
        celula.setColspan(4);
        Font fonteRecusa = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.DARK_GRAY);
        for (PedidoOcorrencia recusa : recusas) {
            celula.addElement(new Paragraph(
                    MessageFormat.format(textos.getString("recusa.template"),
                            recusa.getOcorridoEm().format(dataFormatter),
                            recusa.getEnvioRecusadoEm().format(dataFormatter),
                            recusa.getDescricao()),
                    fonteRecusa));
        }
        return celula;
    }

    private PdfPCell celulaTexto(String texto) {
        return new PdfPCell(new Paragraph(texto, new Font(Font.HELVETICA, 9)));
    }

    private String status(ChecklistDocumento doc, boolean temRecusa, ResourceBundle textos) {
        if (doc.getAceitoEm() != null) {
            return textos.getString("status.aceito");
        }
        if (doc.getEnviadoEm() != null) {
            return textos.getString("status.enviado");
        }
        if (temRecusa) {
            return textos.getString("status.recusado");
        }
        return textos.getString("status.pendente");
    }

    private String nomeDocumento(ChecklistDocumento doc, ResourceBundle textos) {
        String rotulo = textos.getString(chaveDocumento(doc.getTipoDocumento()));
        if (doc.getTipoDocumento() == TipoDocumento.DOCUMENTO_ADICIONAL && doc.getDescricao() != null) {
            return rotulo + " - " + doc.getDescricao();
        }
        return rotulo;
    }

    // Switch expression sem default de proposito: TipoDocumento novo
    // sem chave aqui vira erro de compilacao, nao um documento sem
    // nome legivel silenciosamente no PDF do cliente.
    private String chaveDocumento(TipoDocumento tipo) {
        return switch (tipo) {
            case INVOICE -> "documento.invoice";
            case PACKING_LIST -> "documento.packingList";
            case BL -> "documento.bl";
            case CERTIFICADO_SANITARIO -> "documento.certificadoSanitario";
            case DOCUMENTO_ADICIONAL -> "documento.adicional";
        };
    }

    // Mesmo raciocinio do chaveDocumento(): switch sem default, estado
    // novo sem rotulo legivel vira erro de compilacao. Resolve o gap
    // de PedidoEstado.name() cru que a barra de progresso tinha antes
    // desta feature (nunca teve rotulo legivel, nem so em PT).
    private String chaveEstado(PedidoEstado estado) {
        return switch (estado) {
            case CRIADO -> "estado.criado";
            case DOCUMENTACAO_ENVIADA -> "estado.documentacaoEnviada";
            case DOCUMENTACAO_ACEITA -> "estado.documentacaoAceita";
            case PAGAMENTO_PARCIAL_RECEBIDO -> "estado.pagamentoParcialRecebido";
            case EMBARCADO -> "estado.embarcado";
            case PAGAMENTO_SALDO_RECEBIDO -> "estado.pagamentoSaldoRecebido";
            case DOCUMENTOS_ORIGINAIS_ENVIADOS -> "estado.documentosOriginaisEnviados";
            case ENTREGUE -> "estado.entregue";
            case CANCELADO -> "estado.cancelado";
        };
    }

    private String formatarData(LocalDateTime data, ResourceBundle textos, DateTimeFormatter dataFormatter) {
        return data == null ? textos.getString("placeholder.semData") : data.format(dataFormatter);
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
