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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "checklist_documento",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pedido_id", "tipo_documento"}))
public class ChecklistDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 40)
    private TipoDocumento tipoDocumento;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @Column(name = "aceito_em")
    private LocalDateTime aceitoEm;

    protected ChecklistDocumento() {
    }

    public ChecklistDocumento(Pedido pedido, TipoDocumento tipoDocumento) {
        this.pedido = pedido;
        this.tipoDocumento = tipoDocumento;
    }

    public UUID getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public LocalDateTime getEnviadoEm() { return enviadoEm; }
    public LocalDateTime getAceitoEm() { return aceitoEm; }
}
