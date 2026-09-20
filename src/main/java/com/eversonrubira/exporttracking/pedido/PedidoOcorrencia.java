package com.eversonrubira.exporttracking.pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

// Registro generico de ocorrencias que geram atraso/custo extra e
// precisam ficar documentadas (motivo obrigatorio) - reabertura de
// documento ja aceito, alteracao de consignee pos-embarque, etc.
// Uma tabela so, em vez de um campo disperso por cenario, porque os
// dois casos concretos ja discutidos tem a mesma forma por baixo.
@Entity
@Table(name = "pedido_ocorrencia")
public class PedidoOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoOcorrencia tipo;

    @Column(nullable = false, length = 500)
    private String descricao;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm;

    // Os dois campos abaixo (V5) so sao preenchidos em RECUSA_DOCUMENTO -
    // nulos pros demais tipos, que nao se referem a um documento
    // especifico do checklist. E o que o PDF de status (tarefa futura,
    // separada) vai ler pra montar o historico de recusas por documento.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 40)
    private TipoDocumento tipoDocumento;

    @Column(name = "envio_recusado_em")
    private LocalDateTime envioRecusadoEm;

    protected PedidoOcorrencia() {
    }

    public PedidoOcorrencia(Pedido pedido, TipoOcorrencia tipo, String descricao) {
        this.pedido = pedido;
        this.tipo = tipo;
        this.descricao = descricao;
        this.ocorridoEm = LocalDateTime.now();
    }

    public PedidoOcorrencia(Pedido pedido, TipoOcorrencia tipo, String descricao,
                             TipoDocumento tipoDocumento, LocalDateTime envioRecusadoEm) {
        this(pedido, tipo, descricao);
        this.tipoDocumento = tipoDocumento;
        this.envioRecusadoEm = envioRecusadoEm;
    }

    public UUID getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public TipoOcorrencia getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public LocalDateTime getOcorridoEm() { return ocorridoEm; }
    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public LocalDateTime getEnvioRecusadoEm() { return envioRecusadoEm; }
}
