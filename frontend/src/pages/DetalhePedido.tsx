import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
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
import { traduzirErro, type ErrosTraduzidos } from '../i18n/erros'
import { formatarData, formatarMoeda, formatarNumero } from '../i18n/intl'

const SEM_ERROS: ErrosTraduzidos = { porCampo: {}, mensagemGeral: null }

export default function DetalhePedido() {
  const { t, i18n } = useTranslation()
  const { numeroPedido } = useParams<{ numeroPedido: string }>()
  const [pedido, setPedido] = useState<PedidoResponse | null>(null)
  const [historico, setHistorico] = useState<PedidoTransicaoResponse[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erros, setErros] = useState<ErrosTraduzidos>(SEM_ERROS)
  const [acaoEmCurso, setAcaoEmCurso] = useState(false)

  const recarregar = useCallback(async () => {
    if (!numeroPedido) return
    setCarregando(true)
    setErros(SEM_ERROS)
    try {
      const [p, h] = await Promise.all([buscarPedido(numeroPedido), buscarHistorico(numeroPedido)])
      setPedido(p)
      setHistorico(h)
    } catch (e) {
      setErros(e instanceof ApiError ? traduzirErro(t, e) : { porCampo: {}, mensagemGeral: t('detail.erroCarregar') })
    } finally {
      setCarregando(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [numeroPedido])

  useEffect(() => {
    recarregar()
  }, [recarregar])

  async function executar(acao: () => Promise<unknown>) {
    setAcaoEmCurso(true)
    setErros(SEM_ERROS)
    try {
      await acao()
      await recarregar()
    } catch (e) {
      setErros(e instanceof ApiError ? traduzirErro(t, e) : { porCampo: {}, mensagemGeral: t('detail.erroAcao') })
    } finally {
      setAcaoEmCurso(false)
    }
  }

  if (carregando) return <p className="text-stone-500">{t('detail.carregando')}</p>
  if (erros.mensagemGeral && !pedido) return <p className="text-red-600">{erros.mensagemGeral}</p>
  if (!pedido || !numeroPedido) return null

  const transicoesDisponiveis = TRANSICOES_MANUAIS[pedido.estado]

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">{t('detail.titulo', { numeroPedido: pedido.numeroPedido })}</h1>
        <span className="mt-1 inline-block rounded-full bg-stone-100 px-2 py-1 text-xs font-medium text-stone-700">
          {t(`enums.pedidoEstado.${pedido.estado}`)}
        </span>
      </div>

      {erros.mensagemGeral && <p className="text-red-600">{erros.mensagemGeral}</p>}

      <Secao titulo={t('detail.secoes.dadosPedido')}>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
          <Item label={t('campos.cliente')} valor={pedido.cliente} />
          <Item label={t('campos.consignee')} valor={pedido.consignee} />
          <Item label={t('detail.campos.paisDestino')} valor={pedido.paisDestino} />
          <Item label={t('detail.campos.portoOrigem')} valor={pedido.portoOrigem} />
          <Item label={t('detail.campos.portoDestino')} valor={pedido.portoDestino} />
          <Item label={t('campos.produto')} valor={pedido.produto} />
          <Item
            label={t('detail.campos.quantidade')}
            valor={`${formatarNumero(pedido.quantidade, i18n.language)} ${pedido.unidadeMedida}`}
          />
          <Item
            label={t('detail.campos.precoAcordado')}
            valor={formatarMoeda(pedido.precoAcordado, pedido.moeda, i18n.language)}
          />
          <Item label={t('detail.campos.incoterm')} valor={t(`enums.incoterm.${pedido.incoterm}`)} />
          <Item
            label={t('detail.campos.formaPagamento')}
            valor={t(`enums.formaPagamento.${pedido.formaPagamento}`)}
          />
          <Item
            label={t('detail.campos.percentualParcial')}
            valor={`${formatarNumero(pedido.percentualParcial, i18n.language)}%`}
          />
          <Item label={t('campos.ciaMaritima')} valor={pedido.ciaMaritima ?? '—'} />
          <Item label={t('campos.numeroContainer')} valor={pedido.numeroContainer ?? '—'} />
        </dl>
      </Secao>

      <Secao titulo={t('detail.secoes.logistica')}>
        <FormularioLogistica
          pedido={pedido}
          desabilitado={acaoEmCurso}
          erroCiaMaritima={erros.porCampo.ciaMaritima}
          erroNumeroContainer={erros.porCampo.numeroContainer}
          onSalvar={(ciaMaritima, numeroContainer) =>
            executar(() => atualizarLogistica(numeroPedido, { ciaMaritima, numeroContainer }))
          }
        />
      </Secao>

      <Secao titulo={t('detail.secoes.checklist')}>
        <table className="w-full text-left text-sm">
          <thead className="text-stone-500">
            <tr>
              <th className="py-2">{t('detail.checklist.documento')}</th>
              <th className="py-2">{t('detail.checklist.enviado')}</th>
              <th className="py-2">{t('detail.checklist.aceito')}</th>
              <th className="py-2">{t('detail.checklist.acoes')}</th>
            </tr>
          </thead>
          <tbody>
            {pedido.checklist.map((doc) => (
              <tr key={doc.tipoDocumento} className="border-t border-stone-100">
                <td className="py-2 font-medium">{t(`enums.tipoDocumento.${doc.tipoDocumento}`)}</td>
                <td className="py-2">{doc.enviadoEm ? t('detail.checklist.sim') : t('detail.checklist.nao')}</td>
                <td className="py-2">{doc.aceitoEm ? t('detail.checklist.sim') : t('detail.checklist.nao')}</td>
                <td className="space-x-2 py-2">
                  {!doc.enviadoEm && (
                    <BotaoAcao
                      disabled={acaoEmCurso}
                      onClick={() =>
                        executar(() => enviarDocumento(numeroPedido, doc.tipoDocumento))
                      }
                    >
                      {t('detail.checklist.enviar')}
                    </BotaoAcao>
                  )}
                  {doc.enviadoEm && !doc.aceitoEm && (
                    <BotaoAcao
                      disabled={acaoEmCurso}
                      onClick={() =>
                        executar(() => aceitarDocumento(numeroPedido, doc.tipoDocumento))
                      }
                    >
                      {t('detail.checklist.aceitar')}
                    </BotaoAcao>
                  )}
                  {doc.enviadoEm && !doc.aceitoEm && (
                    <BotaoRecusar
                      disabled={acaoEmCurso}
                      erroMotivo={erros.porCampo.motivo}
                      onConfirmar={(motivo) =>
                        executar(() => recusarDocumento(numeroPedido, doc.tipoDocumento, motivo))
                      }
                    />
                  )}
                  {doc.aceitoEm && (
                    <BotaoReabrir
                      disabled={acaoEmCurso}
                      erroMotivo={erros.porCampo.motivo}
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

      <Secao titulo={t('detail.secoes.acoes')}>
        <div className="flex flex-wrap gap-2">
          {pedido.estado === 'DOCUMENTACAO_ACEITA' && (
            <BotaoAcao
              disabled={acaoEmCurso}
              onClick={() => executar(() => confirmarPagamentoParcial(numeroPedido))}
            >
              {t('detail.acoes.confirmarPagamentoParcial')}
            </BotaoAcao>
          )}
          {pedido.estado === 'EMBARCADO' && (
            <BotaoAcao
              disabled={acaoEmCurso}
              onClick={() => executar(() => confirmarPagamentoSaldo(numeroPedido))}
            >
              {t('detail.acoes.confirmarPagamentoSaldo')}
            </BotaoAcao>
          )}
          {transicoesDisponiveis.map((novoEstado) => (
            <BotaoAcao
              key={novoEstado}
              disabled={acaoEmCurso}
              variante={novoEstado === 'CANCELADO' ? 'perigo' : 'padrao'}
              onClick={() => executar(() => transicionar(numeroPedido, novoEstado))}
            >
              {novoEstado === 'CANCELADO'
                ? t('detail.acoes.cancelarPedido')
                : t('detail.acoes.avancarPara', { estado: t(`enums.pedidoEstado.${novoEstado}`) })}
            </BotaoAcao>
          ))}
          <a
            href={urlStatusPdf(numeroPedido, i18n.language)}
            target="_blank"
            rel="noreferrer"
            className="rounded-md border border-stone-300 px-4 py-2 text-sm font-medium text-stone-700 hover:bg-stone-100"
          >
            {t('detail.acoes.gerarPdf')}
          </a>
        </div>
      </Secao>

      <Secao titulo={t('detail.secoes.historico')}>
        <ul className="space-y-2 text-sm">
          {historico.map((h, i) => (
            <li key={i} className="flex justify-between border-b border-stone-100 pb-2">
              <span>
                {t(`enums.pedidoEstado.${h.estadoAnterior}`)} → {t(`enums.pedidoEstado.${h.estadoNovo}`)}
              </span>
              <span className="text-stone-500">{formatarData(h.ocorridoEm, i18n.language)}</span>
            </li>
          ))}
          {historico.length === 0 && <li className="text-stone-500">{t('detail.historico.vazio')}</li>}
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
  erroMotivo,
  onConfirmar,
}: {
  disabled?: boolean
  erroMotivo?: string
  onConfirmar: (motivo: string) => void
}) {
  const { t } = useTranslation()
  const [abrindo, setAbrindo] = useState(false)
  const [motivo, setMotivo] = useState('')

  if (!abrindo) {
    return (
      <BotaoAcao disabled={disabled} onClick={() => setAbrindo(true)}>
        {t('detail.checklist.reabrir')}
      </BotaoAcao>
    )
  }

  return (
    <span className="inline-flex items-center gap-2">
      <span className="inline-flex flex-col">
        <input
          autoFocus
          placeholder={t('detail.checklist.motivoPlaceholder')}
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          className="input w-40"
        />
        {erroMotivo && <span className="mt-1 text-xs text-red-600">{erroMotivo}</span>}
      </span>
      <BotaoAcao
        disabled={disabled || !motivo}
        onClick={() => {
          onConfirmar(motivo)
          setAbrindo(false)
          setMotivo('')
        }}
      >
        {t('detail.checklist.confirmar')}
      </BotaoAcao>
    </span>
  )
}

function BotaoRecusar({
  disabled,
  erroMotivo,
  onConfirmar,
}: {
  disabled?: boolean
  erroMotivo?: string
  onConfirmar: (motivo: string) => void
}) {
  const { t } = useTranslation()
  const [abrindo, setAbrindo] = useState(false)
  const [motivo, setMotivo] = useState('')

  if (!abrindo) {
    return (
      <BotaoAcao variante="perigo" disabled={disabled} onClick={() => setAbrindo(true)}>
        {t('detail.checklist.recusar')}
      </BotaoAcao>
    )
  }

  const motivoValido = motivo.trim().length > 0 && motivo.length <= 500

  return (
    <span className="inline-flex items-center gap-2">
      <span className="inline-flex flex-col">
        <input
          autoFocus
          placeholder={t('detail.checklist.motivoRecusaPlaceholder')}
          value={motivo}
          maxLength={500}
          onChange={(e) => setMotivo(e.target.value)}
          className="input w-56"
        />
        {erroMotivo && <span className="mt-1 text-xs text-red-600">{erroMotivo}</span>}
      </span>
      <BotaoAcao
        variante="perigo"
        disabled={disabled || !motivoValido}
        onClick={() => {
          onConfirmar(motivo.trim())
          setAbrindo(false)
          setMotivo('')
        }}
      >
        {t('detail.checklist.confirmar')}
      </BotaoAcao>
    </span>
  )
}

function FormularioLogistica({
  pedido,
  desabilitado,
  erroCiaMaritima,
  erroNumeroContainer,
  onSalvar,
}: {
  pedido: PedidoResponse
  desabilitado: boolean
  erroCiaMaritima?: string
  erroNumeroContainer?: string
  onSalvar: (ciaMaritima: string | undefined, numeroContainer: string | undefined) => void
}) {
  const { t } = useTranslation()
  const [ciaMaritima, setCiaMaritima] = useState(pedido.ciaMaritima ?? '')
  const [numeroContainer, setNumeroContainer] = useState(pedido.numeroContainer ?? '')

  useEffect(() => {
    setCiaMaritima(pedido.ciaMaritima ?? '')
    setNumeroContainer(pedido.numeroContainer ?? '')
  }, [pedido.ciaMaritima, pedido.numeroContainer])

  return (
    <div className="flex flex-wrap items-end gap-4">
      <label className="text-sm">
        <span className="mb-1 block font-medium text-stone-700">{t('campos.ciaMaritima')}</span>
        <input
          value={ciaMaritima}
          onChange={(e) => setCiaMaritima(e.target.value)}
          className="input"
        />
        {erroCiaMaritima && <span className="mt-1 block text-xs text-red-600">{erroCiaMaritima}</span>}
      </label>
      <label className="text-sm">
        <span className="mb-1 block font-medium text-stone-700">{t('campos.numeroContainer')}</span>
        <input
          value={numeroContainer}
          onChange={(e) => setNumeroContainer(e.target.value)}
          className="input"
        />
        {erroNumeroContainer && <span className="mt-1 block text-xs text-red-600">{erroNumeroContainer}</span>}
      </label>
      <BotaoAcao
        disabled={desabilitado}
        onClick={() => onSalvar(ciaMaritima || undefined, numeroContainer || undefined)}
      >
        {t('detail.logistica.salvar')}
      </BotaoAcao>
    </div>
  )
}
