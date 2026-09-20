import type {
  AtualizarLogisticaRequest,
  CriarPedidoRequest,
  ErrorResponse,
  PedidoEstado,
  PedidoResponse,
  PedidoTransicaoResponse,
  ProximoNumeroResponse,
  TipoDocumento,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  readonly status: number
  readonly body: ErrorResponse | null

  constructor(status: number, body: ErrorResponse | null) {
    super(body?.mensagem ?? `Erro HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorResponse | null
    throw new ApiError(response.status, body)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function listarPedidos(estado?: PedidoEstado): Promise<PedidoResponse[]> {
  const query = estado ? `?estado=${encodeURIComponent(estado)}` : ''
  return request(`/pedidos${query}`)
}

export function buscarPedido(numeroPedido: string): Promise<PedidoResponse> {
  return request(`/pedidos/${encodeURIComponent(numeroPedido)}`)
}

export function criarPedido(dto: CriarPedidoRequest): Promise<PedidoResponse> {
  return request('/pedidos', { method: 'POST', body: JSON.stringify(dto) })
}

export function buscarProximoNumeroSugerido(): Promise<ProximoNumeroResponse> {
  return request('/pedidos/proximo-numero')
}

export function transicionar(
  numeroPedido: string,
  novoEstado: PedidoEstado,
): Promise<PedidoResponse> {
  return request(`/pedidos/${encodeURIComponent(numeroPedido)}/transicionar`, {
    method: 'PATCH',
    body: JSON.stringify({ novoEstado }),
  })
}

export function confirmarPagamentoParcial(numeroPedido: string): Promise<PedidoResponse> {
  return request(`/pedidos/${encodeURIComponent(numeroPedido)}/pagamento-parcial`, {
    method: 'POST',
  })
}

export function confirmarPagamentoSaldo(numeroPedido: string): Promise<PedidoResponse> {
  return request(`/pedidos/${encodeURIComponent(numeroPedido)}/pagamento-saldo`, {
    method: 'POST',
  })
}

export function buscarHistorico(numeroPedido: string): Promise<PedidoTransicaoResponse[]> {
  return request(`/pedidos/${encodeURIComponent(numeroPedido)}/historico`)
}

export function atualizarLogistica(
  numeroPedido: string,
  dto: AtualizarLogisticaRequest,
): Promise<PedidoResponse> {
  return request(`/pedidos/${encodeURIComponent(numeroPedido)}/logistica`, {
    method: 'PATCH',
    body: JSON.stringify(dto),
  })
}

export function enviarDocumento(numeroPedido: string, tipo: TipoDocumento): Promise<void> {
  return request(
    `/pedidos/${encodeURIComponent(numeroPedido)}/documentos/${tipo}/enviar`,
    { method: 'PATCH' },
  )
}

export function aceitarDocumento(numeroPedido: string, tipo: TipoDocumento): Promise<void> {
  return request(
    `/pedidos/${encodeURIComponent(numeroPedido)}/documentos/${tipo}/aceitar`,
    { method: 'PATCH' },
  )
}

export function reabrirDocumento(
  numeroPedido: string,
  tipo: TipoDocumento,
  motivo: string,
): Promise<void> {
  return request(
    `/pedidos/${encodeURIComponent(numeroPedido)}/documentos/${tipo}/reabrir`,
    { method: 'PATCH', body: JSON.stringify({ motivo }) },
  )
}

export function recusarDocumento(
  numeroPedido: string,
  tipo: TipoDocumento,
  motivo: string,
): Promise<void> {
  return request(
    `/pedidos/${encodeURIComponent(numeroPedido)}/documentos/${tipo}/recusar`,
    { method: 'PATCH', body: JSON.stringify({ motivo }) },
  )
}

export function urlStatusPdf(numeroPedido: string): string {
  return `${API_BASE_URL}/pedidos/${encodeURIComponent(numeroPedido)}/status.pdf`
}
