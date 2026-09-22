import type { TFunction } from 'i18next'
import type { ApiError } from '../api/client'

// Codigos de erro gerais que nao vem em `campos[]` - ver
// docs/SPEC.md do backend, secao "Contrato de erro". Cada um tem
// chave propria em erros.dominio.*.
const CODIGOS_GERAIS = new Set([
  'PEDIDO_NAO_ENCONTRADO',
  'CHECKLIST_DOCUMENTO_NAO_ENCONTRADO',
  'TRANSICAO_INVALIDA',
  'DOCUMENTO_JA_ACEITO',
  'DOCUMENTO_NAO_ENVIADO',
  'DOCUMENTO_NAO_ACEITO',
  'DOCUMENTO_ADICIONAL_JA_EXISTE',
  'PARAMETRO_INVALIDO',
  'JSON_MALFORMADO',
])

// Parametros cujo valor e um enum do backend (PedidoEstado/TipoDocumento)
// e precisa ser traduzido antes de interpolar na mensagem - o resto
// (numeroPedido, valor bruto de PARAMETRO_INVALIDO etc.) e exibido
// como veio.
const CHAVE_ENUM_POR_PARAMETRO: Record<string, string> = {
  tipo: 'enums.tipoDocumento',
  estadoAtual: 'enums.pedidoEstado',
  estadoSolicitado: 'enums.pedidoEstado',
}

export interface ErrosTraduzidos {
  porCampo: Record<string, string>
  mensagemGeral: string | null
}

function rotuloCampo(t: TFunction, caminho: string): string {
  const chave = caminho.split('.').pop() ?? caminho
  const traduzido = t(`campos.${chave}`, { defaultValue: '' })
  return traduzido || chave
}

// Converte os parametros crus do backend em valores prontos pra
// interpolar na chave de traducao: enums viram rotulo legivel, listas
// (valoresAceitos) viram string separada por virgula - i18next nao
// formata array sozinho.
function prepararParametros(t: TFunction, parametros: Record<string, unknown> | undefined): Record<string, unknown> {
  const preparados: Record<string, unknown> = {}
  for (const [chave, valor] of Object.entries(parametros ?? {})) {
    if (Array.isArray(valor)) {
      preparados[chave] = valor.join(', ')
      continue
    }
    const chaveEnum = CHAVE_ENUM_POR_PARAMETRO[chave]
    if (chaveEnum && typeof valor === 'string') {
      preparados[chave] = t(`${chaveEnum}.${valor}`, { defaultValue: valor })
      continue
    }
    preparados[chave] = valor
  }
  return preparados
}

// Traduz um ApiError (ver api/client.ts) pelo codigo, nunca pelo
// texto cru do backend (`mensagem` e so debug, nao chega na UI).
// campo com erro aparece perto do campo (porCampo, chave = caminho
// completo do JSON, ex. "condicoesComerciais.percentualParcial");
// o resto vai pro banner (mensagemGeral).
export function traduzirErro(t: TFunction, apiError: ApiError): ErrosTraduzidos {
  const body = apiError.body
  const porCampo: Record<string, string> = {}
  let mensagemGeral: string | null = null

  if (!body) {
    return { porCampo, mensagemGeral: t('erros.generico') }
  }

  if (body.campos && body.campos.length > 0) {
    for (const campoErro of body.campos) {
      const parametros = {
        campo: rotuloCampo(t, campoErro.campo ?? ''),
        ...prepararParametros(t, campoErro.parametros),
      }
      const chave = `erros.validacao.${campoErro.codigo}`
      const traduzida = t(chave, { ...parametros, defaultValue: '' })
      const mensagem = traduzida || t('erros.campoGenerico', parametros)
      if (campoErro.campo) {
        porCampo[campoErro.campo] = mensagem
      } else {
        mensagemGeral = mensagem
      }
    }
    return { porCampo, mensagemGeral }
  }

  if (CODIGOS_GERAIS.has(body.erro)) {
    const parametros = prepararParametros(t, body.parametros ?? undefined)
    const chave = `erros.dominio.${body.erro}`
    const traduzida = t(chave, { ...parametros, defaultValue: '' })
    return { porCampo, mensagemGeral: traduzida || t('erros.generico') }
  }

  return { porCampo, mensagemGeral: t('erros.generico') }
}
