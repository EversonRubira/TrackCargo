export type PedidoEstado =
  | 'CRIADO'
  | 'DOCUMENTACAO_ENVIADA'
  | 'DOCUMENTACAO_ACEITA'
  | 'PAGAMENTO_PARCIAL_RECEBIDO'
  | 'EMBARCADO'
  | 'PAGAMENTO_SALDO_RECEBIDO'
  | 'DOCUMENTOS_ORIGINAIS_ENVIADOS'
  | 'ENTREGUE'
  | 'CANCELADO'

export const PEDIDO_ESTADOS: PedidoEstado[] = [
  'CRIADO',
  'DOCUMENTACAO_ENVIADA',
  'DOCUMENTACAO_ACEITA',
  'PAGAMENTO_PARCIAL_RECEBIDO',
  'EMBARCADO',
  'PAGAMENTO_SALDO_RECEBIDO',
  'DOCUMENTOS_ORIGINAIS_ENVIADOS',
  'ENTREGUE',
  'CANCELADO',
]

// Espelha PedidoEstado#TRANSICOES_MANUAIS (backend) — transições válidas via PATCH /transicionar.
export const TRANSICOES_MANUAIS: Record<PedidoEstado, PedidoEstado[]> = {
  CRIADO: ['CANCELADO'],
  DOCUMENTACAO_ENVIADA: ['CANCELADO'],
  DOCUMENTACAO_ACEITA: ['PAGAMENTO_PARCIAL_RECEBIDO', 'CANCELADO'],
  PAGAMENTO_PARCIAL_RECEBIDO: ['EMBARCADO', 'CANCELADO'],
  EMBARCADO: ['PAGAMENTO_SALDO_RECEBIDO'],
  PAGAMENTO_SALDO_RECEBIDO: ['DOCUMENTOS_ORIGINAIS_ENVIADOS'],
  DOCUMENTOS_ORIGINAIS_ENVIADOS: ['ENTREGUE'],
  ENTREGUE: [],
  CANCELADO: [],
}

export type TipoDocumento =
  | 'INVOICE'
  | 'PACKING_LIST'
  | 'BL'
  | 'CERTIFICADO_SANITARIO'
  | 'DOCUMENTO_ADICIONAL'

export const TIPOS_DOCUMENTO: TipoDocumento[] = [
  'INVOICE',
  'PACKING_LIST',
  'BL',
  'CERTIFICADO_SANITARIO',
  'DOCUMENTO_ADICIONAL',
]

export type Incoterm =
  | 'EXW'
  | 'FCA'
  | 'FAS'
  | 'FOB'
  | 'CFR'
  | 'CIF'
  | 'CPT'
  | 'CIP'
  | 'DAP'
  | 'DPU'
  | 'DDP'

export const INCOTERMS: Incoterm[] = [
  'EXW',
  'FCA',
  'FAS',
  'FOB',
  'CFR',
  'CIF',
  'CPT',
  'CIP',
  'DAP',
  'DPU',
  'DDP',
]

export type FormaPagamento =
  | 'CARTA_CREDITO'
  | 'TT_ANTECIPADO'
  | 'TT_CONTRA_DOCUMENTOS'
  | 'COBRANCA_DOCUMENTARIA'

export const FORMAS_PAGAMENTO: FormaPagamento[] = [
  'CARTA_CREDITO',
  'TT_ANTECIPADO',
  'TT_CONTRA_DOCUMENTOS',
  'COBRANCA_DOCUMENTARIA',
]

export interface CondicoesComerciaisRequest {
  precoAcordado: number
  moeda: string
  incoterm: Incoterm
  formaPagamento: FormaPagamento
  percentualParcial: number
}

export interface CriarPedidoRequest {
  numeroPedido: string
  cliente: string
  consignee: string
  paisDestino: string
  portoOrigem: string
  portoDestino: string
  produto: string
  quantidade: number
  unidadeMedida: string
  condicoesComerciais: CondicoesComerciaisRequest
}

export interface AtualizarLogisticaRequest {
  ciaMaritima?: string
  numeroContainer?: string
}

export interface TransicionarRequest {
  novoEstado: PedidoEstado
}

export interface ReabrirDocumentoRequest {
  motivo: string
}

export interface ChecklistDocumentoResponse {
  tipoDocumento: TipoDocumento
  enviado: boolean
  aceito: boolean
  enviadoEm: string | null
  aceitoEm: string | null
  reabertoEm: string | null
  motivoReabertura: string | null
}

export interface PedidoResponse {
  numeroPedido: string
  cliente: string
  consignee: string
  paisDestino: string
  portoOrigem: string
  portoDestino: string
  produto: string
  quantidade: number
  unidadeMedida: string
  ciaMaritima: string | null
  numeroContainer: string | null
  precoAcordado: number
  moeda: string
  incoterm: Incoterm
  formaPagamento: FormaPagamento
  percentualParcial: number
  estado: PedidoEstado
  pagamentoParcialConfirmadoEm: string | null
  pagamentoSaldoConfirmadoEm: string | null
  criadoEm: string
  atualizadoEm: string
  checklist: ChecklistDocumentoResponse[]
}

export interface PedidoTransicaoResponse {
  estadoAnterior: PedidoEstado
  estadoNovo: PedidoEstado
  ocorridoEm: string
}

export interface ErrorResponse {
  erro: string
  mensagem: string
  estadoAtual: PedidoEstado | null
  estadoSolicitado: PedidoEstado | null
}
