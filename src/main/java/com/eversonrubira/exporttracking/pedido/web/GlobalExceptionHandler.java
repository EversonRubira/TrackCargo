package com.eversonrubira.exporttracking.pedido.web;

import com.eversonrubira.exporttracking.pedido.exception.ChecklistDocumentoNaoEncontradoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoAdicionalJaExisteException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoJaAceitoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoAceitoException;
import com.eversonrubira.exporttracking.pedido.exception.DocumentoNaoEnviadoException;
import com.eversonrubira.exporttracking.pedido.exception.PedidoNaoEncontradoException;
import com.eversonrubira.exporttracking.pedido.exception.TransicaoInvalidaException;
import com.eversonrubira.exporttracking.pedido.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse tratarPedidoNaoEncontrado(PedidoNaoEncontradoException ex) {
        return ErrorResponse.deDominio("PEDIDO_NAO_ENCONTRADO", ex.getMessage(),
                Map.of("numeroPedido", ex.getNumeroPedido()));
    }

    @ExceptionHandler(ChecklistDocumentoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse tratarChecklistDocumentoNaoEncontrado(ChecklistDocumentoNaoEncontradoException ex) {
        return ErrorResponse.deDominio("CHECKLIST_DOCUMENTO_NAO_ENCONTRADO", ex.getMessage(),
                Map.of("numeroPedido", ex.getNumeroPedido(), "tipo", ex.getTipo()));
    }

    @ExceptionHandler(TransicaoInvalidaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarTransicaoInvalida(TransicaoInvalidaException ex) {
        return ErrorResponse.deDominio("TRANSICAO_INVALIDA", ex.getMessage(),
                Map.of("estadoAtual", ex.getEstadoAtual(), "estadoSolicitado", ex.getEstadoSolicitado()));
    }

    @ExceptionHandler(DocumentoJaAceitoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoJaAceito(DocumentoJaAceitoException ex) {
        return ErrorResponse.deDominio("DOCUMENTO_JA_ACEITO", ex.getMessage(), Map.of("tipo", ex.getTipo()));
    }

    @ExceptionHandler(DocumentoNaoEnviadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoNaoEnviado(DocumentoNaoEnviadoException ex) {
        return ErrorResponse.deDominio("DOCUMENTO_NAO_ENVIADO", ex.getMessage(), Map.of("tipo", ex.getTipo()));
    }

    @ExceptionHandler(DocumentoNaoAceitoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoNaoAceito(DocumentoNaoAceitoException ex) {
        return ErrorResponse.deDominio("DOCUMENTO_NAO_ACEITO", ex.getMessage(), Map.of("tipo", ex.getTipo()));
    }

    @ExceptionHandler(DocumentoAdicionalJaExisteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoAdicionalJaExiste(DocumentoAdicionalJaExisteException ex) {
        return ErrorResponse.deDominio("DOCUMENTO_ADICIONAL_JA_EXISTE", ex.getMessage(),
                Map.of("numeroPedido", ex.getNumeroPedido()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse tratarValidacaoInvalida(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(err -> err.getField()))
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ErrorResponse.deValidacao(mensagem, ValidacaoCodigoMapper.paraCampos(ex.getBindingResult()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse tratarParametroInvalido(MethodArgumentTypeMismatchException ex) {
        String mensagem = "Valor invalido para " + ex.getName() + ": " + ex.getValue();
        return ErrorResponse.deDominio("PARAMETRO_INVALIDO", mensagem,
                Map.of("parametro", ex.getName(), "valor", String.valueOf(ex.getValue())));
    }

    // Enum invalido no corpo (ex: "moeda": "Yen") falha na desserializacao do
    // Jackson - antes de qualquer @Valid rodar, entao nao passa pelo handler
    // de MethodArgumentNotValidException acima. Extrai o campo do path do
    // erro e gera a lista de valores aceitos direto do enum (Enum.values()),
    // pra ampliar o enum depois nao exigir tocar aqui. JSON malformado sem
    // causa de enum identificavel cai no codigo generico JSON_MALFORMADO -
    // nunca expoe texto interno do Jackson (linha/coluna, nome de classe Java
    // etc.) nem em mensagem nem em parametros.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse tratarCorpoInvalido(HttpMessageNotReadableException ex) {
        InvalidFormatException enumInvalido = causaDeEnumInvalido(ex);
        if (enumInvalido != null) {
            String campo = campoDoEnumInvalido(enumInvalido);
            List<String> valoresAceitos = valoresAceitosDoEnum(enumInvalido);
            String mensagem = mensagemEnumInvalido(campo, valoresAceitos);
            ErrorResponse.CampoErro campoErro = new ErrorResponse.CampoErro(
                    campo.isBlank() ? null : campo, "VALOR_ENUM_INVALIDO", Map.of("valoresAceitos", valoresAceitos));
            return ErrorResponse.deValidacao(mensagem, List.of(campoErro));
        }
        return ErrorResponse.de("JSON_MALFORMADO", "Corpo da requisicao invalido ou mal formado");
    }

    private InvalidFormatException causaDeEnumInvalido(Throwable ex) {
        Throwable causa = ex.getCause();
        while (causa != null) {
            if (causa instanceof InvalidFormatException invalidFormat
                    && invalidFormat.getTargetType() != null
                    && invalidFormat.getTargetType().isEnum()) {
                return invalidFormat;
            }
            causa = causa.getCause();
        }
        return null;
    }

    private String campoDoEnumInvalido(InvalidFormatException ex) {
        return ex.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(nome -> nome != null && !nome.isBlank())
                .collect(Collectors.joining("."));
    }

    private List<String> valoresAceitosDoEnum(InvalidFormatException ex) {
        return Arrays.stream(ex.getTargetType().getEnumConstants())
                .map(Object::toString)
                .toList();
    }

    private String mensagemEnumInvalido(String campo, List<String> valoresAceitos) {
        String mensagem = "valores aceitos sao " + comEGramatical(valoresAceitos);
        return campo.isBlank() ? mensagem : campo + ": " + mensagem;
    }

    private String comEGramatical(List<String> valores) {
        if (valores.size() == 1) {
            return valores.get(0);
        }
        return String.join(", ", valores.subList(0, valores.size() - 1)) + " e " + valores.get(valores.size() - 1);
    }
}
