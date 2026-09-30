// Wishlist page (Feature 9): rich cards with photo, product link, comment, price
// and a running total. Independent from the shopping list.
import { useCallback, useEffect, useState } from 'react'
import { createWishItem, getWishlist, removeWishItem, updateWishItem } from '../api/wishlist'
import { Field, FormError, SubmitButton } from '../components/form'
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, Modal, Notice } from '../components/ui'
import { formatCurrency } from '../lib/format'

// The API only stores http(s) links; an address pasted without the scheme
// ("tienda.com/silla") is assumed to be https rather than rejected.
function webAddress(raw) {
  const value = raw.trim()
  if (!value) return null
  return /^https?:\/\//i.test(value) ? value : `https://${value}`
}

function WishModal({ item, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: item?.name ?? '',
    imageUrl: item?.imageUrl ?? '',
    productUrl: item?.productUrl ?? '',
    comment: item?.comment ?? '',
    price: item?.price != null ? String(item.price) : '',
    priority: item?.priority != null ? String(item.priority) : '',
  })
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState(null)
  const [loading, setLoading] = useState(false)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.name.trim()) { setErrors({ name: 'El nombre es obligatorio' }); return }
    setErrors({})
    setLoading(true)
    setApiError(null)
    try {
      const payload = {
        name: form.name.trim(),
        imageUrl: webAddress(form.imageUrl),
        productUrl: webAddress(form.productUrl),
        comment: form.comment.trim() || null,
        price: form.price === '' ? null : Number(form.price),
        priority: form.priority === '' ? null : Number(form.priority),
      }
      if (item) await updateWishItem(item.id, payload)
      else await createWishItem(payload)
      onSaved()
    } catch (err) {
      setApiError(err?.message ?? 'Error al guardar')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal title={item ? 'Editar deseo' : 'Nuevo deseo'} onClose={onClose} dismissable={!loading}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <FormError message={apiError} />
        <Field label="Nombre" name="name" value={form.name} onChange={set('name')} error={errors.name} />
        <Field label="URL de imagen" name="imageUrl" value={form.imageUrl} onChange={set('imageUrl')}
               placeholder="https://…" />
        <Field label="URL del producto" name="productUrl" value={form.productUrl} onChange={set('productUrl')}
               placeholder="https://…" />
        <Field label="Comentario" name="comment" value={form.comment} onChange={set('comment')} />
        <Field label="Precio (€)" name="price" type="number" step="0.01" min="0"
               value={form.price} onChange={set('price')} />
        <Field label="Prioridad (1-5)" name="priority" type="number" min="1" max="5"
               value={form.priority} onChange={set('priority')} />
        <SubmitButton loading={loading} loadingText="Guardando…">Guardar</SubmitButton>
      </form>
    </Modal>
  )
}

function WishCard({ item, onEdit, onDelete }) {
  return (
    <li className="flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      {item.imageUrl && (
        <img src={item.imageUrl} alt={item.name}
             className="h-40 w-full object-cover"
             onError={(e) => { e.currentTarget.style.display = 'none' }} />
      )}
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-slate-900">{item.name}</span>
          {item.priority != null && (
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">P{item.priority}</span>
          )}
        </div>
        {item.price != null && (
          <p className="text-sm font-medium text-slate-700">{formatCurrency(item.price)}</p>
        )}
        {item.comment && <p className="text-xs text-slate-500">{item.comment}</p>}
        {item.productUrl && (
          <a href={item.productUrl} target="_blank" rel="noopener noreferrer"
             className="text-xs font-medium text-emerald-600 hover:underline">Ver producto ↗</a>
        )}
        <div className="mt-auto flex justify-end gap-1 pt-2">
          <button type="button" onClick={() => onEdit(item)} aria-label={`Editar ${item.name}`}
                  className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
              <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
            </svg>
          </button>
          <button type="button" onClick={() => onDelete(item)} aria-label={`Eliminar ${item.name}`}
                  className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
              <path d="M3 6h18" />
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
              <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
            </svg>
          </button>
        </div>
      </div>
    </li>
  )
}

export default function WishlistPage() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  // Bumping reloadKey re-fetches; loading is derived so the effect never sets
  // state synchronously (which would trigger a cascading render)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const loading = loadedKey !== reloadKey
  const [notice, setNotice] = useState(null)
  const [editing, setEditing] = useState(null) // item | 'new' | null
  const [deleting, setDeleting] = useState(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    getWishlist()
      .then((res) => {
        if (!cancelled) {
          setData(res)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err?.message ?? 'Error al cargar la lista de deseos')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(reloadKey)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const load = useCallback(() => setReloadKey((k) => k + 1), [])

  function handleSaved() {
    setEditing(null)
    setNotice({ tone: 'success', text: 'Deseo guardado' })
    load()
  }

  async function confirmDelete() {
    setDeleteLoading(true)
    try {
      await removeWishItem(deleting.id)
      setDeleting(null)
      setNotice({ tone: 'success', text: 'Deseo eliminado' })
      load()
    } catch (err) {
      setDeleting(null)
      setNotice({ tone: 'error', text: err?.message ?? 'No se pudo eliminar' })
    } finally {
      setDeleteLoading(false)
    }
  }

  const items = data?.items ?? []

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-semibold tracking-tight text-slate-900">Deseos</h1>
          {data && (
            <span className="rounded-full bg-emerald-50 px-3 py-1 text-sm font-semibold text-emerald-700">
              Total: {formatCurrency(data.total)}
            </span>
          )}
        </div>
        <button type="button" onClick={() => setEditing('new')}
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700">
          Nuevo deseo
        </button>
      </div>

      {notice && (
        <Notice tone={notice.tone} onClose={() => setNotice(null)}>{notice.text}</Notice>
      )}

      {loading && <LoadingState />}
      {!loading && error && <ErrorState message={error} onRetry={load} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState
          title="Tu lista de deseos está vacía"
          message="Añade cosas que quieras comprar, con foto, enlace y precio."
        />
      )}

      {!loading && !error && items.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2">
          {items.map((it) => (
            <WishCard key={it.id} item={it} onEdit={setEditing} onDelete={setDeleting} />
          ))}
        </ul>
      )}

      {editing && (
        <WishModal
          item={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={handleSaved}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Eliminar deseo"
          message={`¿Eliminar "${deleting.name}" de tu lista de deseos?`}
          confirmLabel="Eliminar"
          loading={deleteLoading}
          onCancel={() => setDeleting(null)}
          onConfirm={confirmDelete}
        />
      )}
    </div>
  )
}
