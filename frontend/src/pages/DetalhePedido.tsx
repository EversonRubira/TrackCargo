import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useParams } from 'react-router'
import {
  ApiError,
  aceitarDocumento,
  atualizarLogistica,
  buscarHistorico,
  buscarPedido,
  confirmarPagamentoParcial,
  confirmarPagamentoSaldo,
  enviarDocumento,
  reabrirDocumento,
  recusarDocumento,
  transicionar,
  urlStatusPdf,
} from '../api/client'
import {
  TRANSICOES_MANUAIS,
  type PedidoResponse,
  type PedidoTransicaoResponse,
} from '../api/types'

export default function DetalhePedido() {
  const { numeroPedido } = useParams<{ numeroPedido: string }>()
  const [pedido, setPedido] = useState<PedidoResponse | null>(null)
  const [historico, setHistorico] = useState<PedidoTransicaoResponse[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoEmCurso, setAcaoEmCurso] = useState(false)

  const recarregar = useCallback(async () => {
    if (!numeroPedido) return
    setCarregando(true)
    setErro(null)
    try {
      const [p, h] = await Promise.all([buscarPedido(numeroPedido), buscarHistorico(numeroPedido)])
      setPedido(p)
      setHistorico(h)
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Falha ao carregar pedido.')
    } finally {
      setCarregando(false)
    }
  }, [numeroPedido])

  useEffect(() => {
    recarregar()
  }, [recarregar])

  async function executar(acao: () => Promise<unknown>) {
    setAcaoEmCurso(true)
    setErro(null)
    try {
      await acao()
      await recarregar()
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : 'Ação falhou.')
    } finally {
      setAcaoEmCurso(false)
    }
  }

  if (carregando) return <p className="text-stone-500">Carregando...</p>
  if (erro && !pedido) return <p className="text-red-600">{erro}</p>
  if (!pedido || !numeroPedido) return null

  const transicoesDisponiveis = TRANSICOES_MANUAIS[pedido.estado]

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">Pedido {pedido.numeroPedido}</h1>
        <span className="mt-1 inline-block rounded-full bg-stone-100 px-2 py-1 text-xs font-medium text-stone-700">
          {pedido.estado}
        </span>
      </div>

      {erro && <p className="text-red-600">{erro}</p>}

      <Secao titulo="Dados do pedido">
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
          <Item label="Cliente" valor={pedido.cliente} />
          <Item label="Consignee" valor={pedido.consignee} />
          <Item label="País destino" valor={pedido.paisDestino} />
          <Item label="Porto origem" valor={pedido.portoOrigem} />
          <Item label="Porto destino" valor={pedido.portoDestino} />
          <Item label="Produto" valor={pedido.produto} />
          <Item label="Quantidade" valor={`${pedido.quantidade} ${pedido.unidadeMedida}`} />
          <Item label="Preço acordado" valor={`${pedido.precoAcordado} ${pedido.moeda}`} />
          <Item label="Incoterm" valor={pedido.incoterm} />
          <Item label="Forma de pagamento" valor={pedido.formaPagamento} />
          <Item label="Percentual parcial" valor={`${pedido.percentualParcial}%`} />
          <Item label="Cia marítima" valor={pedido.ciaMaritima ?? '—'} />
          <Item label="Nº container" valor={pedido.numeroContainer ?? '—'} />
        </dl>
      </Secao>

      <Secao titulo="Logística">
        <FormularioLogistica
          pedido={pedido}
          desabilitado={acaoEmCurso}
          onSalvar={(ciaMaritima, numeroContainer) =>
            executar(() => atualizarLogistica(numeroPedido, { ciaMaritima, numeroContainer }))
          }
        />
      </Secao>

      <Secao titulo="Checklist de documentos">
        <table className="w-full text-left text-sm">
          <thead className="text-stone-500">
            <tr>
              <th className="py-2">Documento</th>
              <th className="py-2">Enviado</th>
              <th className="py-2">Aceito</th>
              <th className="py-2">Ações</th>
            </tr>
          </thead>
          <tbody>
            {pedido.checklist.map((doc) => (
              <tr key={doc.tipoDocumento} className="border-t border-stone-100">
                <td className="py-2 font-medium">{doc.tipoDocumento}</td>
                <td className="py-2">{doc.enviadoEm ? 'Sim' : 'Não'}</td>
                <td className="py-2">{doc.aceitoEm ? 'Sim' : 'Não'}</td>
                <td className="space-x-2 py-2">
                  {!doc.enviadoEm && (
                    <BotaoAcao
                      disabled={acaoEmCurso}
                      onClick={() =>
                        executar(() => enviarDocumento(numeroPedido, doc.tipoDocumento))
                      }
                    >
                      Enviar
                    </BotaoAcao>
                  )}
                  {doc.enviadoEm && !doc.aceitoEm && (
                    <BotaoAcao
                      disabled={acaoEmCurso}
                      onClick={() =>
                        executar(() => aceitarDocumento(numeroPedido, doc.tipoDocumento))
                      }
                    >
                      Aceitar
                    </BotaoAcao>
                  )}
                  {doc.enviadoEm && !doc.aceitoEm && (
                    <BotaoRecusar
                      disabled={acaoEmCurso}
                      onConfirmar={(motivo) =>
                        executar(() => recusarDocumento(numeroPedido, doc.tipoDocumento, motivo))
                      }
                    />
                  )}
                  {doc.aceitoEm && (
                    <BotaoReabrir
                      disabled={acaoEmCurso}
                      onConfirmar={(motivo) =>
                        executar(() => reabrirDocumento(numeroPedido, doc.tipoDocumento, motivo))
                      }
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Secao>

      <Secao titulo="Ações do pedido">
        <div className="flex flex-wrap gap-2">
          {pedido.estado === 'DOCUMENTACAO_ACEITA' && (
            <BotaoAcao
              disabled={acaoEmCurso}
              onClick={() => executar(() => confirmarPagamentoParcial(numeroPedido))}
            >
              Confirmar pagamento parcial
            </BotaoAcao>
          )}
          {pedido.estado === 'EMBARCADO' && (
            <BotaoAcao
              disabled={acaoEmCurso}
              onClick={() => executar(() => confirmarPagamentoSaldo(numeroPedido))}
            >
              Confirmar pagamento de saldo
            </BotaoAcao>
          )}
          {transicoesDisponiveis.map((novoEstado) => (
            <BotaoAcao
              key={novoEstado}
              disabled={acaoEmCurso}
              variante={novoEstado === 'CANCELADO' ? 'perigo' : 'padrao'}
              onClick={() => executar(() => transicionar(numeroPedido, novoEstado))}
            >
              {novoEstado === 'CANCELADO' ? 'Cancelar pedido' : `Avançar para ${novoEstado}`}
            </BotaoAcao>
          ))}
          <a
            href={urlStatusPdf(numeroPedido)}
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-stone-300 px-4 py-2 text-sm font-medium text-stone-700 hover:bg-stone-100"
          >
            Gerar PDF de status
          </a>
        </div>
      </Secao>

      <Secao titulo="Histórico de transições">
        <ul className="space-y-2 text-sm">
          {historico.map((h, i) => (
            <li key={i} className="flex justify-between border-b border-stone-100 pb-2">
              <span>
                {h.estadoAnterior} → {h.estadoNovo}
              </span>
              <span className="text-stone-500">{new Date(h.ocorridoEm).toLocaleString('pt-BR')}</span>
            </li>
          ))}
          {historico.length === 0 && <li className="text-stone-500">Sem transições registradas.</li>}
        </ul>
      </Secao>
    </div>
  )
}

function Secao({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-stone-200 bg-white p-5">
      <h2 className="mb-3 font-display text-lg font-semibold text-stone-900">{titulo}</h2>
      {children}
    </section>
  )
}

function Item({ label, valor }: { label: string; valor: string }) {
  return (
    <div>
      <dt className="text-stone-500">{label}</dt>
      <dd className="font-medium text-stone-900">{valor}</dd>
    </div>
  )
}

function BotaoAcao({
  children,
  onClick,
  disabled,
  variante = 'padrao',
}: {
  children: ReactNode
  onClick: () => void
  disabled?: boolean
  variante?: 'padrao' | 'perigo'
}) {
  const cores =
    variante === 'perigo'
      ? 'border-red-300 text-red-700 hover:bg-red-50'
      : 'border-stone-300 text-stone-700 hover:bg-stone-100'
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={`rounded-md border px-4 py-2 text-sm font-medium disabled:opacity-50 ${cores}`}
    >
      {children}
    </button>
  )
}

function BotaoReabrir({
  disabled,
  onConfirmar,
}: {
  disabled?: boolean
  onConfirmar: (motivo: string) => void
}) {
  const [abrindo, setAbrindo] = useState(false)
  const [motivo, setMotivo] = useState('')

  if (!abrindo) {
    return (
      <BotaoAcao disabled={disabled} onClick={() => setAbrindo(true)}>
        Reabrir
      </BotaoAcao>
    )
  }

  return (
    <span className="inline-flex items-center gap-2">
      <input
        autoFocus
        placeholder="Motivo"
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
        className="input w-40"
      />
      <BotaoAcao
        disabled={disabled || !motivo}
        onClick={() => {
          onConfirmar(motivo)
          setAbrindo(false)
          setMotivo('')
        }}
      >
        Confirmar
      </BotaoAcao>
    </span>
  )
}

function BotaoRecusar({
  disabled,
  onConfirmar,
}: {
  disabled?: boolean
  onConfirmar: (motivo: string) => void
}) {
  const [abrindo, setAbrindo] = useState(false)
  const [motivo, setMotivo] = useState('')

  if (!abrindo) {
    return (
      <BotaoAcao variante="perigo" disabled={disabled} onClick={() => setAbrindo(true)}>
        Recusar
      </BotaoAcao>
    )
  }

  const motivoValido = motivo.trim().length > 0 && motivo.length <= 500

  return (
    <span className="inline-flex items-center gap-2">
      <input
        autoFocus
        placeholder="Motivo (aparece no PDF do cliente)"
        value={motivo}
        maxLength={500}
        onChange={(e) => setMotivo(e.target.value)}
        className="input w-56"
      />
      <BotaoAcao
        variante="perigo"
        disabled={disabled || !motivoValido}
        onClick={() => {
          onConfirmar(motivo.trim())
          setAbrindo(false)
          setMotivo('')
        }}
      >
        Confirmar
      </BotaoAcao>
    </span>
  )
}

function FormularioLogistica({
  pedido,
  desabilitado,
  onSalvar,
}: {
  pedido: PedidoResponse
  desabilitado: boolean
  onSalvar: (ciaMaritima: string | undefined, numeroContainer: string | undefined) => void
}) {
  const [ciaMaritima, setCiaMaritima] = useState(pedido.ciaMaritima ?? '')
  const [numeroContainer, setNumeroContainer] = useState(pedido.numeroContainer ?? '')

  return (
    <div className="flex flex-wrap items-end gap-4">
      <label className="text-sm">
        <span className="mb-1 block font-medium text-stone-700">Cia marítima</span>
        <input
          value={ciaMaritima}
          onChange={(e) => setCiaMaritima(e.target.value)}
          className="input"
        />
      </label>
      <label className="text-sm">
        <span className="mb-1 block font-medium text-stone-700">Nº container</span>
        <input
          value={numeroContainer}
          onChange={(e) => setNumeroContainer(e.target.value)}
          className="input"
        />
      </label>
      <BotaoAcao
        disabled={desabilitado}
        onClick={() => onSalvar(ciaMaritima || undefined, numeroContainer || undefined)}
      >
        Salvar
      </BotaoAcao>
    </div>
  )
}
