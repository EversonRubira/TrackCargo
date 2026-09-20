package com.eversonrubira.exporttracking.pedido;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Year;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Integracao contra Postgres real (sem mock) - o ponto central desta
// classe (o CAS atomico em incrementarSeCorresponder) so prova algo
// de verdade contra o banco real, nao contra um repository mockado.
// Anos 1901-1903 sao sentinelas isolados de qualquer ano real que
// outro teste/execucao possa usar.
@SpringBootTest
class PedidoSequenciaServiceTest {

    private static final int ANO_ATUAL = Year.now().getValue();
    private static final List<Integer> ANOS_DE_TESTE = List.of(ANO_ATUAL, 1901, 1902, 1903);

    @Autowired
    private PedidoSequenciaService pedidoSequenciaService;

    @Autowired
    private PedidoSequenciaRepository pedidoSequenciaRepository;

    @BeforeEach
    void limparSequenciasDeTeste() {
        ANOS_DE_TESTE.forEach(pedidoSequenciaRepository::deleteById);
    }

    @AfterEach
    void limparSequenciasDeTesteDepois() {
        ANOS_DE_TESTE.forEach(pedidoSequenciaRepository::deleteById);
    }

    @Test
    void sugerirProximoNumeroSemHistoricoRetorna00001ParaAnoAtual() {
        String sugestao = pedidoSequenciaService.sugerirProximoNumero();

        assertThat(sugestao).isEqualTo(String.format("00001/%d", ANO_ATUAL));
    }

    @Test
    void sugerirProximoNumeroApenasEspiaNaoCriaNemAlteraOContador() {
        pedidoSequenciaService.sugerirProximoNumero();
        pedidoSequenciaService.sugerirProximoNumero();

        assertThat(pedidoSequenciaRepository.findById(ANO_ATUAL)).isEmpty();
    }

    @Test
    void reservarSeCorresponderAvancaContadorQuandoNumeroBateComOSugerido() {
        String sugestao = pedidoSequenciaService.sugerirProximoNumero();

        pedidoSequenciaService.reservarSeCorresponder(sugestao);

        assertThat(pedidoSequenciaRepository.findById(ANO_ATUAL).map(PedidoSequencia::getProximoNumero))
                .contains(2);
        assertThat(pedidoSequenciaService.sugerirProximoNumero())
                .isEqualTo(String.format("00002/%d", ANO_ATUAL));
    }

    @Test
    void reservarSeCorresponderComNumeroManualForaDoPadraoNaoCriaSequencia() {
        pedidoSequenciaService.reservarSeCorresponder("PO-MANUAL-123");

        assertThat(pedidoSequenciaRepository.findById(ANO_ATUAL)).isEmpty();
    }

    @Test
    void reservarSeCorresponderComNumeroDiferenteDoAtualNaoAvancaOContador() {
        // Sugestao ainda nao usada e 00001/ANO_ATUAL - "usar" 00002 direto
        // (usuario editou pra um numero que nao e o proximo de verdade) nao
        // pode mexer no contador, porque nao bate com o valor atual.
        pedidoSequenciaService.reservarSeCorresponder(String.format("00002/%d", ANO_ATUAL));

        assertThat(pedidoSequenciaRepository.findById(ANO_ATUAL).map(PedidoSequencia::getProximoNumero))
                .contains(1);
    }

    @Test
    void sequenciaResetaPorAnoDoisAnosAvancamIndependentemente() {
        pedidoSequenciaService.reservarSeCorresponder("00001/1901");
        pedidoSequenciaService.reservarSeCorresponder("00002/1901");
        pedidoSequenciaService.reservarSeCorresponder("00001/1902");

        assertThat(pedidoSequenciaRepository.findById(1901).map(PedidoSequencia::getProximoNumero)).contains(3);
        assertThat(pedidoSequenciaRepository.findById(1902).map(PedidoSequencia::getProximoNumero)).contains(2);
    }

    @Test
    void duasReservasSimultaneasParaOMesmoNumeroSoUmaAvancaOContador() throws InterruptedException {
        String numero = "00001/1903";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch partida = new CountDownLatch(1);

        Runnable tarefa = () -> {
            prontos.countDown();
            try {
                partida.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pedidoSequenciaService.reservarSeCorresponder(numero);
        };

        executor.submit(tarefa);
        executor.submit(tarefa);
        prontos.await();
        partida.countDown();
        executor.shutdown();

        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(pedidoSequenciaRepository.findById(1903).map(PedidoSequencia::getProximoNumero))
                .contains(2);
    }
}
