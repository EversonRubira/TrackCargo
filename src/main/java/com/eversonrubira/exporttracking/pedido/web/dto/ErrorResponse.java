package com.eversonrubira.exporttracking.pedido.web.dto;

import java.util.List;
import java.util.Map;

// Corpo de erro padrao. `erro` e o codigo geral (granular por natureza
// pros 7 erros de dominio; sempre "VALIDACAO_INVALIDA"/"JSON_MALFORMADO"/
// "PARAMETRO_INVALIDO" pros demais). `mensagem` e texto de depuracao em
// PT, mantido so pra log/debug - o cliente traduz pelos codigos, nao
// exibe este campo. `parametros` carrega os dados de um erro de dominio
// (ex: numeroPedido, tipo, estadoAtual/estadoSolicitado - agora dentro
// do mapa, sem campos proprios duplicando a mesma informacao). `campos`
// carrega os erros de validacao, um item por campo invalido, cada um
// com seu proprio codigo granular (ver ValidacaoCodigoMapper) e
// parametros (ex: max de um @Size, min/max de um @FaixaDecimal).
public record ErrorResponse(
        String erro,
        String mensagem,
        Map<String, Object> parametros,
        List<CampoErro> campos
) {
    public record CampoErro(String campo, String codigo, Map<String, Object> parametros) {
    }

    public static ErrorResponse de(String erro, String mensagem) {
        return new ErrorResponse(erro, mensagem, null, null);
    }

    public static ErrorResponse deDominio(String erro, String mensagem, Map<String, Object> parametros) {
        return new ErrorResponse(erro, mensagem, parametros, null);
    }

    public static ErrorResponse deValidacao(String mensagem, List<CampoErro> campos) {
        return new ErrorResponse("VALIDACAO_INVALIDA", mensagem, null, campos);
    }
}
