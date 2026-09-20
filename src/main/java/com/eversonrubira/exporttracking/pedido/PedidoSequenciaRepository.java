package com.eversonrubira.exporttracking.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PedidoSequenciaRepository extends JpaRepository<PedidoSequencia, Integer> {

    @Query(value = "SELECT proximo_numero FROM pedido_sequencia WHERE ano = :ano", nativeQuery = true)
    Optional<Integer> buscarProximoNumero(@Param("ano") int ano);

    // Garante a linha do ano sem sobrescrever um contador ja existente -
    // idempotente, pode rodar concorrentemente sem duplicar a PK.
    @Modifying
    @Query(value = "INSERT INTO pedido_sequencia (ano, proximo_numero) VALUES (:ano, 1) "
            + "ON CONFLICT (ano) DO NOTHING", nativeQuery = true)
    void garantirAno(@Param("ano") int ano);

    // CAS atomico: so avanca o contador se o valor atual for exatamente o
    // numero que esta sendo reservado - e o que faz duas criacoes
    // concorrentes nao colidirem (a que perder a corrida simplesmente nao
    // avanca nada, sem lock explicito).
    @Modifying
    @Query(value = "UPDATE pedido_sequencia SET proximo_numero = proximo_numero + 1 "
            + "WHERE ano = :ano AND proximo_numero = :numero", nativeQuery = true)
    int incrementarSeCorresponder(@Param("ano") int ano, @Param("numero") int numero);
}
