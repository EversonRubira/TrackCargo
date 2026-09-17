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

    @Column(length = 200)
    private String descricao;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    @Column(name = "aceito_em")
    private LocalDateTime aceitoEm;

    @Column(name = "reaberto_em")
    private LocalDateTime reabertoEm;

    @Column(name = "motivo_reabertura", length = 500)
    private String motivoReabertura;

    protected ChecklistDocumento() {
    }

    public ChecklistDocumento(Pedido pedido, TipoDocumento tipoDocumento) {
        this(pedido, tipoDocumento, null);
    }

    public ChecklistDocumento(Pedido pedido, TipoDocumento tipoDocumento, String descricao) {
        this.pedido = pedido;
        this.tipoDocumento = tipoDocumento;
        this.descricao = descricao;
    }

    // Pacote-privado - so ChecklistService muda o ciclo de vida do documento.
    void marcarEnviado() {
        this.enviadoEm = LocalDateTime.now();
    }

    void marcarAceito() {
        this.aceitoEm = LocalDateTime.now();
    }

    void reabrir(String motivo) {
        this.reabertoEm = LocalDateTime.now();
        this.motivoReabertura = motivo;
        this.aceitoEm = null;
    }

    public UUID getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public String getDescricao() { return descricao; }
    public LocalDateTime getEnviadoEm() { return enviadoEm; }
    public LocalDateTime getAceitoEm() { return aceitoEm; }
    public LocalDateTime getReabertoEm() { return reabertoEm; }
    public String getMotivoReabertura() { return motivoReabertura; }
}
