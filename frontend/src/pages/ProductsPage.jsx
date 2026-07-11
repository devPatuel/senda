// Products page (Feature 7): food catalog + per-supermarket price manager.
// Each product shows its current price per supermarket (cheapest first) and lets
// you register a new price or inspect the full history.
import { useCallback, useEffect, useState } from 'react'
import {
  addPrice as apiAddPrice,
  createProduct,
  listPrices,
  listProducts,
  removeProduct,
  updateProduct,
} from '../api/products'
import { Field, FormError, SubmitButton } from '../components/form'
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingState,
  Modal,
  Notice,
  SelectField,
} from '../components/ui'
import { formatCurrency, formatDate } from '../lib/format'

const UNIT_TYPES = [
  { id: 'WEIGHT', label: 'Peso' },
  { id: 'QUANTITY', label: 'Cantidad' },
]

function unitTypeLabel(id) {
  return UNIT_TYPES.find((u) => u.id === id)?.label ?? id
}

// --- Product add/edit modal ------------------------------------------------
function ProductModal({ product, onClose, onSave }) {
  const [form, setForm] = useState({
    name: product?.name ?? '',
    unitType: product?.unitType ?? 'WEIGHT',
    amount: product?.amount != null ? String(product.amount) : '',
    unit: product?.unit ?? '',
  })
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState(null)
  const [loading, setLoading] = useState(false)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  async function handleSubmit(e) {
    e.preventDefault()
    const fe = {}
    if (!form.name.trim()) fe.name = 'El nombre es obligatorio'
    if (form.amount === '' || Number(form.amount) <= 0) fe.amount = 'Indica una cantidad positiva'
    if (!form.unit.trim()) fe.unit = 'La unidad es obligatoria'
    if (Object.keys(fe).length) { setErrors(fe); return }

    setLoading(true)
    setApiError(null)
    try {
      const payload = {
        name: form.name.trim(),
        unitType: form.unitType,
        amount: Number(form.amount),
        unit: form.unit.trim(),
      }
      const saved = product
        ? await updateProduct(product.id, payload)
        : await createProduct(payload)
      onSave(saved)
    } catch (err) {
      setApiError(err?.message ?? 'Error al guardar')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal title={product ? 'Editar producto' : 'Nuevo producto'} onClose={onClose} dismissable={!loading}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <FormError message={apiError} />
        <Field label="Nombre" name="name" value={form.name} onChange={set('name')} error={errors.name} />
        <SelectField label="Tipo de unidad" name="unitType" value={form.unitType} onChange={set('unitType')}>
          {UNIT_TYPES.map((u) => (
            <option key={u.id} value={u.id}>{u.label}</option>
          ))}
        </SelectField>
        <Field label="Cantidad" name="amount" type="number" step="0.001" min="0"
               value={form.amount} onChange={set('amount')} error={errors.amount} />
        <Field label="Unidad (p. ej. L, kg, ud)" name="unit" value={form.unit}
               onChange={set('unit')} error={errors.unit} placeholder="L" />
        <SubmitButton loading={loading} loadingText="Guardando…">Guardar</SubmitButton>
      </form>
    </Modal>
  )
}

// --- Register price modal --------------------------------------------------
function PriceModal({ product, onClose, onSaved }) {
  const [form, setForm] = useState({ supermarket: '', price: '' })
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState(null)
  const [loading, setLoading] = useState(false)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  async function handleSubmit(e) {
    e.preventDefault()
    const fe = {}
    if (!form.supermarket.trim()) fe.supermarket = 'Indica el supermercado'
    if (form.price === '' || Number(form.price) <= 0) fe.price = 'Indica un precio positivo'
    if (Object.keys(fe).length) { setErrors(fe); return }

    setLoading(true)
    setApiError(null)
    try {
      await apiAddPrice(product.id, {
        supermarket: form.supermarket.trim(),
        price: Number(form.price),
      })
      onSaved()
    } catch (err) {
      setApiError(err?.message ?? 'Error al registrar el precio')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal title={`Registrar precio · ${product.name}`} onClose={onClose} dismissable={!loading}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <FormError message={apiError} />
        <Field label="Supermercado" name="supermarket" value={form.supermarket}
               onChange={set('supermarket')} error={errors.supermarket} placeholder="Mercadona" />
        <Field label="Precio (€)" name="price" type="number" step="0.01" min="0"
               value={form.price} onChange={set('price')} error={errors.price} />
        <SubmitButton loading={loading} loadingText="Guardando…">Guardar</SubmitButton>
      </form>
    </Modal>
  )
}

// --- Price history modal ---------------------------------------------------
function HistoryModal({ product, onClose }) {
  const [entries, setEntries] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let alive = true
    listPrices(product.id)
      .then((data) => { if (alive) setEntries(data) })
      .catch((err) => { if (alive) setError(err?.message ?? 'Error al cargar el histórico') })
    return () => { alive = false }
  }, [product.id])

  return (
    <Modal title={`Histórico · ${product.name}`} onClose={onClose}>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {!error && entries === null && <LoadingState />}
      {!error && entries?.length === 0 && (
        <p className="text-sm text-slate-500">Aún no hay precios registrados.</p>
      )}
      {!error && entries?.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {entries.map((e) => (
            <li key={e.id} className="flex items-center justify-between py-2 text-sm">
              <span className="text-slate-700">{e.supermarket}</span>
              <span className="text-slate-500">{formatDate(e.recordedAt.slice(0, 10))}</span>
              <span className="font-semibold text-slate-900">{formatCurrency(e.price)}</span>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}

// --- Product card ----------------------------------------------------------
function ProductCard({ product, onAddPrice, onHistory, onEdit, onDelete }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-900">{product.name}</h3>
          <p className="text-sm text-slate-500">
            {product.amount} {product.unit} · {unitTypeLabel(product.unitType)}
          </p>
        </div>
        <div className="flex shrink-0 gap-1.5">
          <button type="button" onClick={() => onEdit(product)}
                  className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs text-slate-600 hover:border-slate-300 hover:text-slate-900">
            Editar
          </button>
          <button type="button" onClick={() => onDelete(product)}
                  className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs text-red-600 hover:border-red-300">
            Eliminar
          </button>
        </div>
      </div>

      {product.currentPrices.length === 0 ? (
        <p className="mt-3 text-sm text-slate-400">Sin precios registrados.</p>
      ) : (
        <ul className="mt-3 space-y-1">
          {product.currentPrices.map((cp, i) => (
            <li key={cp.supermarket}
                className="flex items-center justify-between rounded-lg px-2 py-1 text-sm">
              <span className="text-slate-700">{cp.supermarket}</span>
              <span className={i === 0 ? 'font-semibold text-emerald-700' : 'text-slate-600'}>
                {formatCurrency(cp.price)}
                {i === 0 && product.currentPrices.length > 1 && (
                  <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-700">
                    Más barato
                  </span>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-4 flex gap-2">
        <button type="button" onClick={() => onAddPrice(product)}
                className="rounded-lg bg-emerald-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-emerald-700">
          Registrar precio
        </button>
        <button type="button" onClick={() => onHistory(product)}
                className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:border-slate-300 hover:text-slate-900">
          Histórico
        </button>
      </div>
    </div>
  )
}

// --- Page ------------------------------------------------------------------
export default function ProductsPage() {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  const [editing, setEditing] = useState(null) // product | 'new' | null
  const [pricing, setPricing] = useState(null) // product | null
  const [history, setHistory] = useState(null) // product | null
  const [deleting, setDeleting] = useState(null) // product | null
  const [deleteLoading, setDeleteLoading] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    listProducts()
      .then((data) => setProducts(data))
      .catch((err) => setError(err?.message ?? 'Error al cargar los productos'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  function handleSaved() {
    setEditing(null)
    setNotice({ tone: 'success', text: 'Producto guardado' })
    load()
  }

  function handlePriceSaved() {
    setPricing(null)
    setNotice({ tone: 'success', text: 'Precio registrado' })
    load()
  }

  async function confirmDelete() {
    setDeleteLoading(true)
    try {
      await removeProduct(deleting.id)
      setDeleting(null)
      setNotice({ tone: 'success', text: 'Producto eliminado' })
      load()
    } catch (err) {
      setDeleting(null)
      setNotice({ tone: 'error', text: err?.message ?? 'No se pudo eliminar' })
    } finally {
      setDeleteLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-slate-900">Productos</h1>
          <p className="text-sm text-slate-500">Catálogo de comida y precios por supermercado.</p>
        </div>
        <button type="button" onClick={() => setEditing('new')}
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700">
          Nuevo producto
        </button>
      </div>

      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>{notice.text}</Notice>
      )}

      {loading && <LoadingState />}
      {!loading && error && <ErrorState message={error} onRetry={load} />}
      {!loading && !error && products.length === 0 && (
        <EmptyState
          title="Sin productos"
          message="Crea tu primer producto para empezar a registrar precios."
          action={(
            <button type="button" onClick={() => setEditing('new')}
                    className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700">
              Nuevo producto
            </button>
          )}
        />
      )}

      {!loading && !error && products.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-2">
          {products.map((p) => (
            <ProductCard
              key={p.id}
              product={p}
              onAddPrice={setPricing}
              onHistory={setHistory}
              onEdit={setEditing}
              onDelete={setDeleting}
            />
          ))}
        </div>
      )}

      {editing && (
        <ProductModal
          product={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSave={handleSaved}
        />
      )}
      {pricing && (
        <PriceModal product={pricing} onClose={() => setPricing(null)} onSaved={handlePriceSaved} />
      )}
      {history && (
        <HistoryModal product={history} onClose={() => setHistory(null)} />
      )}
      {deleting && (
        <ConfirmDialog
          title="Eliminar producto"
          message={`¿Eliminar "${deleting.name}" y todo su histórico de precios?`}
          confirmLabel="Eliminar"
          loading={deleteLoading}
          onCancel={() => setDeleting(null)}
          onConfirm={confirmDelete}
        />
      )}
    </div>
  )
}
