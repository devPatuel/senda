import { useEffect, useState } from 'react'
import { listCategories } from '../api/categories'
import {
  listTransactions,
  createTransaction,
  updateTransaction,
  removeTransaction,
} from '../api/transactions'
import { formatCurrency, formatDate, todayISO } from '../lib/format'
import { Field, FormError, SubmitButton } from '../components/form'
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingState,
  Modal,
  SelectField,
} from '../components/ui'

const EMPTY_FILTERS = { from: '', to: '', categoryId: '', type: '' }

function TransactionForm({ categories, transaction, onClose, onSaved }) {
  const isEdit = Boolean(transaction)
  const [form, setForm] = useState(() =>
    transaction
      ? {
          type: transaction.type,
          categoryId: String(transaction.categoryId),
          amount: String(transaction.amount),
          date: transaction.date,
          description: transaction.description || '',
        }
      : { type: 'EXPENSE', categoryId: '', amount: '', date: todayISO(), description: '' },
  )
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  const availableCategories = categories.filter((c) => c.active && c.type === form.type)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function selectType(type) {
    setForm((prev) => {
      const current = categories.find((c) => String(c.id) === prev.categoryId)
      return {
        ...prev,
        type,
        // Reset the category when it does not belong to the new type
        categoryId: current && current.type === type ? prev.categoryId : '',
      }
    })
  }

  function validate() {
    const errors = {}
    const amount = Number(form.amount.replace(',', '.'))
    if (!form.categoryId) errors.categoryId = 'Elige una categoría'
    if (!form.amount.trim() || Number.isNaN(amount) || amount <= 0) {
      errors.amount = 'Introduce un importe mayor que cero'
    }
    if (!form.date) errors.date = 'Introduce la fecha'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)

    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = {
      categoryId: Number(form.categoryId),
      type: form.type,
      amount: Number(form.amount.replace(',', '.')),
      date: form.date,
      description: form.description.trim() || null,
    }

    setSaving(true)
    try {
      if (isEdit) {
        await updateTransaction(transaction.id, payload)
      } else {
        await createTransaction(payload)
      }
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido guardar el movimiento')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={isEdit ? 'Editar movimiento' : 'Nuevo movimiento'} onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <div className="grid grid-cols-2 gap-1 rounded-lg bg-slate-100 p-1" role="group" aria-label="Tipo">
          <button
            type="button"
            onClick={() => selectType('EXPENSE')}
            aria-pressed={form.type === 'EXPENSE'}
            className={[
              'rounded-md px-3 py-2 text-sm font-medium transition-colors',
              form.type === 'EXPENSE'
                ? 'bg-white text-red-600 shadow-sm'
                : 'text-slate-500 hover:text-slate-700',
            ].join(' ')}
          >
            Gasto
          </button>
          <button
            type="button"
            onClick={() => selectType('INCOME')}
            aria-pressed={form.type === 'INCOME'}
            className={[
              'rounded-md px-3 py-2 text-sm font-medium transition-colors',
              form.type === 'INCOME'
                ? 'bg-white text-emerald-600 shadow-sm'
                : 'text-slate-500 hover:text-slate-700',
            ].join(' ')}
          >
            Ingreso
          </button>
        </div>

        <SelectField
          label="Categoría"
          name="categoryId"
          value={form.categoryId}
          onChange={handleChange}
          error={fieldErrors.categoryId}
        >
          <option value="">Selecciona una categoría</option>
          {availableCategories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </SelectField>

        <Field
          label="Importe (€)"
          name="amount"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0"
          placeholder="0,00"
          value={form.amount}
          onChange={handleChange}
          error={fieldErrors.amount}
        />

        <Field
          label="Fecha"
          name="date"
          type="date"
          value={form.date}
          onChange={handleChange}
          error={fieldErrors.date}
        />

        <Field
          label="Descripción (opcional)"
          name="description"
          type="text"
          placeholder="Ej.: compra semanal"
          value={form.description}
          onChange={handleChange}
          error={fieldErrors.description}
        />

        <SubmitButton loading={saving} loadingText="Guardando…">
          {isEdit ? 'Guardar cambios' : 'Crear movimiento'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

export default function TransactionsPage() {
  const [categories, setCategories] = useState([])
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [showFilters, setShowFilters] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  // "loading" is derived: the request in flight has not been marked as loaded
  const [loadedKey, setLoadedKey] = useState(null)
  const requestKey = JSON.stringify([page, filters, reloadKey])
  const loading = loadedKey !== requestKey
  const [formOpen, setFormOpen] = useState(false)
  const [editingTransaction, setEditingTransaction] = useState(null)
  const [toDelete, setToDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    listCategories()
      .then((list) => {
        if (!cancelled) setCategories(list)
      })
      .catch(() => {
        // Filters and form will simply have no categories; the list itself still loads
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const key = JSON.stringify([page, filters, reloadKey])
    listTransactions({ page, ...filters })
      .then((result) => {
        if (!cancelled) {
          setData(result)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar los movimientos')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [page, filters, reloadKey])

  function handleFilterChange(e) {
    const { name, value } = e.target
    setPage(0)
    setFilters((prev) => ({ ...prev, [name]: value }))
  }

  function clearFilters() {
    setPage(0)
    setFilters(EMPTY_FILTERS)
  }

  function openCreate() {
    setEditingTransaction(null)
    setFormOpen(true)
  }

  function openEdit(transaction) {
    setEditingTransaction(transaction)
    setFormOpen(true)
  }

  function handleSaved() {
    setFormOpen(false)
    setEditingTransaction(null)
    setReloadKey((k) => k + 1)
  }

  async function handleDelete() {
    setDeleting(true)
    try {
      await removeTransaction(toDelete.id)
      setToDelete(null)
      // If we removed the last row of a later page, step back one page
      if (data && data.content.length === 1 && page > 0) {
        setPage(page - 1)
      } else {
        setReloadKey((k) => k + 1)
      }
    } catch (err) {
      setToDelete(null)
      setError(err.message || 'No se ha podido eliminar el movimiento')
    } finally {
      setDeleting(false)
    }
  }

  const activeFilterCount = Object.values(filters).filter(Boolean).length
  const hasResults = data && data.content.length > 0

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Movimientos</h1>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Nuevo movimiento
        </button>
      </div>

      <div>
        <button
          type="button"
          onClick={() => setShowFilters((v) => !v)}
          aria-expanded={showFilters}
          className="flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
            <path d="M4 6h16M7 12h10M10 18h4" />
          </svg>
          Filtros
          {activeFilterCount > 0 && (
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-600 text-xs font-semibold text-white">
              {activeFilterCount}
            </span>
          )}
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className={`h-4 w-4 transition-transform ${showFilters ? 'rotate-180' : ''}`}
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>

        {showFilters && (
          <div className="mt-3 rounded-2xl border border-slate-200 bg-white p-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Field
                label="Desde"
                name="from"
                type="date"
                value={filters.from}
                onChange={handleFilterChange}
              />
              <Field
                label="Hasta"
                name="to"
                type="date"
                value={filters.to}
                onChange={handleFilterChange}
              />
              <SelectField
                label="Categoría"
                name="categoryId"
                value={filters.categoryId}
                onChange={handleFilterChange}
              >
                <option value="">Todas</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </SelectField>
              <SelectField label="Tipo" name="type" value={filters.type} onChange={handleFilterChange}>
                <option value="">Todos</option>
                <option value="EXPENSE">Gasto</option>
                <option value="INCOME">Ingreso</option>
              </SelectField>
            </div>
            {activeFilterCount > 0 && (
              <button
                type="button"
                onClick={clearFilters}
                className="mt-3 text-sm font-medium text-emerald-600 hover:text-emerald-700"
              >
                Limpiar filtros
              </button>
            )}
          </div>
        )}
      </div>

      {loading && !data && <LoadingState label="Cargando movimientos…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!error && data && !hasResults && !loading && (
        <EmptyState
          title={
            activeFilterCount > 0
              ? 'Sin resultados con estos filtros'
              : 'Aún no hay movimientos'
          }
          message={
            activeFilterCount > 0
              ? 'Prueba a cambiar o limpiar los filtros.'
              : 'Registra tu primer gasto o ingreso para empezar.'
          }
          action={
            activeFilterCount > 0 ? (
              <button
                type="button"
                onClick={clearFilters}
                className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
              >
                Limpiar filtros
              </button>
            ) : (
              <button
                type="button"
                onClick={openCreate}
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
              >
                Añadir tu primer movimiento
              </button>
            )
          }
        />
      )}

      {!error && hasResults && (
        <>
          <ul
            className={[
              'divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white',
              loading ? 'opacity-60' : '',
            ].join(' ')}
          >
            {data.content.map((t) => (
              <li key={t.id} className="flex items-center gap-3 px-4 py-3">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: t.categoryColor }}
                  aria-hidden="true"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-900">
                    {t.description || t.categoryName}
                  </p>
                  <p className="truncate text-xs text-slate-500">
                    {t.categoryName} · {formatDate(t.date)}
                  </p>
                </div>
                <p
                  className={[
                    'shrink-0 text-sm font-semibold tabular-nums',
                    t.type === 'INCOME' ? 'text-emerald-600' : 'text-slate-900',
                  ].join(' ')}
                >
                  {t.type === 'INCOME' ? '+' : '−'}
                  {formatCurrency(t.amount)}
                </p>
                <div className="flex shrink-0 items-center gap-0.5">
                  <button
                    type="button"
                    onClick={() => openEdit(t)}
                    aria-label="Editar movimiento"
                    className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                      <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    onClick={() => setToDelete(t)}
                    aria-label="Eliminar movimiento"
                    className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                      <path d="M3 6h18" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                  </button>
                </div>
              </li>
            ))}
          </ul>

          <div className="flex items-center justify-between">
            <button
              type="button"
              onClick={() => setPage((p) => p - 1)}
              disabled={page === 0 || loading}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Anterior
            </button>
            <span className="text-sm text-slate-500">
              Página {data.page + 1} de {Math.max(data.totalPages, 1)}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => p + 1)}
              disabled={page >= data.totalPages - 1 || loading}
              className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Siguiente
            </button>
          </div>
        </>
      )}

      {formOpen && (
        <TransactionForm
          categories={categories}
          transaction={editingTransaction}
          onClose={() => {
            setFormOpen(false)
            setEditingTransaction(null)
          }}
          onSaved={handleSaved}
        />
      )}

      {toDelete && (
        <ConfirmDialog
          title="Eliminar movimiento"
          message={`¿Seguro que quieres eliminar este movimiento de ${formatCurrency(toDelete.amount)} (${toDelete.categoryName})? Esta acción no se puede deshacer.`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setToDelete(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}
