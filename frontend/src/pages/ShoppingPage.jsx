// Shopping list (Feature 10): built on top of the food catalog. Mark catalog
// products as "to buy"; the estimated total sums the latest known price × quantity.
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  addToList,
  clearChecked,
  getShoppingList,
  removeListItem,
  updateListItem,
} from '../api/shoppingList'
import { listProducts } from '../api/products'
import { SubmitButton } from '../components/form'
import { EmptyState, ErrorState, LoadingState, Notice, SelectField } from '../components/ui'
import { formatCurrency } from '../lib/format'

export default function ShoppingPage() {
  const [data, setData] = useState(null)
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [selectedProduct, setSelectedProduct] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [adding, setAdding] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    Promise.all([getShoppingList(), listProducts()])
      .then(([list, prods]) => {
        setData(list)
        setProducts(prods)
      })
      .catch((err) => setError(err?.message ?? 'Error al cargar la lista de la compra'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  const items = data?.items ?? []
  const inListIds = new Set(items.map((i) => i.productId))
  const available = products.filter((p) => !inListIds.has(p.id))
  const hasChecked = items.some((i) => i.checked)

  async function handleAdd(e) {
    e.preventDefault()
    if (!selectedProduct) return
    setAdding(true)
    setNotice(null)
    try {
      await addToList({ productId: Number(selectedProduct), quantity: Number(quantity) || 1 })
      setSelectedProduct('')
      setQuantity('1')
      load()
    } catch (err) {
      setNotice({ tone: 'error', text: err?.message ?? 'No se pudo añadir' })
    } finally {
      setAdding(false)
    }
  }

  async function toggleChecked(item) {
    try {
      await updateListItem(item.id, { checked: !item.checked })
      load()
    } catch (err) {
      setNotice({ tone: 'error', text: err?.message ?? 'No se pudo actualizar' })
    }
  }

  async function remove(item) {
    try {
      await removeListItem(item.id)
      load()
    } catch (err) {
      setNotice({ tone: 'error', text: err?.message ?? 'No se pudo eliminar' })
    }
  }

  async function handleClearChecked() {
    try {
      await clearChecked()
      load()
    } catch (err) {
      setNotice({ tone: 'error', text: err?.message ?? 'No se pudo vaciar' })
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-semibold tracking-tight text-slate-900">Lista de la compra</h1>
          {data && (
            <span className="rounded-full bg-emerald-50 px-3 py-1 text-sm font-semibold text-emerald-700">
              Total estimado: {formatCurrency(data.estimatedTotal)}
            </span>
          )}
        </div>
        {hasChecked && (
          <button type="button" onClick={handleClearChecked}
                  className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:border-slate-300 hover:text-slate-900">
            Vaciar comprados
          </button>
        )}
      </div>

      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>{notice.text}</Notice>
      )}

      {loading && <LoadingState />}
      {!loading && error && <ErrorState message={error} onRetry={load} />}

      {!loading && !error && (
        <>
          {products.length === 0 ? (
            <EmptyState
              title="No hay productos en el catálogo"
              message="Añade productos en el catálogo primero para poder ponerlos en la lista."
              action={(
                <Link to="/productos"
                      className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700">
                  Ir al catálogo
                </Link>
              )}
            />
          ) : (
            <form onSubmit={handleAdd} className="flex flex-wrap items-end gap-2">
              <div className="min-w-[12rem] flex-1">
                <SelectField label="Producto" name="product" value={selectedProduct}
                             onChange={(e) => setSelectedProduct(e.target.value)}>
                  <option value="">Selecciona un producto</option>
                  {available.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </SelectField>
              </div>
              <div className="w-24">
                <label htmlFor="sl-qty" className="mb-1.5 block text-sm font-medium text-slate-700">Cantidad</label>
                <input id="sl-qty" type="number" min="1" value={quantity}
                       onChange={(e) => setQuantity(e.target.value)}
                       className="h-10 w-full rounded-lg border border-slate-200 px-3 focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100" />
              </div>
              <SubmitButton loading={adding} loadingText="Añadiendo…">Añadir</SubmitButton>
            </form>
          )}

          {items.length === 0 && products.length > 0 && (
            <EmptyState
              title="Tu lista está vacía"
              message="Añade productos del catálogo para empezar a comprar."
            />
          )}

          {items.length > 0 && (
            <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
              {items.map((it) => (
                <li key={it.id} className="flex items-center gap-3 px-4 py-3">
                  <input type="checkbox" checked={it.checked}
                         onChange={() => toggleChecked(it)}
                         aria-label={`Marcar ${it.productName} como comprado`}
                         className="h-4 w-4 rounded border-slate-300 accent-emerald-600" />
                  <div className="min-w-0 flex-1">
                    <p className={['truncate text-sm font-medium', it.checked ? 'text-slate-400 line-through' : 'text-slate-900'].join(' ')}>
                      {it.productName} <span className="text-slate-400">×{it.quantity}</span>
                    </p>
                    <p className="truncate text-xs text-slate-500">
                      {it.unitPrice != null
                        ? `${formatCurrency(it.unitPrice)} · ${it.supermarket}`
                        : 'sin precio'}
                    </p>
                  </div>
                  {it.lineTotal != null && (
                    <span className="shrink-0 text-sm font-semibold tabular-nums text-slate-700">
                      {formatCurrency(it.lineTotal)}
                    </span>
                  )}
                  <button type="button" onClick={() => remove(it)} aria-label={`Eliminar ${it.productName}`}
                          className="shrink-0 rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                      <path d="M3 6h18" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
