package com.eversonrubira.exporttracking.pedido;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Numero de pedido sugerido, padrao NNNNN/AAAA (5 digitos com zero a
// esquerda, ano reseta a sequencia). GET /pedidos/proximo-numero so
// espia o contador (sugerirProximoNumero) - nunca reserva nem
// incrementa. O incremento de verdade so acontece dentro de
// PedidoService.criar(), via reservarSeCorresponder(), e so avanca
// quando o numeroPedido criado bate exatamente com o proximo_numero
// atual daquele ano: se o usuario editar a sugestao pra outro valor
// (ou pra um numero manual fora do padrao), o contador nao se move -
// evita numero pulado quando o formulario e aberto e abandonado.
@Service
public class PedidoSequenciaService {

    private static final Pattern PADRAO_NUMERO_GERADO = Pattern.compile("^(\\d{5})/(\\d{4})$");

    private final PedidoSequenciaRepository repository;

    public PedidoSequenciaService(PedidoSequenciaRepository repository) {
        this.repository = repository;
    }

    public String sugerirProximoNumero() {
        int ano = anoAtual();
        int proximoNumero = repository.buscarProximoNumero(ano).orElse(1);
        return formatar(proximoNumero, ano);
    }

    @Transactional
    public void reservarSeCorresponder(String numeroPedido) {
        Matcher matcher = PADRAO_NUMERO_GERADO.matcher(numeroPedido);
        if (!matcher.matches()) {
            return;
        }
        int numero = Integer.parseInt(matcher.group(1));
        int ano = Integer.parseInt(matcher.group(2));

        repository.garantirAno(ano);
        repository.incrementarSeCorresponder(ano, numero);
    }

    private int anoAtual() {
        return Year.now().getValue();
    }

    private String formatar(int numero, int ano) {
        return String.format("%05d/%d", numero, ano);
    }
}
