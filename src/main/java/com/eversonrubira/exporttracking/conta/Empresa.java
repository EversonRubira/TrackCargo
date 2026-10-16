package com.eversonrubira.exporttracking.conta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "empresa")
public class Empresa {

    // Empresa criada pela migracao V7 para os pedidos anteriores ao
    // degrau 1. Unico sitio do Java com este id (o SQL da V7 repete-o,
    // e o EmpresaRepositoryTest confirma que os dois batem).
    public static final UUID ID_PADRAO = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected Empresa() {
        // exigido pelo JPA
    }

    // Construtor em vez de Builder (como PedidoOcorrencia): um campo so
    // nao justifica a classe Builder que o Pedido usa pelos seus 14.
    public Empresa(String nome) {
        this.nome = Objects.requireNonNull(nome, "nome e obrigatorio");
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
    public String getNome() { return nome; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
