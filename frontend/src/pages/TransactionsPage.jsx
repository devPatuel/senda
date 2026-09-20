import { useEffect, useState } from 'react'
import { listCategories } from '../api/categories'
import { listTransactions, removeTransaction } from '../api/transactions'
import { formatCurrency, formatDate } from '../lib/format'
import { Field } from '../components/form'
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingState,
  Notice,
  SelectField,
} from '../components/ui'
import TransactionForm from '../components/TransactionForm'

const EMPTY_FILTERS = { from: '', to: '', categoryId: '', type: '' }

/**
 * A category colour washed down to a row background. The colour itself would
 * fight the text; at 8% it groups the list by category without shouting.
 * Returns undefined for anything that is not a #RRGGBB colour, so the row
 * simply stays white.
 */
function tint(hex) {
  const match = /^#([0-9a-f]{6})$/i.exec(hex || '')
  if (!match) return undefined
  const value = parseInt(match[1], 16)
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, 0.08)`
}

export default function TransactionsPage({ spaceId }) {
  const [categories, setCategories] = useState([])
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [showFilters, setShowFilters] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  // Action (delete) errors live apart from load errors so a failed delete
  // never unmounts the already-loaded list.
  const [actionError, setActionError] = useState(null)
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
    // Include inactive ones: existing transactions may reference soft-deleted
    // categories, which must still be displayed in filters and the edit form
    listCategories({ includeInactive: true, ...(spaceId != null && { spaceId }) })
      .then((list) => {
        if (!cancelled) setCategories(list)
      })
      .catch(() => {
        // Filters and form will simply have no categories; the list itself still loads
      })
    return () => {
      cancelled = true
    }
  }, [spaceId])

  useEffect(() => {
    let cancelled = false
    const key = JSON.stringify([page, filters, reloadKey])
    listTransactions({ page, ...filters, ...(spaceId != null && { spaceId }) })
      .then((result) => {
        if (cancelled) return
        // The page can fall out of range when data changes elsewhere (another
        // tab/device): clamp to the last available page instead of rendering
        // an empty state without pagination controls.
        if (result.content.length === 0 && page > 0 && result.totalElements > 0) {
          setPage(Math.max(result.totalPages - 1, 0))
          return
        }
        setData(result)
        setError(null)
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
  }, [page, filters, reloadKey, spaceId])

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
    setActionError(null)
    setReloadKey((k) => k + 1)
  }

  async function handleDelete() {
    setDeleting(true)
    setActionError(null)
    try {
      await removeTransaction(toDelete.id)
      // If we removed the last row of a later page, step back one page
      if (data && data.content.length === 1 && page > 0) {
        setPage(page - 1)
      } else {
        setReloadKey((k) => k + 1)
      }
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar el movimiento')
    } finally {
      setToDelete(null)
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
                    {c.active ? c.name : `${c.name} (inactiva)`}
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

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

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
              <li
                key={t.id}
                data-testid={`transaction-row-${t.id}`}
                className="flex items-center gap-3 px-4 py-3"
                style={{ backgroundColor: tint(t.categoryColor) }}
              >
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: t.categoryColor }}
                  aria-hidden="true"
                />
                {t.categoryEmoji && (
                  <span className="shrink-0 text-base leading-none" aria-hidden="true">
                    {t.categoryEmoji}
                  </span>
                )}
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
          spaceId={spaceId}
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
