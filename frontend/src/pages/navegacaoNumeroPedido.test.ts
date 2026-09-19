import { describe, expect, it } from 'vitest'

// Cobre o bug ja identificado: numeroPedido com barra (padrao novo de
// numeracao automatica, ex: "00001/2026") quebrava a navegacao porque
// virava dois segmentos de rota em vez de um so, e o react-router nao
// conseguia casar com a rota /pedidos/:numeroPedido. A correcao (Link
// to e navigate() em ListaPedidos.tsx/CriarPedido.tsx) passou a montar
// o caminho com encodeURIComponent(numeroPedido) - este teste reproduz
// o comportamento de segmentacao/decodificacao de uma URL sem precisar
// renderizar componentes ou subir um router de verdade.
describe('link de pedido com numero contendo barra', () => {
  it('gera um unico segmento de rota que decodifica de volta pro numero original', () => {
    const numeroPedido = '00001/2026'

    const linkGerado = `/pedidos/${encodeURIComponent(numeroPedido)}`
    const segmentos = linkGerado.split('/').filter(Boolean)

    // Sem encodeURIComponent, isto seria ['pedidos', '00001', '2026']
    // (3 segmentos) - o bug original, onde /pedidos/:numeroPedido nunca
    // casava porque a rota so espera 2 segmentos.
    expect(segmentos).toEqual(['pedidos', '00001%2F2026'])

    const numeroPedidoDoParam = decodeURIComponent(segmentos[1])
    expect(numeroPedidoDoParam).toBe(numeroPedido)
  })

  it('numero sem barra continua virando um segmento simples, sem escapes desnecessarios', () => {
    const numeroPedido = 'PO-0001'

    const linkGerado = `/pedidos/${encodeURIComponent(numeroPedido)}`

    expect(linkGerado).toBe('/pedidos/PO-0001')
  })
})
