import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, listarPedidos } from '../api/client'
import { PEDIDO_ESTADOS, type PedidoEstado, type PedidoResponse } from '../api/types'

export default function ListaPedidos() {
  const [pedidos, setPedidos] = useState<PedidoResponse[]>([])
  const [estado, setEstado] = useState<PedidoEstado | ''>('')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    setCarregando(true)
    setErro(null)
    listarPedidos(estado || undefined)
      .then(setPedidos)
      .catch((e: unknown) => {
        setErro(e instanceof ApiError ? e.message : 'Falha ao carregar pedidos.')
      })
      .finally(() => setCarregando(false))
  }, [estado])

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Pedidos</h1>
        <select
          value={estado}
          onChange={(e) => setEstado(e.target.value as PedidoEstado | '')}
          className="rounded-md border border-stone-300 bg-white px-3 py-2 text-sm"
        >
          <option value="">Todos os estados</option>
          {PEDIDO_ESTADOS.map((e) => (
            <option key={e} value={e}>
              {e}
            </option>
          ))}
        </select>
      </div>

      {carregando && <p className="text-stone-500">Carregando...</p>}
      {erro && <p className="text-red-600">{erro}</p>}

      {!carregando && !erro && (
        <div className="overflow-x-auto rounded-lg border border-stone-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-200 bg-stone-100 text-stone-600">
              <tr>
                <th className="px-4 py-3">Número</th>
                <th className="px-4 py-3">Cliente</th>
                <th className="px-4 py-3">Consignee</th>
                <th className="px-4 py-3">Produto</th>
                <th className="px-4 py-3">Cia marítima</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3">Atualizado em</th>
              </tr>
            </thead>
            <tbody>
              {pedidos.map((p) => (
                <tr key={p.numeroPedido} className="border-b border-stone-100 last:border-0">
                  <td className="px-4 py-3">
                    <Link
                      to={`/pedidos/${p.numeroPedido}`}
                      className="font-medium text-stone-900 underline-offset-2 hover:underline"
                    >
                      {p.numeroPedido}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{p.cliente}</td>
                  <td className="px-4 py-3">{p.consignee}</td>
                  <td className="px-4 py-3">{p.produto}</td>
                  <td className="px-4 py-3">{p.ciaMaritima ?? '—'}</td>
                  <td className="px-4 py-3">
                    <span className="rounded-full bg-stone-100 px-2 py-1 text-xs font-medium text-stone-700">
                      {p.estado}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-stone-500">
                    {new Date(p.atualizadoEm).toLocaleString('pt-BR')}
                  </td>
                </tr>
              ))}
              {pedidos.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-6 text-center text-stone-500">
                    Nenhum pedido encontrado.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
