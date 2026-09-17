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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "numero_pedido", nullable = false, unique = true, length = 50)
    private String numeroPedido;

    @Column(nullable = false, length = 200)
    private String cliente;

    // Destinatario da carga no BL - pode ser diferente do cliente
    // (comprador). Ja visto mudar depois do embarque na pratica.
    @Column(nullable = false, length = 200)
    private String consignee;

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

    private Pedido(Builder b) {
        this.numeroPedido = Objects.requireNonNull(b.numeroPedido, "numeroPedido e obrigatorio");
        this.cliente = Objects.requireNonNull(b.cliente, "cliente e obrigatorio");
        this.consignee = Objects.requireNonNull(b.consignee, "consignee e obrigatorio");
        this.paisDestino = Objects.requireNonNull(b.paisDestino, "paisDestino e obrigatorio");
        this.portoOrigem = Objects.requireNonNull(b.portoOrigem, "portoOrigem e obrigatorio");
        this.portoDestino = Objects.requireNonNull(b.portoDestino, "portoDestino e obrigatorio");
        this.produto = Objects.requireNonNull(b.produto, "produto e obrigatorio");
        this.quantidade = Objects.requireNonNull(b.quantidade, "quantidade e obrigatoria");
        this.unidadeMedida = Objects.requireNonNull(b.unidadeMedida, "unidadeMedida e obrigatoria");
        this.precoAcordado = Objects.requireNonNull(b.precoAcordado, "precoAcordado e obrigatorio");
        this.moeda = (b.moeda != null) ? b.moeda : "USD";
        this.incoterm = Objects.requireNonNull(b.incoterm, "incoterm e obrigatorio");
        this.formaPagamento = Objects.requireNonNull(b.formaPagamento, "formaPagamento e obrigatorio");
        this.percentualParcial = Objects.requireNonNull(b.percentualParcial, "percentualParcial e obrigatorio");
        this.estado = PedidoEstado.CRIADO;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String numeroPedido;
        private String cliente;
        private String consignee;
        private String paisDestino;
        private String portoOrigem;
        private String portoDestino;
        private String produto;
        private BigDecimal quantidade;
        private String unidadeMedida;
        private BigDecimal precoAcordado;
        private String moeda;
        private Incoterm incoterm;
        private FormaPagamento formaPagamento;
        private BigDecimal percentualParcial;

        public Builder numeroPedido(String v) { this.numeroPedido = v; return this; }
        public Builder cliente(String v) { this.cliente = v; return this; }
        public Builder consignee(String v) { this.consignee = v; return this; }
        public Builder paisDestino(String v) { this.paisDestino = v; return this; }
        public Builder portoOrigem(String v) { this.portoOrigem = v; return this; }
        public Builder portoDestino(String v) { this.portoDestino = v; return this; }
        public Builder produto(String v) { this.produto = v; return this; }
        public Builder quantidade(BigDecimal v) { this.quantidade = v; return this; }
        public Builder unidadeMedida(String v) { this.unidadeMedida = v; return this; }
        public Builder precoAcordado(BigDecimal v) { this.precoAcordado = v; return this; }
        public Builder moeda(String v) { this.moeda = v; return this; }
        public Builder incoterm(Incoterm v) { this.incoterm = v; return this; }
        public Builder formaPagamento(FormaPagamento v) { this.formaPagamento = v; return this; }
        public Builder percentualParcial(BigDecimal v) { this.percentualParcial = v; return this; }

        public Pedido build() {
            return new Pedido(this);
        }
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

    // Pacote-privado de proposito: so PedidoService/ChecklistService
    // (mesmo pacote) podem mudar estado ou consignee - mutacao e regra
    // de negocio, nunca um setter publico solto.
    void aplicarTransicao(PedidoEstado novoEstado) {
        this.estado = novoEstado;
    }

    void aplicarConsignee(String novoConsignee) {
        this.consignee = novoConsignee;
    }

    void confirmarPagamentoParcial() {
        this.pagamentoParcialConfirmadoEm = LocalDateTime.now();
    }

    void confirmarPagamentoSaldo() {
        this.pagamentoSaldoConfirmadoEm = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getNumeroPedido() { return numeroPedido; }
    public String getCliente() { return cliente; }
    public String getConsignee() { return consignee; }
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
