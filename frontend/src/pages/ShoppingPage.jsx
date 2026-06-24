// Shopping page: two tabs — Grocery list (GROCERY) and Wishlist (WISHLIST).
// Grocery: quick-add by name + checkbox to mark bought/unbought.
// Wishlist: cards with price, envelope, feasibility badge + modal for add/edit.
import { useCallback, useEffect, useRef, useState } from 'react'
import { getEnvelopes } from '../api/allocation'
import { createItem, listItems, removeItem, setBought, updateItem } from '../api/shopping'
import { Field, FormError, SubmitButton } from '../components/form'
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, Modal, Notice, SelectField } from '../components/ui'
import { formatCurrency } from '../lib/format'

// ---------------------------------------------------------------------------
// Tab constants
// ---------------------------------------------------------------------------
const TABS = [
  { id: 'GROCERY', label: 'Comida' },
  { id: 'WISHLIST', label: 'Deseos' },
]

// ---------------------------------------------------------------------------
// Feasibility badge
// ---------------------------------------------------------------------------
function FeasibleBadge({ feasible }) {
  if (feasible === null || feasible === undefined) return null
  return feasible ? (
    <span className="inline-flex items-center rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-700">
      Factible
    </span>
  ) : (
    <span className="inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700">
      Aún no
    </span>
  )
}

// ---------------------------------------------------------------------------
// Wishlist item modal form
// ---------------------------------------------------------------------------
function WishlistModal({ item, envelopes, onClose, onSave }) {
  const [form, setForm] = useState({
    name: item?.name ?? '',
    estimatedPrice: item?.estimatedPrice != null ? String(item.estimatedPrice) : '',
    envelopeId: item?.envelopeId != null ? String(item.envelopeId) : '',
    priority: item?.priority != null ? String(item.priority) : '',
    notes: item?.notes ?? '',
  })
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState(null)
  const [loading, setLoading] = useState(false)

  function set(field) {
    return (e) => setForm((f) => ({ ...f, [field]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const fieldErrors = {}
    if (!form.name.trim()) fieldErrors.name = 'El nombre es obligatorio'
    if (Object.keys(fieldErrors).length) { setErrors(fieldErrors); return }

    setLoading(true)
    setApiError(null)
    try {
      const payload = {
        listType: 'WISHLIST',
        name: form.name.trim(),
        estimatedPrice: form.estimatedPrice !== '' ? Number(form.estimatedPrice) : null,
        envelopeId: form.envelopeId !== '' ? Number(form.envelopeId) : null,
        priority: form.priority !== '' ? Number(form.priority) : null,
        notes: form.notes.trim() || null,
      }
      const saved = item
        ? await updateItem(item.id, payload)
        : await createItem(payload)
      onSave(saved)
    } catch (err) {
      setApiError(err?.message ?? 'Error al guardar')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal title={item ? 'Editar deseo' : 'Nuevo deseo'} onClose={onClose}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Field
          label="Nombre"
          name="name"
          value={form.name}
          onChange={set('name')}
          placeholder="Ej. NAS, coche, viaje…"
          error={errors.name}
        />
        <Field
          label="Precio estimado (€)"
          name="estimatedPrice"
          type="number"
          min="0"
          step="0.01"
          value={form.estimatedPrice}
          onChange={set('estimatedPrice')}
          placeholder="Opcional"
        />
        <SelectField
          label="Sobre asociado"
          name="envelopeId"
          value={form.envelopeId}
          onChange={set('envelopeId')}
        >
          <option value="">Sin sobre</option>
          {envelopes.map((env) => (
            <option key={env.id} value={env.id}>
              {env.name} ({formatCurrency(env.balance)})
            </option>
          ))}
        </SelectField>
        <Field
          label="Prioridad"
          name="priority"
          type="number"
          min="1"
          max="5"
          value={form.priority}
          onChange={set('priority')}
          placeholder="Opcional (1-5, 1 = más urgente)"
        />
        <Field
          label="Notas"
          name="notes"
          value={form.notes}
          onChange={set('notes')}
          placeholder="Opcional"
        />
        {apiError && <FormError message={apiError} />}
        <SubmitButton loading={loading} loadingText="Guardando…">
          {item ? 'Guardar cambios' : 'Añadir deseo'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Grocery tab
// ---------------------------------------------------------------------------
function GroceryTab({ items, onToggle, onDelete, onAdd }) {
  const [newName, setNewName] = useState('')
  const [adding, setAdding] = useState(false)
  const inputRef = useRef(null)

  async function handleQuickAdd(e) {
    e.preventDefault()
    const name = newName.trim()
    if (!name) return
    setAdding(true)
    try {
      await onAdd({ listType: 'GROCERY', name })
      setNewName('')
      inputRef.current?.focus()
    } finally {
      setAdding(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Quick add form */}
      <form onSubmit={handleQuickAdd} className="flex gap-2">
        <input
          ref={inputRef}
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="Añadir producto…"
          className="flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
        />
        <button
          type="submit"
          disabled={adding || !newName.trim()}
          className="rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {adding ? '…' : 'Añadir'}
        </button>
      </form>

      {/* Item list */}
      {items.length === 0 ? (
        <EmptyState
          title="Lista vacía"
          message="Añade productos con el campo de arriba."
        />
      ) : (
        <ul className="flex flex-col divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white">
          {items.map((item) => (
            <li key={item.id} className="flex items-center gap-3 px-4 py-3">
              <input
                type="checkbox"
                id={`grocery-${item.id}`}
                checked={item.bought}
                onChange={() => onToggle(item)}
                className="h-4 w-4 rounded border-slate-300 accent-emerald-600"
              />
              <label
                htmlFor={`grocery-${item.id}`}
                className={[
                  'flex-1 text-sm',
                  item.bought ? 'text-slate-400 line-through' : 'text-slate-800',
                ].join(' ')}
              >
                {item.name}
              </label>
              <button
                type="button"
                onClick={() => onDelete(item)}
                aria-label={`Eliminar ${item.name}`}
                className="rounded p-1 text-slate-300 transition-colors hover:bg-red-50 hover:text-red-500"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
                  <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" />
                </svg>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Wishlist tab
// ---------------------------------------------------------------------------
function WishlistTab({ items, envelopes, onEdit, onDelete, onAdd }) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <button
          type="button"
          onClick={() => onAdd()}
          className="rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
        >
          + Nuevo deseo
        </button>
      </div>

      {items.length === 0 ? (
        <EmptyState
          title="Sin deseos todavía"
          message="Añade tu primer deseo con el botón de arriba."
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => (
            <li
              key={item.id}
              className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
            >
              <div className="flex items-start justify-between gap-2">
                <div className="flex flex-col gap-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-slate-900">{item.name}</span>
                    {item.priority != null && (
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
                        P{item.priority}
                      </span>
                    )}
                    <FeasibleBadge feasible={item.feasible} />
                  </div>
                  {item.estimatedPrice != null && (
                    <p className="text-sm text-slate-700">
                      {formatCurrency(item.estimatedPrice)}
                      {item.envelopeName && (
                        <span className="ml-1 text-slate-400">
                          · {item.envelopeName}
                          {item.envelopeBalance != null && (
                            <span> ({formatCurrency(item.envelopeBalance)} acumulados)</span>
                          )}
                        </span>
                      )}
                    </p>
                  )}
                  {item.notes && (
                    <p className="text-xs text-slate-500">{item.notes}</p>
                  )}
                </div>
                <div className="flex shrink-0 gap-1">
                  <button
                    type="button"
                    onClick={() => onEdit(item)}
                    aria-label={`Editar ${item.name}`}
                    className="rounded p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5Z" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    onClick={() => onDelete(item)}
                    aria-label={`Eliminar ${item.name}`}
                    className="rounded p-1.5 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-500"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
                      <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" />
                    </svg>
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------
export default function ShoppingPage() {
  const [activeTab, setActiveTab] = useState('GROCERY')
  const [items, setItems] = useState([])
  const [envelopes, setEnvelopes] = useState([])
  const [loadingItems, setLoadingItems] = useState(true)
  const [errorItems, setErrorItems] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(-1)

  // Wishlist modal state
  const [modalItem, setModalItem] = useState(undefined) // undefined = closed, null = new, obj = edit
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)
  const [notice, setNotice] = useState(null)

  const reload = useCallback(() => setReloadKey((k) => k + 1), [])

  // Load items + envelopes whenever tab or reloadKey changes
  useEffect(() => {
    if (loadedKey === reloadKey) return
    let cancelled = false

    setLoadingItems(true)
    setErrorItems(null)

    const fetchItems = listItems({ listType: activeTab })
    const fetchEnvelopes = getEnvelopes()

    Promise.all([fetchItems, fetchEnvelopes])
      .then(([fetchedItems, fetchedEnvelopes]) => {
        if (cancelled) return
        setItems(fetchedItems)
        setEnvelopes(fetchedEnvelopes)
        setLoadedKey(reloadKey)
      })
      .catch((err) => {
        if (cancelled) return
        setErrorItems(err?.message ?? 'Error al cargar')
      })
      .finally(() => {
        if (!cancelled) setLoadingItems(false)
      })

    return () => { cancelled = true }
  }, [activeTab, reloadKey, loadedKey])

  // Reset loadedKey when tab changes so data reloads
  function handleTabChange(tabId) {
    setActiveTab(tabId)
    setLoadedKey(-1)
  }

  // --- Grocery handlers ---

  async function handleToggleBought(item) {
    try {
      const updated = await setBought(item.id, !item.bought)
      setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)))
    } catch {
      setNotice({ tone: 'error', text: 'No se pudo actualizar el estado' })
    }
  }

  async function handleAddGrocery(data) {
    const created = await createItem(data)
    setItems((prev) => [created, ...prev])
  }

  // --- Shared delete ---

  async function handleConfirmDelete() {
    if (!confirmDelete) return
    setDeleting(true)
    try {
      await removeItem(confirmDelete.id)
      setItems((prev) => prev.filter((i) => i.id !== confirmDelete.id))
      setNotice({ tone: 'success', text: `"${confirmDelete.name}" eliminado` })
    } catch {
      setNotice({ tone: 'error', text: 'No se pudo eliminar' })
    } finally {
      setDeleting(false)
      setConfirmDelete(null)
    }
  }

  // --- Wishlist handlers ---

  function handleSaveWishlist(saved) {
    setItems((prev) => {
      const exists = prev.find((i) => i.id === saved.id)
      return exists
        ? prev.map((i) => (i.id === saved.id ? saved : i))
        : [saved, ...prev]
    })
    setModalItem(undefined)
    setNotice({ tone: 'success', text: `"${saved.name}" guardado` })
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-bold tracking-tight text-slate-900">
        Lista de la compra
      </h1>

      {/* Tabs */}
      <div className="mb-6 flex gap-1 rounded-xl border border-slate-200 bg-slate-100 p-1">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => handleTabChange(tab.id)}
            className={[
              'flex-1 rounded-lg py-2 text-sm font-medium transition-colors',
              activeTab === tab.id
                ? 'bg-white text-emerald-700 shadow-sm'
                : 'text-slate-500 hover:text-slate-800',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Notice */}
      {notice && (
        <div className="mb-4">
          <Notice tone={notice.tone} onClose={() => setNotice(null)}>
            {notice.text}
          </Notice>
        </div>
      )}

      {/* Content */}
      {loadingItems ? (
        <LoadingState />
      ) : errorItems ? (
        <ErrorState message={errorItems} onRetry={reload} />
      ) : activeTab === 'GROCERY' ? (
        <GroceryTab
          items={items}
          onToggle={handleToggleBought}
          onDelete={(item) => setConfirmDelete(item)}
          onAdd={handleAddGrocery}
        />
      ) : (
        <WishlistTab
          items={items}
          envelopes={envelopes}
          onEdit={(item) => setModalItem(item)}
          onDelete={(item) => setConfirmDelete(item)}
          onAdd={() => setModalItem(null)}
        />
      )}

      {/* Wishlist modal */}
      {modalItem !== undefined && (
        <WishlistModal
          item={modalItem}
          envelopes={envelopes}
          onClose={() => setModalItem(undefined)}
          onSave={handleSaveWishlist}
        />
      )}

      {/* Confirm delete dialog */}
      {confirmDelete && (
        <ConfirmDialog
          title="Eliminar elemento"
          message={`¿Eliminar "${confirmDelete.name}"? Esta acción no se puede deshacer.`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setConfirmDelete(null)}
          onConfirm={handleConfirmDelete}
        />
      )}
    </div>
  )
}
