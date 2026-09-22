// Data/numero/moeda via Intl nativo conforme o idioma ativo - sem lib
// nova, o browser ja resolve formatacao por locale.

export function formatarData(iso: string, idioma: string): string {
  return new Intl.DateTimeFormat(idioma, { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso))
}

export function formatarNumero(valor: number, idioma: string): string {
  return new Intl.NumberFormat(idioma).format(valor)
}

export function formatarMoeda(valor: number, moeda: string, idioma: string): string {
  return new Intl.NumberFormat(idioma, { style: 'currency', currency: moeda }).format(valor)
}
