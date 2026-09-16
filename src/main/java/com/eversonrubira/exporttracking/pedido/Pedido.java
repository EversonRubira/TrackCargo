package com.eversonrubira.exporttracking.pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Chave de negocio: numero do PO, atribuido na abertura da
    // negociacao - antes de qualquer documento (a invoice, por
    // exemplo, e gerada depois, e vive so como item do checklist).
    @Column(name = "numero_pedido", nullable = false, unique = true, length = 50)
    private String numeroPedido;

    @Column(nullable = false, length = 200)
    private String cliente;

    @Column(name = "pais_destino", nullable = false, length = 100)
    private String paisDestino;

    @Column(name = "porto_origem", nullable = false, length = 100)
    private String portoOrigem;

    @Column(name = "porto_destino", nullable = false, length = 100)
    private String portoDestino;

    @Column(nullable = false, length = 100)
    private String produto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantidade;

    @Column(name = "unidade_medida", nullable = false, length = 10)
    private String unidadeMedida;

    @Column(name = "cia_maritima", length = 100)
    private String ciaMaritima;

    @Column(name = "numero_container", length = 30)
    private String numeroContainer;

    @Column(name = "preco_acordado", nullable = false, precision = 14, scale = 2)
    private BigDecimal precoAcordado;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Incoterm incoterm;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 30)
    private FormaPagamento formaPagamento;

    // Ex: 30.00 = 30% antecipado / 70% contra embarque. O saldo
    // (100 - percentualParcial) e calculado, nunca persistido, pra
    // nao correr risco dos dois ficarem incoerentes entre si.
    @Column(name = "percentual_parcial", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualParcial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PedidoEstado estado;

    @Column(name = "pagamento_parcial_confirmado_em")
    private LocalDateTime pagamentoParcialConfirmadoEm;

    @Column(name = "pagamento_saldo_confirmado_em")
    private LocalDateTime pagamentoSaldoConfirmadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected Pedido() {
        // exigido pelo JPA
    }

    public Pedido(String numeroPedido, String cliente, String paisDestino,
                  String portoOrigem, String portoDestino, String produto,
                  BigDecimal quantidade, String unidadeMedida,
                  BigDecimal precoAcordado, String moeda,
                  Incoterm incoterm, FormaPagamento formaPagamento,
                  BigDecimal percentualParcial) {
        this.numeroPedido = numeroPedido;
        this.cliente = cliente;
        this.paisDestino = paisDestino;
        this.portoOrigem = portoOrigem;
        this.portoDestino = portoDestino;
        this.produto = produto;
        this.quantidade = quantidade;
        this.unidadeMedida = unidadeMedida;
        this.precoAcordado = precoAcordado;
        this.moeda = (moeda != null) ? moeda : "USD";
        this.incoterm = incoterm;
        this.formaPagamento = formaPagamento;
        this.percentualParcial = percentualParcial;
        this.estado = PedidoEstado.CRIADO;
    }

    @PrePersist
    void aoPersistir() {
        LocalDateTime agora = LocalDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getNumeroPedido() { return numeroPedido; }
    public String getCliente() { return cliente; }
    public String getPaisDestino() { return paisDestino; }
    public String getPortoOrigem() { return portoOrigem; }
    public String getPortoDestino() { return portoDestino; }
    public String getProduto() { return produto; }
    public BigDecimal getQuantidade() { return quantidade; }
    public String getUnidadeMedida() { return unidadeMedida; }
    public String getCiaMaritima() { return ciaMaritima; }
    public void setCiaMaritima(String ciaMaritima) { this.ciaMaritima = ciaMaritima; }
    public String getNumeroContainer() { return numeroContainer; }
    public void setNumeroContainer(String numeroContainer) { this.numeroContainer = numeroContainer; }
    public BigDecimal getPrecoAcordado() { return precoAcordado; }
    public String getMoeda() { return moeda; }
    public Incoterm getIncoterm() { return incoterm; }
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public BigDecimal getPercentualParcial() { return percentualParcial; }
    public PedidoEstado getEstado() { return estado; }
    public LocalDateTime getPagamentoParcialConfirmadoEm() { return pagamentoParcialConfirmadoEm; }
    public LocalDateTime getPagamentoSaldoConfirmadoEm() { return pagamentoSaldoConfirmadoEm; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
