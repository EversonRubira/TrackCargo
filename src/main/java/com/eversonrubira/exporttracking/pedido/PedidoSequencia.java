package com.eversonrubira.exporttracking.pedido;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedido_sequencia")
public class PedidoSequencia {

    @Id
    private Integer ano;

    private Integer proximoNumero;

    protected PedidoSequencia() {
        // exigido pelo JPA
    }

    public Integer getAno() { return ano; }
    public Integer getProximoNumero() { return proximoNumero; }
}
