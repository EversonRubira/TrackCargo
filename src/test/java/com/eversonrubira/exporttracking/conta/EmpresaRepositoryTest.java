package com.eversonrubira.exporttracking.conta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Postgres real: o id da empresa padrao aparece duas vezes (SQL da V7 e
// Empresa.ID_PADRAO). Este teste falha se alguem mudar um sem o outro.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmpresaRepositoryTest {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Test
    void migracaoCriouEmpresaPadraoComOIdDaConstante() {
        Optional<Empresa> padrao = empresaRepository.findById(Empresa.ID_PADRAO);

        assertThat(padrao).isPresent();
        assertThat(padrao.get().getNome()).isEqualTo("Empresa padrao");
    }
}
