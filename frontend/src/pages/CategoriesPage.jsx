import { useEffect, useState } from 'react'
import {
  listCategories,
  createCategory,
  updateCategory,
  removeCategory,
  getBudget,
  assignToCategory,
  setCategoryTarget,
} from '../api/categories'
import { formatCurrency } from '../lib/format'
import { Field, FormError, SubmitButton } from '../components/form'
import { ConfirmDialog, ErrorState, LoadingState, Modal, Notice } from '../components/ui'

// Accepts comma or dot as the decimal separator and a leading minus.
function parseDecimal(raw) {
  return Number(String(raw).replace(',', '.'))
}

const PALETTE = [
  '#ef4444',
  '#f97316',
  '#f59e0b',
  '#84cc16',
  '#10b981',
  '#06b6d4',
  '#3b82f6',
  '#8b5cf6',
  '#ec4899',
  '#64748b',
]

const GROUPS = [
  { type: 'EXPENSE', title: 'Gastos' },
  { type: 'INCOME', title: 'Ingresos' },
]

function ColorPicker({ value, onChange }) {
  return (
    <div>
      <span className="mb-1.5 block text-sm font-medium text-slate-700">Color</span>
      <div className="flex flex-wrap items-center gap-2">
        {PALETTE.map((color) => (
          <button
            key={color}
            type="button"
            onClick={() => onChange(color)}
            aria-label={`Color ${color}`}
            aria-pressed={value === color}
            className={[
              'h-8 w-8 rounded-full border transition-transform',
              value === color
                ? 'scale-110 border-slate-400 ring-2 ring-slate-300 ring-offset-1'
                : 'border-slate-200 hover:scale-105',
            ].join(' ')}
            style={{ backgroundColor: color }}
          />
        ))}
        <input
          type="color"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-label="Color personalizado"
          className="h-8 w-8 cursor-pointer rounded-full border border-slate-200 bg-white p-0.5"
        />
      </div>
    </div>
  )
}

function CategoryForm({ category, onClose, onSaved }) {
  const isEdit = Boolean(category)
  const [form, setForm] = useState(() =>
    category
      ? { name: category.name, type: category.type, color: category.color }
      : { name: '', type: 'EXPENSE', color: PALETTE[4] },
  )
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)

    const name = form.name.trim()
    if (!name) {
      setFieldErrors({ name: 'Introduce un nombre' })
      return
    }
    setFieldErrors({})

    setSaving(true)
    try {
      if (isEdit) {
        await updateCategory(category.id, {
          name,
          type: category.type,
          color: form.color,
          active: category.active,
        })
      } else {
        await createCategory({ name, type: form.type, color: form.color })
      }
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido guardar la categoría')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={isEdit ? 'Editar categoría' : 'Nueva categoría'}
      onClose={onClose}
      dismissable={!saving}
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <Field
          label="Nombre"
          name="name"
          type="text"
          placeholder="Ej.: suscripciones"
          value={form.name}
          onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
          error={fieldErrors.name}
        />

        {!isEdit && (
          <div className="grid grid-cols-2 gap-1 rounded-lg bg-slate-100 p-1" role="group" aria-label="Tipo">
            <button
              type="button"
              onClick={() => setForm((prev) => ({ ...prev, type: 'EXPENSE' }))}
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
              onClick={() => setForm((prev) => ({ ...prev, type: 'INCOME' }))}
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
        )}

        <ColorPicker
          value={form.color}
          onChange={(color) => setForm((prev) => ({ ...prev, color }))}
        />

        <SubmitButton loading={saving} loadingText="Guardando…">
          {isEdit ? 'Guardar cambios' : 'Crear categoría'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

// Move money into (or out of, with a negative amount) a category envelope.
function AssignForm({ category, currentBalance, onClose, onSaved }) {
  const [value, setValue] = useState('')
  const [fieldError, setFieldError] = useState(null)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const amount = parseDecimal(value)
    if (!value.trim() || Number.isNaN(amount) || amount === 0) {
      setFieldError('Introduce un importe distinto de 0')
      return
    }
    setFieldError(null)
    setSaving(true)
    try {
      const budget = await assignToCategory(category.id, amount)
      onSaved(budget)
    } catch (err) {
      setError(err.message || 'No se ha podido asignar')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={`Asignar a · ${category.name}`} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <p className="text-sm text-slate-500">
          Saldo actual: <span className="font-medium text-slate-700">{formatCurrency(currentBalance)}</span>.
          Usa un importe negativo para sacar dinero de esta categoría.
        </p>
        <Field
          label="Importe a asignar"
          name="amount"
          type="text"
          inputMode="decimal"
          placeholder="0,00"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          error={fieldError}
        />
        <div className="flex flex-wrap gap-2">
          {['10', '50', '100', '-10'].map((preset) => (
            <button
              key={preset}
              type="button"
              onClick={() => setValue(preset)}
              className="rounded-lg border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
            >
              {preset.startsWith('-') ? preset : `+${preset}`}
            </button>
          ))}
        </div>
        <SubmitButton loading={saving} loadingText="Asignando…">
          Asignar
        </SubmitButton>
      </form>
    </Modal>
  )
}

// Set or clear an expense category's funding target.
function TargetForm({ category, currentTarget, onClose, onSaved }) {
  const [value, setValue] = useState(currentTarget != null ? String(currentTarget) : '')
  const [fieldError, setFieldError] = useState(null)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const trimmed = value.trim()
    // An empty value clears the target (sends null)
    let targetAmount = null
    if (trimmed !== '') {
      const amount = parseDecimal(trimmed)
      if (Number.isNaN(amount) || amount < 0) {
        setFieldError('Introduce un importe mayor o igual que 0')
        return
      }
      targetAmount = amount
    }
    setFieldError(null)
    setSaving(true)
    try {
      const budget = await setCategoryTarget(category.id, targetAmount)
      onSaved(budget)
    } catch (err) {
      setError(err.message || 'No se ha podido guardar el objetivo')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={`Objetivo de · ${category.name}`} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <p className="text-sm text-slate-500">
          Importe que quieres mantener asignado en esta categoría. Déjalo vacío para quitar el objetivo.
        </p>
        <Field
          label="Importe objetivo"
          name="targetAmount"
          type="text"
          inputMode="decimal"
          placeholder="0,00"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          error={fieldError}
        />
        <SubmitButton loading={saving} loadingText="Guardando…">
          Guardar objetivo
        </SubmitButton>
      </form>
    </Modal>
  )
}

export default function CategoriesPage() {
  const [categories, setCategories] = useState(null)
  const [budget, setBudget] = useState(null)
  const [assigning, setAssigning] = useState(null)
  const [targeting, setTargeting] = useState(null)
  const [showInactive, setShowInactive] = useState(false)
  const [error, setError] = useState(null)
  // Action (delete/reactivate) errors live apart from load errors so a failed
  // action never unmounts the already-loaded list.
  const [actionError, setActionError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  // "loading" is derived: the request in flight has not been marked as loaded
  const [loadedKey, setLoadedKey] = useState(null)
  const requestKey = `${showInactive}-${reloadKey}`
  const loading = loadedKey !== requestKey
  const [formOpen, setFormOpen] = useState(false)
  const [editingCategory, setEditingCategory] = useState(null)
  const [toDelete, setToDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    let cancelled = false
    const key = `${showInactive}-${reloadKey}`
    Promise.all([
      listCategories(showInactive ? { includeInactive: true } : {}),
      getBudget(),
    ])
      .then(([list, budgetData]) => {
        if (!cancelled) {
          setCategories(list)
          setBudget(budgetData)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar las categorías')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [showInactive, reloadKey])

  // Lookup of budget data per expense category id (balance, spend, target).
  const budgetById = new Map((budget?.categories ?? []).map((c) => [c.id, c]))

  function handleSaved() {
    setFormOpen(false)
    setEditingCategory(null)
    setNotice(null)
    setActionError(null)
    setReloadKey((k) => k + 1)
  }

  async function handleDelete() {
    const category = toDelete
    setDeleting(true)
    setNotice(null)
    setActionError(null)
    try {
      await removeCategory(category.id)
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar la categoría')
      return
    } finally {
      setToDelete(null)
      setDeleting(false)
    }
    // The delete succeeded: refresh through the keyed effect, which already
    // handles errors and cancellation (a manual setCategories could race with it)
    setReloadKey((k) => k + 1)
    // Best-effort check to tell apart "deleted" from "deactivated" (the backend
    // deactivates instead of deleting when the category has transactions)
    try {
      const all = await listCategories({ includeInactive: true })
      const remaining = all.some((c) => c.id === category.id)
      setNotice(
        remaining
          ? `«${category.name}» tenía movimientos, así que se ha desactivado en lugar de eliminarse.`
          : `Categoría «${category.name}» eliminada.`,
      )
    } catch {
      // The delete already happened; do not report it as failed
      setNotice(`Categoría «${category.name}» eliminada o desactivada.`)
    }
  }

  async function handleReactivate(category) {
    setNotice(null)
    setActionError(null)
    try {
      await updateCategory(category.id, {
        name: category.name,
        type: category.type,
        color: category.color,
        active: true,
      })
      setReloadKey((k) => k + 1)
    } catch (err) {
      setActionError(err.message || 'No se ha podido reactivar la categoría')
    }
  }

  function groupItems(type) {
    return (categories ?? [])
      .filter((c) => c.type === type)
      .sort((a, b) => Number(b.active) - Number(a.active) || a.name.localeCompare(b.name, 'es'))
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Categorías</h1>
        <button
          type="button"
          onClick={() => {
            setEditingCategory(null)
            setFormOpen(true)
          }}
          className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Nueva categoría
        </button>
      </div>

      <label className="flex w-fit cursor-pointer items-center gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          checked={showInactive}
          onChange={(e) => setShowInactive(e.target.checked)}
          className="h-4 w-4 rounded border-slate-300 accent-emerald-600"
        />
        Mostrar inactivas
      </label>

      {notice && <Notice onClose={() => setNotice(null)}>{notice}</Notice>}

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {loading && !categories && <LoadingState label="Cargando categorías…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!error && budget && (
        <section className="grid grid-cols-3 gap-px overflow-hidden rounded-2xl border border-slate-200 bg-slate-200">
          <div className="bg-white px-4 py-3 text-center">
            <p className="text-xs text-slate-500">Total en cuentas</p>
            <p className="mt-1 text-lg font-semibold tabular-nums text-slate-900">
              {formatCurrency(budget.totalAccounts)}
            </p>
          </div>
          <div className="bg-white px-4 py-3 text-center">
            <p className="text-xs text-slate-500">Asignado</p>
            <p className="mt-1 text-lg font-semibold tabular-nums text-slate-900">
              {formatCurrency(budget.totalAssigned)}
            </p>
          </div>
          <div className="bg-white px-4 py-3 text-center">
            <p className="text-xs text-slate-500">Por asignar</p>
            <p
              className={[
                'mt-1 text-lg font-semibold tabular-nums',
                Number(budget.toAssign) < 0 ? 'text-red-600' : 'text-emerald-600',
              ].join(' ')}
            >
              {formatCurrency(budget.toAssign)}
            </p>
          </div>
        </section>
      )}

      {!error && categories && (
        <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
          {GROUPS.map((group) => {
            const items = groupItems(group.type)
            return (
              <section key={group.type} className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
                <h2 className="border-b border-slate-100 px-4 py-3 text-sm font-semibold text-slate-700">
                  {group.title}
                </h2>
                {items.length === 0 ? (
                  <p className="px-4 py-6 text-sm text-slate-500">
                    No hay categorías de este tipo.
                  </p>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {items.map((c) => {
                      const b = group.type === 'EXPENSE' ? budgetById.get(c.id) : null
                      return (
                      <li key={c.id} className="px-4 py-3">
                        <div className="flex items-center gap-3">
                        <span
                          className={[
                            'h-5 w-5 shrink-0 rounded-full border border-slate-200',
                            c.active ? '' : 'opacity-40',
                          ].join(' ')}
                          style={{ backgroundColor: c.color }}
                          aria-hidden="true"
                        />
                        <span
                          className={[
                            'min-w-0 flex-1 truncate text-sm font-medium',
                            c.active ? 'text-slate-900' : 'text-slate-400 line-through',
                          ].join(' ')}
                        >
                          {c.name}
                        </span>
                        {!c.active && (
                          <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                            Inactiva
                          </span>
                        )}
                        {b && c.active && (
                          <div className="shrink-0 text-right">
                            <p
                              className={[
                                'text-sm font-semibold tabular-nums',
                                Number(b.balance) < 0 ? 'text-red-600' : 'text-slate-900',
                              ].join(' ')}
                            >
                              {formatCurrency(b.balance)}
                            </p>
                            <p className="text-xs text-slate-400 tabular-nums">
                              gastado {formatCurrency(b.spentThisMonth)}
                            </p>
                          </div>
                        )}
                        {c.active ? (
                          <div className="flex shrink-0 items-center gap-0.5">
                            {b && (
                              <button
                                type="button"
                                onClick={() => setAssigning(c)}
                                className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
                              >
                                Asignar
                              </button>
                            )}
                            {b && (
                              <button
                                type="button"
                                onClick={() => setTargeting(c)}
                                className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
                              >
                                Objetivo
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={() => {
                                setEditingCategory(c)
                                setFormOpen(true)
                              }}
                              aria-label={`Editar ${c.name}`}
                              className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                                <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                              </svg>
                            </button>
                            <button
                              type="button"
                              onClick={() => setToDelete(c)}
                              aria-label={`Eliminar ${c.name}`}
                              className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                                <path d="M3 6h18" />
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                                <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                              </svg>
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleReactivate(c)}
                            aria-label={`Reactivar ${c.name}`}
                            className="shrink-0 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
                          >
                            Reactivar
                          </button>
                        )}
                        </div>
                        {b && c.active && b.targetAmount != null && Number(b.targetAmount) > 0 && (
                          <div className="mt-2 space-y-1">
                            <div className="flex items-center justify-between text-xs text-slate-400">
                              <span>objetivo {formatCurrency(b.targetAmount)}</span>
                              <span className="tabular-nums">
                                {Math.round(
                                  Math.min(Math.max((Number(b.balance) / Number(b.targetAmount)) * 100, 0), 100),
                                )}
                                %
                              </span>
                            </div>
                            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                              <div
                                className="h-full rounded-full"
                                style={{
                                  width: `${Math.min(Math.max((Number(b.balance) / Number(b.targetAmount)) * 100, 0), 100)}%`,
                                  backgroundColor: c.color,
                                }}
                              />
                            </div>
                          </div>
                        )}
                      </li>
                      )
                    })}
                  </ul>
                )}
              </section>
            )
          })}
        </div>
      )}

      {formOpen && (
        <CategoryForm
          category={editingCategory}
          onClose={() => {
            setFormOpen(false)
            setEditingCategory(null)
          }}
          onSaved={handleSaved}
        />
      )}

      {toDelete && (
        <ConfirmDialog
          title="Eliminar categoría"
          message={`¿Seguro que quieres eliminar «${toDelete.name}»? Si tiene movimientos asociados se desactivará en lugar de eliminarse.`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setToDelete(null)}
          onConfirm={handleDelete}
        />
      )}

      {assigning && (
        <AssignForm
          category={assigning}
          currentBalance={budgetById.get(assigning.id)?.balance ?? 0}
          onClose={() => setAssigning(null)}
          onSaved={(updatedBudget) => {
            setBudget(updatedBudget)
            setAssigning(null)
            setNotice(`Saldo de «${assigning.name}» actualizado.`)
            setActionError(null)
          }}
        />
      )}

      {targeting && (
        <TargetForm
          category={targeting}
          currentTarget={budgetById.get(targeting.id)?.targetAmount ?? null}
          onClose={() => setTargeting(null)}
          onSaved={(updatedBudget) => {
            setBudget(updatedBudget)
            setTargeting(null)
            setNotice(`Objetivo de «${targeting.name}» actualizado.`)
            setActionError(null)
          }}
        />
      )}
    </div>
  )
}
