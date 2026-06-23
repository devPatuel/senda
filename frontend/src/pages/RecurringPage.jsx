import { useEffect, useState } from 'react'
import {
  getRecurring,
  createRecurring,
  updateRecurring,
  removeRecurring,
} from '../api/recurring'
import { listCategories } from '../api/categories'
import { formatCurrency, formatDate } from '../lib/format'
import { Field, FormError, SubmitButton } from '../components/form'
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, Modal, Notice, SelectField } from '../components/ui'

const FREQUENCY_LABELS = { MONTHLY: 'Mensual', ANNUAL: 'Anual' }
const MONTHS = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]

function parseDecimal(raw) {
  return Number(String(raw).replace(',', '.'))
}

function RecurringForm({ payment, categories, onClose, onSaved }) {
  const isEdit = Boolean(payment)
  const [form, setForm] = useState(() =>
    payment
      ? {
          name: payment.name,
          amount: String(payment.amount),
          frequency: payment.frequency,
          categoryId: String(payment.categoryId),
          dayOfMonth: String(payment.dayOfMonth),
          month: payment.month ? String(payment.month) : '1',
        }
      : {
          name: '',
          amount: '',
          frequency: 'MONTHLY',
          categoryId: String(categories[0]?.id ?? ''),
          dayOfMonth: '1',
          month: '1',
        },
  )
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function validate() {
    const errors = {}
    if (!form.name.trim()) errors.name = 'Introduce un nombre'
    const amount = parseDecimal(form.amount)
    if (!form.amount.trim() || Number.isNaN(amount) || amount <= 0) {
      errors.amount = 'Importe mayor que 0'
    }
    if (!form.categoryId) errors.categoryId = 'Elige una categoría'
    const day = Number(form.dayOfMonth)
    if (!Number.isInteger(day) || day < 1 || day > 31) errors.dayOfMonth = 'Día entre 1 y 31'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = {
      name: form.name.trim(),
      amount: parseDecimal(form.amount),
      frequency: form.frequency,
      categoryId: Number(form.categoryId),
      dayOfMonth: Number(form.dayOfMonth),
      // month only matters for annual payments
      month: form.frequency === 'ANNUAL' ? Number(form.month) : null,
    }

    setSaving(true)
    try {
      if (isEdit) {
        await updateRecurring(payment.id, payload)
      } else {
        await createRecurring(payload)
      }
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido guardar el pago')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={isEdit ? 'Editar pago recurrente' : 'Nuevo pago recurrente'} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <Field
          label="Nombre"
          name="name"
          type="text"
          placeholder="Ej.: Netflix, seguro del coche"
          value={form.name}
          onChange={handleChange}
          error={fieldErrors.name}
        />

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Importe"
            name="amount"
            type="number"
            inputMode="decimal"
            step="0.01"
            placeholder="0,00"
            value={form.amount}
            onChange={handleChange}
            error={fieldErrors.amount}
          />
          <SelectField label="Frecuencia" name="frequency" value={form.frequency} onChange={handleChange}>
            <option value="MONTHLY">Mensual</option>
            <option value="ANNUAL">Anual</option>
          </SelectField>
        </div>

        <SelectField
          label="Categoría"
          name="categoryId"
          value={form.categoryId}
          onChange={handleChange}
          error={fieldErrors.categoryId}
        >
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </SelectField>

        <div className="grid grid-cols-2 gap-3">
          {form.frequency === 'ANNUAL' && (
            <SelectField label="Mes" name="month" value={form.month} onChange={handleChange}>
              {MONTHS.map((m, i) => (
                <option key={m} value={i + 1}>
                  {m.charAt(0).toUpperCase() + m.slice(1)}
                </option>
              ))}
            </SelectField>
          )}
          <Field
            label="Día del mes"
            name="dayOfMonth"
            type="number"
            inputMode="numeric"
            min="1"
            max="31"
            value={form.dayOfMonth}
            onChange={handleChange}
            error={fieldErrors.dayOfMonth}
          />
        </div>

        <SubmitButton loading={saving} loadingText="Guardando…">
          {isEdit ? 'Guardar cambios' : 'Crear pago'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

export default function RecurringPage() {
  const [payments, setPayments] = useState(null)
  const [categories, setCategories] = useState([])
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const loading = loadedKey !== reloadKey
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [toDelete, setToDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    const key = reloadKey
    Promise.all([getRecurring(), listCategories({ type: 'EXPENSE' })])
      .then(([list, cats]) => {
        if (!cancelled) {
          setPayments(list)
          setCategories(cats)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar los pagos recurrentes')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  function handleSaved() {
    setFormOpen(false)
    setEditing(null)
    setActionError(null)
    setReloadKey((k) => k + 1)
  }

  async function handleDelete() {
    setDeleting(true)
    setActionError(null)
    try {
      await removeRecurring(toDelete.id)
      setReloadKey((k) => k + 1)
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar el pago')
    } finally {
      setToDelete(null)
      setDeleting(false)
    }
  }

  const monthlyTotal = (payments ?? []).reduce((sum, p) => sum + Number(p.monthlyEquivalent), 0)
  const noCategories = !loading && categories.length === 0

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Pagos recurrentes</h1>
        <button
          type="button"
          onClick={() => {
            setEditing(null)
            setFormOpen(true)
          }}
          disabled={noCategories}
          className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:opacity-60"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Nuevo pago
        </button>
      </div>

      {payments && payments.length > 0 && (
        <section className="rounded-2xl border border-slate-200 bg-white p-6 text-center">
          <p className="text-sm text-slate-500">Coste mensual equivalente</p>
          <p className="mt-1 text-4xl font-bold tracking-tight text-slate-900">
            {formatCurrency(monthlyTotal)}
          </p>
          <p className="mt-1 text-xs text-slate-400">Los pagos anuales cuentan como su importe / 12</p>
        </section>
      )}

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {loading && !payments && <LoadingState label="Cargando pagos recurrentes…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {noCategories && !error && (
        <EmptyState
          title="Necesitas una categoría de gasto"
          message="Crea al menos una categoría de gasto para poder registrar pagos recurrentes."
        />
      )}

      {!error && payments && payments.length === 0 && !noCategories && (
        <EmptyState
          title="Aún no hay pagos recurrentes"
          message="Registra tus suscripciones y seguros para anticipar los próximos cobros."
        />
      )}

      {!error && payments && payments.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
          {payments.map((p) => (
            <li key={p.id} className="flex items-center gap-3 px-4 py-3">
              <span
                className="h-5 w-5 shrink-0 rounded-full border border-slate-200"
                style={{ backgroundColor: p.categoryColor }}
                aria-hidden="true"
              />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900">{p.name}</p>
                <p className="truncate text-xs text-slate-500">
                  {FREQUENCY_LABELS[p.frequency]} · {p.categoryName} · próximo {formatDate(p.nextDueDate)}
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-sm font-semibold tabular-nums text-slate-900">
                  {formatCurrency(p.amount)}
                </p>
                {p.frequency === 'ANNUAL' && (
                  <p className="text-xs text-slate-400 tabular-nums">
                    {formatCurrency(p.monthlyEquivalent)}/mes
                  </p>
                )}
              </div>
              <div className="flex shrink-0 items-center gap-0.5">
                <button
                  type="button"
                  onClick={() => {
                    setEditing(p)
                    setFormOpen(true)
                  }}
                  aria-label={`Editar ${p.name}`}
                  className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                    <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                  </svg>
                </button>
                <button
                  type="button"
                  onClick={() => setToDelete(p)}
                  aria-label={`Eliminar ${p.name}`}
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
      )}

      {formOpen && (
        <RecurringForm
          payment={editing}
          categories={categories}
          onClose={() => {
            setFormOpen(false)
            setEditing(null)
          }}
          onSaved={handleSaved}
        />
      )}

      {toDelete && (
        <ConfirmDialog
          title="Eliminar pago recurrente"
          message={`¿Seguro que quieres eliminar «${toDelete.name}»? Esta acción no se puede deshacer.`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setToDelete(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}
