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

@Entity
@Table(name = "pedido_transicao")
public class PedidoTransicao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 40)
    private PedidoEstado estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_novo", nullable = false, length = 40)
    private PedidoEstado estadoNovo;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm;

    protected PedidoTransicao() {
    }

    public PedidoTransicao(Pedido pedido, PedidoEstado estadoAnterior, PedidoEstado estadoNovo) {
        this.pedido = pedido;
        this.estadoAnterior = estadoAnterior;
        this.estadoNovo = estadoNovo;
        this.ocorridoEm = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public PedidoEstado getEstadoAnterior() { return estadoAnterior; }
    public PedidoEstado getEstadoNovo() { return estadoNovo; }
    public LocalDateTime getOcorridoEm() { return ocorridoEm; }
}
