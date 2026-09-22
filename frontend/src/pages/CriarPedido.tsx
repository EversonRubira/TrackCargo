import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { ApiError, buscarProximoNumeroSugerido, criarPedido } from '../api/client'
import { FORMAS_PAGAMENTO, INCOTERMS, MOEDAS, type CriarPedidoRequest } from '../api/types'
import { traduzirErro, type ErrosTraduzidos } from '../i18n/erros'

const ESTADO_INICIAL: CriarPedidoRequest = {
  numeroPedido: '',
  cliente: '',
  consignee: '',
  paisDestino: '',
  portoOrigem: '',
  portoDestino: '',
  produto: '',
  quantidade: 0,
  unidadeMedida: '',
  condicoesComerciais: {
    precoAcordado: 0,
    moeda: 'USD',
    incoterm: 'FOB',
    formaPagamento: 'CARTA_CREDITO',
    percentualParcial: 0,
  },
}

const SEM_ERROS: ErrosTraduzidos = { porCampo: {}, mensagemGeral: null }

export default function CriarPedido() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [form, setForm] = useState<CriarPedidoRequest>(ESTADO_INICIAL)
  const [enviando, setEnviando] = useState(false)
  const [erros, setErros] = useState<ErrosTraduzidos>(SEM_ERROS)

  useEffect(() => {
    buscarProximoNumeroSugerido()
      .then(({ numeroPedidoSugerido }) => campo('numeroPedido', numeroPedidoSugerido))
      .catch(() => {
        // Falha na sugestao nao impede o cadastro - usuario preenche manualmente.
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function campo<K extends keyof CriarPedidoRequest>(chave: K, valor: CriarPedidoRequest[K]) {
    setForm((atual) => ({ ...atual, [chave]: valor }))
  }

  function campoComercial<K extends keyof CriarPedidoRequest['condicoesComerciais']>(
    chave: K,
    valor: CriarPedidoRequest['condicoesComerciais'][K],
  ) {
    setForm((atual) => ({
      ...atual,
      condicoesComerciais: { ...atual.condicoesComerciais, [chave]: valor },
    }))
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setEnviando(true)
    setErros(SEM_ERROS)
    try {
      const pedido = await criarPedido(form)
      navigate(`/pedidos/${encodeURIComponent(pedido.numeroPedido)}`)
    } catch (e) {
      setErros(e instanceof ApiError ? traduzirErro(t, e) : { porCampo: {}, mensagemGeral: t('create.erroGenerico') })
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold">{t('create.titulo')}</h1>
      <form onSubmit={onSubmit} className="space-y-8">
        <Secao titulo={t('create.secoes.identificacao')}>
          <Campo label={t('campos.numeroPedido')} erro={erros.porCampo.numeroPedido}>
            <input
              required
              value={form.numeroPedido}
              onChange={(e) => campo('numeroPedido', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.cliente')} erro={erros.porCampo.cliente}>
            <input
              required
              value={form.cliente}
              onChange={(e) => campo('cliente', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.consignee')} erro={erros.porCampo.consignee}>
            <input
              required
              value={form.consignee}
              onChange={(e) => campo('consignee', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.paisDestino')} erro={erros.porCampo.paisDestino}>
            <input
              required
              value={form.paisDestino}
              onChange={(e) => campo('paisDestino', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.portoOrigem')} erro={erros.porCampo.portoOrigem}>
            <input
              required
              value={form.portoOrigem}
              onChange={(e) => campo('portoOrigem', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.portoDestino')} erro={erros.porCampo.portoDestino}>
            <input
              required
              value={form.portoDestino}
              onChange={(e) => campo('portoDestino', e.target.value)}
              className="input"
            />
          </Campo>
        </Secao>

        <Secao titulo={t('create.secoes.descricaoMercadoria')}>
          <Campo label={t('campos.produto')} erro={erros.porCampo.produto}>
            <input
              required
              value={form.produto}
              onChange={(e) => campo('produto', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.quantidade')} erro={erros.porCampo.quantidade}>
            <input
              required
              type="number"
              min={0}
              value={form.quantidade}
              onChange={(e) => campo('quantidade', Number(e.target.value))}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.unidadeMedida')} erro={erros.porCampo.unidadeMedida}>
            <input
              required
              value={form.unidadeMedida}
              onChange={(e) => campo('unidadeMedida', e.target.value)}
              className="input"
            />
          </Campo>
        </Secao>

        <Secao titulo={t('create.secoes.condicoesComerciais')}>
          <Campo
            label={t('campos.precoAcordado')}
            erro={erros.porCampo['condicoesComerciais.precoAcordado']}
          >
            <input
              required
              type="number"
              min={0}
              step="0.01"
              value={form.condicoesComerciais.precoAcordado}
              onChange={(e) => campoComercial('precoAcordado', Number(e.target.value))}
              className="input"
            />
          </Campo>
          <Campo label={t('campos.moeda')} erro={erros.porCampo['condicoesComerciais.moeda']}>
            <select
              value={form.condicoesComerciais.moeda}
              onChange={(e) =>
                campoComercial('moeda', e.target.value as CriarPedidoRequest['condicoesComerciais']['moeda'])
              }
              className="input"
            >
              {MOEDAS.map((m) => (
                <option key={m} value={m}>
                  {t(`enums.moeda.${m}`)}
                </option>
              ))}
            </select>
          </Campo>
          <Campo label={t('campos.incoterm')} erro={erros.porCampo['condicoesComerciais.incoterm']}>
            <select
              value={form.condicoesComerciais.incoterm}
              onChange={(e) =>
                campoComercial('incoterm', e.target.value as CriarPedidoRequest['condicoesComerciais']['incoterm'])
              }
              className="input"
            >
              {INCOTERMS.map((i) => (
                <option key={i} value={i}>
                  {t(`enums.incoterm.${i}`)}
                </option>
              ))}
            </select>
          </Campo>
          <Campo
            label={t('campos.formaPagamento')}
            erro={erros.porCampo['condicoesComerciais.formaPagamento']}
          >
            <select
              value={form.condicoesComerciais.formaPagamento}
              onChange={(e) =>
                campoComercial(
                  'formaPagamento',
                  e.target.value as CriarPedidoRequest['condicoesComerciais']['formaPagamento'],
                )
              }
              className="input"
            >
              {FORMAS_PAGAMENTO.map((f) => (
                <option key={f} value={f}>
                  {t(`enums.formaPagamento.${f}`)}
                </option>
              ))}
            </select>
          </Campo>
          <Campo
            label={t('campos.percentualParcial')}
            erro={erros.porCampo['condicoesComerciais.percentualParcial']}
          >
            <input
              required
              type="number"
              min={0}
              max={100}
              value={form.condicoesComerciais.percentualParcial}
              onChange={(e) => campoComercial('percentualParcial', Number(e.target.value))}
              className="input"
            />
          </Campo>
        </Secao>

        {erros.mensagemGeral && <p className="text-red-600">{erros.mensagemGeral}</p>}

        <button
          type="submit"
          disabled={enviando}
          className="rounded-md bg-stone-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-stone-700 disabled:opacity-50"
        >
          {enviando ? t('create.criando') : t('create.criarPedido')}
        </button>
      </form>
    </div>
  )
}

function Secao({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <fieldset className="rounded-lg border border-stone-200 bg-white p-5">
      <legend className="px-1 font-display text-lg font-semibold text-stone-900">{titulo}</legend>
      <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">{children}</div>
    </fieldset>
  )
}

function Campo({ label, erro, children }: { label: string; erro?: string; children: ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-stone-700">{label}</span>
      {children}
      {erro && <span className="mt-1 block text-xs text-red-600">{erro}</span>}
    </label>
  )
}
