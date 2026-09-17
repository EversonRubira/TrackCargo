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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse tratarPedidoNaoEncontrado(PedidoNaoEncontradoException ex) {
        return ErrorResponse.de("PEDIDO_NAO_ENCONTRADO", ex.getMessage());
    }

    @ExceptionHandler(ChecklistDocumentoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse tratarChecklistDocumentoNaoEncontrado(ChecklistDocumentoNaoEncontradoException ex) {
        return ErrorResponse.de("CHECKLIST_DOCUMENTO_NAO_ENCONTRADO", ex.getMessage());
    }

    @ExceptionHandler(TransicaoInvalidaException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarTransicaoInvalida(TransicaoInvalidaException ex) {
        return new ErrorResponse("TRANSICAO_INVALIDA", ex.getMessage(), ex.getEstadoAtual(), ex.getEstadoSolicitado());
    }

    @ExceptionHandler(DocumentoJaAceitoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoJaAceito(DocumentoJaAceitoException ex) {
        return ErrorResponse.de("DOCUMENTO_JA_ACEITO", ex.getMessage());
    }

    @ExceptionHandler(DocumentoNaoEnviadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoNaoEnviado(DocumentoNaoEnviadoException ex) {
        return ErrorResponse.de("DOCUMENTO_NAO_ENVIADO", ex.getMessage());
    }

    @ExceptionHandler(DocumentoNaoAceitoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoNaoAceito(DocumentoNaoAceitoException ex) {
        return ErrorResponse.de("DOCUMENTO_NAO_ACEITO", ex.getMessage());
    }

    @ExceptionHandler(DocumentoAdicionalJaExisteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse tratarDocumentoAdicionalJaExiste(DocumentoAdicionalJaExisteException ex) {
        return ErrorResponse.de("DOCUMENTO_ADICIONAL_JA_EXISTE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse tratarValidacaoInvalida(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(err -> err.getField()))
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ErrorResponse.de("VALIDACAO_INVALIDA", mensagem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse tratarParametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ErrorResponse.de("PARAMETRO_INVALIDO",
                "Valor invalido para " + ex.getName() + ": " + ex.getValue());
    }
}
