import { describe, expect, test } from 'vitest'
import pt from './locales/pt.json'
import en from './locales/en.json'
import es from './locales/es.json'

// Achata um objeto de traducao aninhado em chaves com ponto
// ("erros.validacao.OBRIGATORIO") pra comparar os 3 idiomas por
// conjunto de chaves, nao por valor (o texto e diferente de
// proposito, so a estrutura precisa ser identica).
function chaves(objeto: unknown, prefixo = ''): string[] {
  if (typeof objeto !== 'object' || objeto === null) {
    return [prefixo]
  }
  return Object.entries(objeto).flatMap(([chave, valor]) =>
    chaves(valor, prefixo ? `${prefixo}.${chave}` : chave),
  )
}

describe('paridade de chaves de traducao pt/en/es', () => {
  test('os 3 idiomas tem exatamente o mesmo conjunto de chaves', () => {
    const chavesPt = chaves(pt).sort()
    const chavesEn = chaves(en).sort()
    const chavesEs = chaves(es).sort()

    expect(chavesPt.length).toBeGreaterThan(0)
    expect(chavesEn).toEqual(chavesPt)
    expect(chavesEs).toEqual(chavesPt)
  })
})
