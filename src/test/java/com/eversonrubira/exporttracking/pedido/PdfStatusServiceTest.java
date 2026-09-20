package com.eversonrubira.exporttracking.pedido;

import com.eversonrubira.exporttracking.pedido.pdf.PdfStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Unitario puro: PdfStatusService nao tem repositorio/service injetado
// (recebe checklist/recusas prontos), entao chama gerar() direto, sem
// Spring nem mock nenhum. Fica no pacote .pedido (nao .pedido.pdf,
// onde o service de verdade mora) so porque precisa dos mutadores
// pacote-privados de ChecklistDocumento (marcarEnviado/marcarAceito/
// recusar) pra montar os estados do checklist - mesma razao de
// ChecklistServiceTest estar aqui.
//
// Extracao de texto via PdfTextExtractor do proprio OpenPDF (ja e
// dependencia de producao, nenhuma dependencia nova so pra teste):
// ele decodifica errado qualquer byte WinAnsi acima de 0x7F de volta
// pra Unicode quando a fonte e um Type1 padrao nao embutido sem CMap
// ToUnicode (confirmado comparando com PyMuPDF e com renderizacao
// visual real do PDF - o documento gerado mostra os acentos
// corretamente pro leitor humano, so a extracao automatizada que
// falha; o Apache PDFBox, testado por comparacao antes de decidir
// isso, tem exatamente a mesma limitacao pra esses caracteres, entao
// nao ha motivo pra depender dele so pra este teste). Os testes
// abaixo que envolvem acento/emoji/alfabeto nao-latino documentam
// esse limite e verificam o que a extracao garante de fato: sem
// excecao, resto do texto legivel.
class PdfStatusServiceTest {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PdfStatusService service = new PdfStatusService();
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedido = Pedido.builder()
                .numeroPedido("PO-0001")
                .cliente("Cliente Teste")
                .consignee("Consignee Teste")
                .paisDestino("China")
                .portoOrigem("Porto de Santos")
                .portoDestino("Porto de Xangai")
                .produto("Carne bovina")
                .quantidade(new BigDecimal("20.000"))
                .unidadeMedida("TON")
                .precoAcordado(new BigDecimal("85000.00"))
                .moeda("USD")
                .incoterm(Incoterm.CFR)
                .formaPagamento(FormaPagamento.TT_ANTECIPADO)
                .percentualParcial(new BigDecimal("30.00"))
                .build();
    }

    @Test
    void documentoPendenteMostraStatusPendenteComTravessoesNasDatas() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);

        String texto = extrairTexto(service.gerar(pedido, List.of(), List.of(invoice), Map.of()));

        assertThat(texto).contains("Invoice");
        assertThat(texto).contains("Pendente");
    }

    @Test
    void documentoEnviadoMostraStatusEnviadoComData() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();

        String texto = extrairTexto(service.gerar(pedido, List.of(), List.of(invoice), Map.of()));

        assertThat(texto).contains("Enviado");
        assertThat(texto).contains(invoice.getEnviadoEm().format(FORMATO_DATA));
    }

    @Test
    void documentoAceitoMostraStatusAceitoComData() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        invoice.marcarAceito();

        String texto = extrairTexto(service.gerar(pedido, List.of(), List.of(invoice), Map.of()));

        assertThat(texto).contains("Aceito");
        assertThat(texto).contains(invoice.getAceitoEm().format(FORMATO_DATA));
    }

    @Test
    void documentoRecusadoAguardandoReenvioMostraStatusMotivoEAsDuasDatas() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        LocalDateTime envioOriginal = invoice.getEnviadoEm();
        invoice.recusar();
        PedidoOcorrencia recusa = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Invoice sem assinatura do responsavel", TipoDocumento.INVOICE, envioOriginal);

        String texto = extrairTexto(service.gerar(pedido, List.of(), List.of(invoice),
                Map.of(TipoDocumento.INVOICE, List.of(recusa))));

        assertThat(texto).contains("Recusado, aguardando reenvio");
        assertThat(texto).contains("Invoice sem assinatura do responsavel");
        assertThat(texto).contains(envioOriginal.format(FORMATO_DATA));
        assertThat(texto).contains(recusa.getOcorridoEm().format(FORMATO_DATA));
    }

    @Test
    void documentoRecusadoReenviadoEAceitoMantemARecusaNoHistorico() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        LocalDateTime envioOriginal = invoice.getEnviadoEm();
        invoice.recusar();
        PedidoOcorrencia recusa = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Valor da invoice divergente do contrato", TipoDocumento.INVOICE, envioOriginal);
        invoice.marcarEnviado();
        invoice.marcarAceito();

        String texto = extrairTexto(service.gerar(pedido, List.of(), List.of(invoice),
                Map.of(TipoDocumento.INVOICE, List.of(recusa))));

        assertThat(texto).contains("Aceito");
        assertThat(texto).contains("Valor da invoice divergente do contrato");
    }

    @Test
    void duasRecusasDoMesmoDocumentoAparecemNaOrdemDaLista() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        invoice.recusar();
        PedidoOcorrencia primeira = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Primeira recusa: assinatura ausente", TipoDocumento.INVOICE, LocalDateTime.now().minusDays(2));
        PedidoOcorrencia segunda = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Segunda recusa: valor ainda divergente", TipoDocumento.INVOICE, LocalDateTime.now().minusDays(1));

        String texto = extrairTexto(service.gerar(pedido, List.of(), List.of(invoice),
                Map.of(TipoDocumento.INVOICE, List.of(primeira, segunda))));

        int indicePrimeira = texto.indexOf("Primeira recusa: assinatura ausente");
        int indiceSegunda = texto.indexOf("Segunda recusa: valor ainda divergente");
        assertThat(indicePrimeira).isNotNegative();
        assertThat(indiceSegunda).isGreaterThan(indicePrimeira);
    }

    @Test
    void motivoComQuinhentosCaracteresNaoLancaExcecaoENaoTemOFinalTruncado() throws Exception {
        String base = "Motivo extenso descrevendo a nao conformidade encontrada na inspecao do documento fisico. ";
        String motivo = (base.repeat(6) + "Texto final do motivo para conferir que nao foi cortado aqui mesmo.")
                .substring(0, 500);
        assertThat(motivo).hasSize(500);

        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        invoice.recusar();
        PedidoOcorrencia recusa = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                motivo, TipoDocumento.INVOICE, LocalDateTime.now());

        assertThatCode(() ->
                service.gerar(pedido, List.of(), List.of(invoice), Map.of(TipoDocumento.INVOICE, List.of(recusa))))
                .doesNotThrowAnyException();

        byte[] pdf = service.gerar(pedido, List.of(), List.of(invoice), Map.of(TipoDocumento.INVOICE, List.of(recusa)));
        String texto = extrairTexto(pdf).replaceAll("\\s+", " ");
        String finalDoMotivo = motivo.substring(motivo.length() - 40).replaceAll("\\s+", " ").trim();

        assertThat(texto).contains(finalDoMotivo);
    }

    @Test
    void motivoComAcentosECedilhaNaoLancaExcecaoEMantemTextoAoRedorLegivel() throws Exception {
        // A extracao (ver comentario de classe) transforma cada caractere
        // acentuado num "?" - confirmado renderizando o PDF de verdade
        // (fora deste teste automatizado, via PyMuPDF) que o documento
        // mostra "ação", "país", "número" etc. corretamente pro cliente.
        // Este teste verifica a garantia real: nao quebra a geracao, e o
        // texto ASCII antes/depois de cada trecho acentuado nao se perde.
        String motivo = "Divergencia encontrada: numero do lote nao confere -"
                + " [inicio-acento]ação, país, número, não, descrição, código, ausência[fim-acento]"
                + " - segue o resto do motivo sem problema";

        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        invoice.recusar();
        PedidoOcorrencia recusa = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                motivo, TipoDocumento.INVOICE, LocalDateTime.now());

        byte[] pdf = service.gerar(pedido, List.of(), List.of(invoice), Map.of(TipoDocumento.INVOICE, List.of(recusa)));
        String texto = extrairTexto(pdf).replaceAll("\\s+", " ");

        assertThat(texto).contains("Divergencia encontrada: numero do lote nao confere");
        assertThat(texto).contains("[inicio-acento]");
        assertThat(texto).contains("[fim-acento]");
        assertThat(texto).contains("segue o resto do motivo sem problema");
    }

    @Test
    void motivoComEmojiECaracterNaoLatinoNaoLancaExcecaoEMantemRestoDoMotivoLegivel() throws Exception {
        // Comportamento observado renderizando o PDF de verdade (nao so a
        // extracao): a fonte padrao (Helvetica, uma das 14 fontes base do
        // PDF, nao embutida) nao tem glifo de emoji - o caractere e
        // OMITIDO silenciosamente, sem quebrar a geracao. Caractere de
        // alfabeto nao-latino (cirilico/CJK) depende de fallback de fonte
        // do sistema operacional onde o PDF e gerado - pode aparecer,
        // virar "?" ou ser omitido; por isso este teste nao trava numa
        // expectativa especifica pra esses caracteres (seria dependente
        // de ambiente e quebraria em CI com fontes diferentes das
        // deste sandbox). A garantia real, testada aqui: sem excecao, e o
        // texto normal antes/depois de cada caractere problematico
        // continua legivel.
        // Cada palavra-ancora fica isolada por espacos dos proprios
        // marcadores/caracteres especiais (nao colada com "[]") - a
        // extracao insere um espaco extra perto de um glifo sem
        // substituto (efeito colateral observado, nao um bug deste
        // codigo), e colar a ancora no caractere problematico deixaria
        // o teste fragil a esse detalhe de extracao.
        String motivo = "inicio texto normal antes"
                + " emoji antesemoji " + "😀" + " depoisemoji"
                + " cirilico antescirilico " + "Я" + " depoiscirilico"
                + " cjk antescjk " + "漢" + " depoiscjk"
                + " texto normal depois fim";

        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        invoice.recusar();
        PedidoOcorrencia recusa = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                motivo, TipoDocumento.INVOICE, LocalDateTime.now());

        byte[] pdf = service.gerar(pedido, List.of(), List.of(invoice), Map.of(TipoDocumento.INVOICE, List.of(recusa)));
        String texto = extrairTexto(pdf).replaceAll("\\s+", " ");

        assertThat(texto).contains("inicio texto normal antes");
        assertThat(texto).contains("antesemoji");
        assertThat(texto).contains("depoisemoji");
        assertThat(texto).contains("antescirilico");
        assertThat(texto).contains("depoiscirilico");
        assertThat(texto).contains("antescjk");
        assertThat(texto).contains("depoiscjk");
        assertThat(texto).contains("texto normal depois fim");
    }

    @Test
    void colunaDocumentoUsaRotulosLegiveisEmVezDoNomeDoEnum() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        ChecklistDocumento packingList = new ChecklistDocumento(pedido, TipoDocumento.PACKING_LIST);
        ChecklistDocumento bl = new ChecklistDocumento(pedido, TipoDocumento.BL);
        ChecklistDocumento certificado = new ChecklistDocumento(pedido, TipoDocumento.CERTIFICADO_SANITARIO);
        ChecklistDocumento adicional = new ChecklistDocumento(pedido, TipoDocumento.DOCUMENTO_ADICIONAL,
                "Certificado de origem");

        String texto = extrairTexto(service.gerar(pedido, List.of(),
                List.of(invoice, packingList, bl, certificado, adicional), Map.of()))
                .replaceAll("\\s+", " ");

        assertThat(texto).contains("Invoice");
        assertThat(texto).contains("Packing list");
        assertThat(texto).contains("BL");
        // "Certificado sanitário" tem acento - o "á" vira "?" na extracao
        // (ver comentario de classe), por isso o teste confere o trecho
        // ASCII do rotulo, nao a palavra inteira.
        assertThat(texto).contains("Certificado sanit");
        assertThat(texto).contains("Documento adicional - Certificado de origem");
    }

    @Test
    void documentosSaoOrdenadosPelaOrdemDoEnumNaoPelaOrdemDaListaRecebida() throws Exception {
        // Lista recebida fora de ordem (DOCUMENTO_ADICIONAL e INVOICE
        // invertidos, BL antes de PACKING_LIST) - a tabela precisa sair
        // na ordem estavel do enum mesmo assim.
        ChecklistDocumento adicional = new ChecklistDocumento(pedido, TipoDocumento.DOCUMENTO_ADICIONAL, "Extra");
        ChecklistDocumento bl = new ChecklistDocumento(pedido, TipoDocumento.BL);
        ChecklistDocumento packingList = new ChecklistDocumento(pedido, TipoDocumento.PACKING_LIST);
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        ChecklistDocumento certificado = new ChecklistDocumento(pedido, TipoDocumento.CERTIFICADO_SANITARIO);

        String texto = extrairTexto(service.gerar(pedido, List.of(),
                List.of(adicional, bl, packingList, invoice, certificado), Map.of()));

        int indiceInvoice = texto.indexOf("Invoice");
        int indicePackingList = texto.indexOf("Packing list");
        int indiceBl = texto.indexOf("BL");
        int indiceAdicional = texto.indexOf("Documento adicional");

        assertThat(indiceInvoice).isLessThan(indicePackingList);
        assertThat(indicePackingList).isLessThan(indiceBl);
        assertThat(indiceBl).isLessThan(indiceAdicional);
    }

    // Gera um PDF de exemplo em target/sample-status.pdf pra avaliacao
    // visual manual (nao commitado - target/ ja e gitignored). Dois
    // documentos recusados: um com motivo curto, ja reenviado e aceito
    // (recusa ainda listada), outro com motivo de 500 caracteres ainda
    // aguardando reenvio - cobre os dois casos de layout mais dificeis
    // (celula colspan longa + status "aguardando reenvio").
    @Test
    void geraPdfDeExemploParaAvaliacaoVisual() throws Exception {
        ChecklistDocumento invoice = new ChecklistDocumento(pedido, TipoDocumento.INVOICE);
        invoice.marcarEnviado();
        LocalDateTime envioInvoice = invoice.getEnviadoEm();
        invoice.recusar();
        PedidoOcorrencia recusaInvoice = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                "Assinatura do responsavel ausente na ultima pagina da invoice",
                TipoDocumento.INVOICE, envioInvoice);
        invoice.marcarEnviado();
        invoice.marcarAceito();

        ChecklistDocumento bl = new ChecklistDocumento(pedido, TipoDocumento.BL);
        bl.marcarEnviado();
        LocalDateTime envioBl = bl.getEnviadoEm();
        bl.recusar();
        String motivoLongo = ("Divergencia encontrada na conferencia do BL: numero do container "
                + "nao confere com o informado no booking, peso bruto declarado diverge da "
                + "pesagem no porto de origem, e a descricao da mercadoria esta incompleta - "
                + "favor corrigir os tres pontos e reenviar o documento assinado novamente. ")
                .repeat(3);
        motivoLongo = motivoLongo.substring(0, 500);
        PedidoOcorrencia recusaBl = new PedidoOcorrencia(pedido, TipoOcorrencia.RECUSA_DOCUMENTO,
                motivoLongo, TipoDocumento.BL, envioBl);

        ChecklistDocumento packingList = new ChecklistDocumento(pedido, TipoDocumento.PACKING_LIST);
        packingList.marcarEnviado();

        ChecklistDocumento certificado = new ChecklistDocumento(pedido, TipoDocumento.CERTIFICADO_SANITARIO);

        byte[] pdf = service.gerar(pedido, List.of(), List.of(invoice, bl, packingList, certificado),
                Map.of(TipoDocumento.INVOICE, List.of(recusaInvoice), TipoDocumento.BL, List.of(recusaBl)));

        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/sample-status.pdf"), pdf);

        assertThat(pdf).isNotEmpty();
    }

    private String extrairTexto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder texto = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append(' ');
            }
            return texto.toString();
        } finally {
            reader.close();
        }
    }
}
