import { useTranslation } from 'react-i18next'
import { Link, Route, Routes } from 'react-router'
import { IDIOMAS_SUPORTADOS, type Idioma } from './i18n'
import ListaPedidos from './pages/ListaPedidos'
import CriarPedido from './pages/CriarPedido'
import DetalhePedido from './pages/DetalhePedido'

export default function App() {
  const { t, i18n } = useTranslation()

  return (
    <div className="min-h-screen bg-stone-50 text-stone-900">
      <header className="border-b border-stone-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <Link to="/" className="font-display text-xl font-semibold text-stone-900">
            TrackCargo
          </Link>
          <div className="flex items-center gap-3">
            <SeletorDeIdioma idiomaAtual={i18n.language} onMudar={(idioma) => i18n.changeLanguage(idioma)} />
            <Link
              to="/pedidos/novo"
              className="rounded-md bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-700"
            >
              {t('app.novoPedido')}
            </Link>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">
        <Routes>
          <Route path="/" element={<ListaPedidos />} />
          <Route path="/pedidos/novo" element={<CriarPedido />} />
          <Route path="/pedidos/:numeroPedido" element={<DetalhePedido />} />
        </Routes>
      </main>
    </div>
  )
}

function SeletorDeIdioma({
  idiomaAtual,
  onMudar,
}: {
  idiomaAtual: string
  onMudar: (idioma: Idioma) => void
}) {
  const { t } = useTranslation()
  // i18n.language pode vir com regiao (ex: "pt-BR") antes do
  // nonExplicitSupportedLngs normalizar - cai em "pt" pro <select> não
  // ficar sem opção selecionada.
  const valorAtual = IDIOMAS_SUPORTADOS.includes(idiomaAtual as Idioma)
    ? (idiomaAtual as Idioma)
    : idiomaAtual.split('-')[0]

  return (
    <select
      value={valorAtual}
      onChange={(e) => onMudar(e.target.value as Idioma)}
      className="rounded-md border border-stone-300 bg-white px-2 py-1.5 text-sm"
      aria-label={t('idioma.pt') + '/' + t('idioma.en') + '/' + t('idioma.es')}
    >
      {IDIOMAS_SUPORTADOS.map((idioma) => (
        <option key={idioma} value={idioma}>
          {t(`idioma.${idioma}`)}
        </option>
      ))}
    </select>
  )
}
