package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.web.dto.AtualizarLogisticaRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.CondicoesComerciaisRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.CriarPedidoRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.ReabrirDocumentoRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.RecusarDocumentoRequest;
import com.eversonrubira.exporttracking.pedido.web.dto.TransicionarRequest;
import jakarta.validation.Constraint;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Trava o esquecimento pedido no Bloco 1: se algum DTO ganhar uma
// anotacao de Bean Validation nova (meta-anotada com @Constraint) sem
// uma entrada correspondente em ValidacaoCodigoMapper.CODIGO_POR_ANOTACAO,
// este teste falha - em vez de descobrir em producao que aquele campo
// caiu silenciosamente no codigo generico VALIDACAO_INVALIDA.
//
// Lista de DTOs mantida explicita (sem lib de classpath scanning no
// projeto so pra isso) - adicionar um DTO novo com validacao exige
// adicionar a classe aqui, mesma fricao aceitavel de manter a lista de
// endpoints/DTOs atualizada que o projeto ja tem em outros lugares.
class ValidacaoCodigoMapperTest {

    private static final List<Class<?>> DTOS_COM_VALIDACAO = List.of(
            CriarPedidoRequest.class,
            CondicoesComerciaisRequest.class,
            AtualizarLogisticaRequest.class,
            RecusarDocumentoRequest.class,
            ReabrirDocumentoRequest.class,
            TransicionarRequest.class
    );

    @Test
    void todaAnotacaoDeValidacaoUsadaNosDtosTemEntradaNoMapeador() {
        List<String> semEntrada = new ArrayList<>();

        for (Class<?> dto : DTOS_COM_VALIDACAO) {
            for (Field campo : dto.getDeclaredFields()) {
                for (Annotation anotacao : campo.getAnnotations()) {
                    if (!ehAnotacaoDeValidacao(anotacao)) {
                        continue;
                    }
                    String nome = anotacao.annotationType().getSimpleName();
                    if (!ValidacaoCodigoMapper.CODIGO_POR_ANOTACAO.containsKey(nome)) {
                        semEntrada.add(dto.getSimpleName() + "." + campo.getName() + " -> @" + nome);
                    }
                }
            }
        }

        assertThat(semEntrada)
                .as("Anotacoes de validacao sem entrada em ValidacaoCodigoMapper.CODIGO_POR_ANOTACAO")
                .isEmpty();
    }

    private boolean ehAnotacaoDeValidacao(Annotation anotacao) {
        return anotacao.annotationType().isAnnotationPresent(Constraint.class);
    }
}
