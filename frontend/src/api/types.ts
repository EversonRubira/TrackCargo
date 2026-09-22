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

export type Moeda = 'USD' | 'EUR' | 'BRL'

export const MOEDAS: Moeda[] = ['USD', 'EUR', 'BRL']

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
  moeda: Moeda
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
  moeda: Moeda
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

export interface ProximoNumeroResponse {
  numeroPedidoSugerido: string
}

// Contrato novo (i18n-infra Bloco 1, backend): erro/mensagem
// continuam existindo (mensagem e so texto de depuracao em PT, o
// front nao exibe mais ela direto). parametros carrega os dados de
// um erro de dominio (numeroPedido, tipo, estadoAtual/estadoSolicitado
// - os dois ultimos nao sao mais campos proprios, ver Iso6346/SPEC.md
// do backend); campos carrega os erros de validacao, um item por
// campo invalido. Os dois sao opcionais e mutuamente exclusivos -
// nunca os dois preenchidos ao mesmo tempo.
export interface CampoErro {
  campo: string | null
  codigo: string
  parametros: Record<string, unknown>
}

export interface ErrorResponse {
  erro: string
  mensagem: string
  parametros: Record<string, unknown> | null
  campos: CampoErro[] | null
}
