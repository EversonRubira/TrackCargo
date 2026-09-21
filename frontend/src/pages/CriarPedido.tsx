import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, buscarProximoNumeroSugerido, criarPedido } from '../api/client'
import { FORMAS_PAGAMENTO, INCOTERMS, MOEDAS, type CriarPedidoRequest } from '../api/types'

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

export default function CriarPedido() {
  const navigate = useNavigate()
  const [form, setForm] = useState<CriarPedidoRequest>(ESTADO_INICIAL)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    buscarProximoNumeroSugerido()
      .then(({ numeroPedidoSugerido }) => campo('numeroPedido', numeroPedidoSugerido))
      .catch(() => {
        // Falha na sugestao nao impede o cadastro - usuario preenche manualmente.
      })
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
    setErro(null)
    try {
      const pedido = await criarPedido(form)
      navigate(`/pedidos/${encodeURIComponent(pedido.numeroPedido)}`)
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Falha ao criar pedido.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold">Novo pedido</h1>
      <form onSubmit={onSubmit} className="space-y-8">
        <Secao titulo="Identificação">
          <Campo label="Número do pedido">
            <input
              required
              value={form.numeroPedido}
              onChange={(e) => campo('numeroPedido', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label="Cliente">
            <input
              required
              value={form.cliente}
              onChange={(e) => campo('cliente', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label="Consignee">
            <input
              required
              value={form.consignee}
              onChange={(e) => campo('consignee', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label="País de destino">
            <input
              required
              value={form.paisDestino}
              onChange={(e) => campo('paisDestino', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label="Porto de origem">
            <input
              required
              value={form.portoOrigem}
              onChange={(e) => campo('portoOrigem', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label="Porto de destino">
            <input
              required
              value={form.portoDestino}
              onChange={(e) => campo('portoDestino', e.target.value)}
              className="input"
            />
          </Campo>
        </Secao>

        <Secao titulo="Descrição da mercadoria">
          <Campo label="Produto">
            <input
              required
              value={form.produto}
              onChange={(e) => campo('produto', e.target.value)}
              className="input"
            />
          </Campo>
          <Campo label="Quantidade">
            <input
              required
              type="number"
              min={0}
              value={form.quantidade}
              onChange={(e) => campo('quantidade', Number(e.target.value))}
              className="input"
            />
          </Campo>
          <Campo label="Unidade de medida">
            <input
              required
              value={form.unidadeMedida}
              onChange={(e) => campo('unidadeMedida', e.target.value)}
              className="input"
            />
          </Campo>
        </Secao>

        <Secao titulo="Condições comerciais">
          <Campo label="Preço acordado">
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
          <Campo label="Moeda">
            <select
              value={form.condicoesComerciais.moeda}
              onChange={(e) =>
                campoComercial('moeda', e.target.value as CriarPedidoRequest['condicoesComerciais']['moeda'])
              }
              className="input"
            >
              {MOEDAS.map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>
          </Campo>
          <Campo label="Incoterm">
            <select
              value={form.condicoesComerciais.incoterm}
              onChange={(e) =>
                campoComercial('incoterm', e.target.value as CriarPedidoRequest['condicoesComerciais']['incoterm'])
              }
              className="input"
            >
              {INCOTERMS.map((i) => (
                <option key={i} value={i}>
                  {i}
                </option>
              ))}
            </select>
          </Campo>
          <Campo label="Forma de pagamento">
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
                  {f}
                </option>
              ))}
            </select>
          </Campo>
          <Campo label="Percentual parcial (%)">
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

        {erro && <p className="text-red-600">{erro}</p>}

        <button
          type="submit"
          disabled={enviando}
          className="rounded-md bg-stone-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-stone-700 disabled:opacity-50"
        >
          {enviando ? 'Criando...' : 'Criar pedido'}
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

function Campo({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-stone-700">{label}</span>
      {children}
    </label>
  )
}
