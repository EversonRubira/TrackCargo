import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import i18n from '../i18n'
import { ApiError } from '../api/client'
import type { ErrorResponse } from '../api/types'
import CriarPedido from '../pages/CriarPedido'
import DetalhePedido from '../pages/DetalhePedido'

// JSONs reais copiados das respostas da API rodando (evidencia colada
// no Bloco 1/2 do i18n-infra), nao inventados - ver docs/STATUS.md do
// backend pra origem exata de cada um.
const ERRO_VALIDACAO_REAL: ErrorResponse = {
  erro: 'VALIDACAO_INVALIDA',
  mensagem:
    'condicoesComerciais.percentualParcial: percentualParcial deve estar entre 0 e 100; quantidade: quantidade deve ser maior que zero',
  parametros: null,
  campos: [
    { campo: 'condicoesComerciais.percentualParcial', codigo: 'FORA_DA_FAIXA', parametros: { min: 0.0, max: 100.0 } },
    { campo: 'quantidade', codigo: 'POSITIVO', parametros: {} },
  ],
}

const ERRO_DOMINIO_REAL: ErrorResponse = {
  erro: 'TRANSICAO_INVALIDA',
  mensagem: 'Transicao invalida de CRIADO para PAGAMENTO_SALDO_RECEBIDO',
  parametros: { estadoAtual: 'CRIADO', estadoSolicitado: 'PAGAMENTO_SALDO_RECEBIDO' },
  campos: null,
}

vi.mock('../api/client', async (importOriginal) => {
  const real = await importOriginal<typeof import('../api/client')>()
  return {
    ...real,
    criarPedido: vi.fn(),
    buscarProximoNumeroSugerido: vi.fn().mockResolvedValue({ numeroPedidoSugerido: 'PO-0001' }),
    buscarPedido: vi.fn(),
    buscarHistorico: vi.fn().mockResolvedValue([]),
    transicionar: vi.fn(),
  }
})

import { criarPedido, buscarPedido, transicionar } from '../api/client'

beforeEach(() => {
  vi.clearAllMocks()
  // O jsdom do ambiente de teste reporta navigator.language como
  // "en-US" - sem isso, i18next-browser-languagedetector detecta
  // "en" em vez do "pt" padrao da aplicacao (localStorage vazio no
  // teste, cai no navigator). Forca o idioma esperado pelas asserções.
  i18n.changeLanguage('pt')
})

describe('erro de validacao real exibido junto ao campo (CriarPedido)', () => {
  test('mostra a mensagem traduzida perto de quantidade e percentual parcial, nunca o texto cru do backend', async () => {
    vi.mocked(criarPedido).mockRejectedValueOnce(new ApiError(400, ERRO_VALIDACAO_REAL))

render(
      <MemoryRouter initialEntries={['/pedidos/novo']}>
        <Routes>
          <Route path="/pedidos/novo" element={<CriarPedido />} />
        </Routes>
      </MemoryRouter>,
    )

    // Preenche os campos obrigatorios (texto) pra passar da validacao
    // nativa do <form> no jsdom - so entao o submit chega no onSubmit
    // React e no criarPedido mockado, que e o que este teste quer
    // exercitar (o erro de quantidade/percentual vem do backend, nao
    // do HTML5).
    for (const label of [/número do pedido/i, /^cliente$/i, /consignee/i, /país de destino/i,
      /porto de origem/i, /porto de destino/i, /^produto$/i, /unidade de medida/i]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value: 'x' } })
    }

    fireEvent.click(screen.getByRole('button', { name: /criar pedido/i }))

    await waitFor(() => {
      expect(screen.getByText(/quantidade deve ser maior que zero/i)).toBeInTheDocument()
    })
    expect(screen.getByText(/percentual parcial.*deve estar entre 0 e 100/i)).toBeInTheDocument()

    // Nunca exibe o texto cru de `mensagem` (que viria em PT sempre,
    // mesmo com o idioma ativo em outro) - so as traducoes por codigo.
    expect(screen.queryByText(ERRO_VALIDACAO_REAL.mensagem)).not.toBeInTheDocument()
  })
})

describe('erro de dominio real exibido no banner (DetalhePedido)', () => {
  test('TRANSICAO_INVALIDA mostra estados traduzidos no banner, nunca o texto cru do backend', async () => {
    vi.mocked(buscarPedido).mockResolvedValue({
      numeroPedido: 'PO-0001',
      cliente: 'Cliente Teste',
      consignee: 'Consignee Teste',
      paisDestino: 'China',
      portoOrigem: 'Porto de Santos',
      portoDestino: 'Porto de Xangai',
      produto: 'Carne bovina',
      quantidade: 20,
      unidadeMedida: 'TON',
      ciaMaritima: null,
      numeroContainer: null,
      precoAcordado: 85000,
      moeda: 'USD',
      incoterm: 'CFR',
      formaPagamento: 'TT_ANTECIPADO',
      percentualParcial: 30,
      estado: 'CRIADO',
      pagamentoParcialConfirmadoEm: null,
      pagamentoSaldoConfirmadoEm: null,
      criadoEm: '2026-09-21T10:00:00',
      atualizadoEm: '2026-09-21T10:00:00',
      checklist: [],
    })
    vi.mocked(transicionar).mockRejectedValueOnce(new ApiError(409, ERRO_DOMINIO_REAL))

    render(
      <MemoryRouter initialEntries={['/pedidos/PO-0001']}>
        <Routes>
          <Route path="/pedidos/:numeroPedido" element={<DetalhePedido />} />
        </Routes>
      </MemoryRouter>,
    )

    const botaoCancelar = await screen.findByRole('button', { name: /cancelar pedido/i })
    fireEvent.click(botaoCancelar)

    await waitFor(() => {
      expect(screen.getByText(/n[ãa]o.*poss[íi]vel avan[çc]ar de criado para pagamento de saldo recebido/i))
        .toBeInTheDocument()
    })
    expect(screen.queryByText(ERRO_DOMINIO_REAL.mensagem)).not.toBeInTheDocument()
  })
})
