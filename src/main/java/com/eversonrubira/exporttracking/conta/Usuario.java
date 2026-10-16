package com.eversonrubira.exporttracking.conta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Um utilizador pertence a uma so empresa (decisao do degrau 1).
    // @ManyToOne carrega a empresa junto (EAGER por padrao no JPA): o
    // GET /auth/me precisa sempre do nome dela, e com open-in-view
    // desligado um LAZY rebentaria fora da transacao.
    @ManyToOne(optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 200)
    private String email;

    // Hash no formato do DelegatingPasswordEncoder ("{bcrypt}$2a$..."),
    // nunca a senha em texto. Sem getter publico de proposito: so o
    // UsuarioDetailsService (mesmo pacote) precisa de o ler.
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected Usuario() {
        // exigido pelo JPA
    }

    public Usuario(Empresa empresa, String nome, String email, String senhaHash) {
        this.empresa = Objects.requireNonNull(empresa, "empresa e obrigatoria");
        this.nome = Objects.requireNonNull(nome, "nome e obrigatorio");
        this.email = normalizarEmail(Objects.requireNonNull(email, "email e obrigatorio"));
        this.senhaHash = Objects.requireNonNull(senhaHash, "senhaHash e obrigatorio");
    }

    // Ponto unico da regra "email sem distinguir maiusculas": quem grava
    // (construtor) e quem procura (ContaService, UsuarioDetailsService)
    // passam por aqui, por isso "Ana@X.com" e "ana@x.com" sao a mesma
    // conta. Locale.ROOT evita surpresas de idioma (ex: o "I" turco).
    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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

    String getSenhaHash() { return senhaHash; }

    public UUID getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
