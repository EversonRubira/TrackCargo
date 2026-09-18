import { Link, Route, Routes } from 'react-router'
import ListaPedidos from './pages/ListaPedidos'
import CriarPedido from './pages/CriarPedido'
import DetalhePedido from './pages/DetalhePedido'

export default function App() {
  return (
    <div className="min-h-screen bg-stone-50 text-stone-900">
      <header className="border-b border-stone-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <Link to="/" className="font-display text-xl font-semibold text-stone-900">
            TrackCargo
          </Link>
          <Link
            to="/pedidos/novo"
            className="rounded-md bg-stone-900 px-4 py-2 text-sm font-medium text-white hover:bg-stone-700"
          >
            Novo pedido
          </Link>
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
