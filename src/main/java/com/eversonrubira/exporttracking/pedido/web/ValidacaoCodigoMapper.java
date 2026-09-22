package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.web.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Ponto unico de traducao anotacao Bean Validation -> codigo de erro
// estavel. Sem if/else por anotacao: CODIGO_POR_ANOTACAO e so dado (uma
// entrada por anotacao usada nos DTOs, com a lista de atributos que
// viram "parametros" no ErrorResponse), a extracao dos valores e generica
// via ConstraintDescriptor.getAttributes(). Anotacao nova sem entrada
// aqui nao quebra a API - cai em VALIDACAO_INVALIDA (o mesmo codigo geral
// que ja existia antes deste contrato) com um log de aviso, pra alguem
// notar e completar o mapa, sem exigir isso hoje (ValidacaoCodigoMapperTest
// varre as anotacoes dos DTOs por reflexao e falha se uma ficar de fora).
class ValidacaoCodigoMapper {

    private static final Logger LOG = LoggerFactory.getLogger(ValidacaoCodigoMapper.class);

    private static final String CODIGO_FALLBACK = "VALIDACAO_INVALIDA";

    record CodigoInfo(String codigo, List<String> atributosParametros) {
    }

    static final Map<String, CodigoInfo> CODIGO_POR_ANOTACAO = Map.of(
            "NotNull", new CodigoInfo("OBRIGATORIO", List.of()),
            "NotBlank", new CodigoInfo("OBRIGATORIO", List.of()),
            "Positive", new CodigoInfo("POSITIVO", List.of()),
            "FaixaDecimal", new CodigoInfo("FORA_DA_FAIXA", List.of("min", "max")),
            "Size", new CodigoInfo("TAMANHO_MAXIMO", List.of("max")),
            "NumeroContainerIso6346", new CodigoInfo("CONTAINER_ISO6346_INVALIDO", List.of())
    );

    private ValidacaoCodigoMapper() {
    }

    static List<ErrorResponse.CampoErro> paraCampos(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .sorted((a, b) -> a.getField().compareTo(b.getField()))
                .map(ValidacaoCodigoMapper::paraCampoErro)
                .toList();
    }

    private static ErrorResponse.CampoErro paraCampoErro(FieldError erro) {
        ConstraintViolation<?> violacao = erro.unwrap(ConstraintViolation.class);
        String anotacao = violacao.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();

        CodigoInfo info = CODIGO_POR_ANOTACAO.get(anotacao);
        if (info == null) {
            LOG.warn("Anotacao de validacao '{}' sem entrada em ValidacaoCodigoMapper.CODIGO_POR_ANOTACAO "
                    + "- usando codigo generico '{}' para o campo '{}'", anotacao, CODIGO_FALLBACK, erro.getField());
            return new ErrorResponse.CampoErro(erro.getField(), CODIGO_FALLBACK, Map.of());
        }

        Map<String, Object> atributos = violacao.getConstraintDescriptor().getAttributes();
        Map<String, Object> parametros = new LinkedHashMap<>();
        for (String chave : info.atributosParametros()) {
            parametros.put(chave, atributos.get(chave));
        }
        return new ErrorResponse.CampoErro(erro.getField(), info.codigo(), parametros);
    }
}
