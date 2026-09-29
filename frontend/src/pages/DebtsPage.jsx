import { useEffect, useState } from 'react'
import {
  listDebts,
  createDebt,
  updateDebt,
  removeDebt,
  listPayments,
  addPayment,
  removePayment,
} from '../api/debts'
import { formatCurrency, formatDate, todayISO } from '../lib/format'
import { Field, FormError, SubmitButton } from '../components/form'
import {
  ConfirmDialog,
  ErrorState,
  LoadingState,
  Modal,
  Notice,
} from '../components/ui'

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const DIRECTION_LABELS = {
  THEY_OWE_ME: 'Me deben',
  I_OWE: 'Debo',
}

// Error messages from the backend translated to Spanish
function translateDebtError(message) {
  if (!message) return 'Ha ocurrido un error inesperado'
  if (message.includes('Payment exceeds the pending amount')) {
    return 'El abono supera el importe pendiente'
  }
  if (message.includes('cannot be less than the amount already paid')) {
    return 'El importe original no puede ser inferior a lo ya pagado'
  }
  return message
}

// ---------------------------------------------------------------------------
// DebtForm — create / edit a debt
// ---------------------------------------------------------------------------

function DebtForm({ debt, onClose, onSaved }) {
  const isEdit = Boolean(debt)
  const [form, setForm] = useState(() =>
    debt
      ? {
          direction: debt.direction,
          counterparty: debt.counterparty,
          concept: debt.concept,
          originalAmount: String(debt.originalAmount),
          date: debt.date,
        }
      : {
          direction: 'THEY_OWE_ME',
          counterparty: '',
          concept: '',
          originalAmount: '',
          date: todayISO(),
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
    if (!form.counterparty.trim()) errors.counterparty = 'Introduce el nombre de la persona'
    if (!form.concept.trim()) errors.concept = 'Introduce el concepto'
    const amt = Number(form.originalAmount.replace(',', '.'))
    if (!form.originalAmount.trim() || Number.isNaN(amt) || amt <= 0) {
      errors.originalAmount = 'Introduce un importe válido y positivo'
    }
    if (!form.date) errors.date = 'Introduce una fecha'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)

    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = {
      direction: form.direction,
      counterparty: form.counterparty.trim(),
      concept: form.concept.trim(),
      originalAmount: Number(form.originalAmount.replace(',', '.')),
      date: form.date,
    }

    setSaving(true)
    try {
      if (isEdit) {
        await updateDebt(debt.id, payload)
      } else {
        await createDebt(payload)
      }
      onSaved()
    } catch (err) {
      setError(translateDebtError(err.message))
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={isEdit ? 'Editar deuda' : 'Nueva deuda'} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        {/* Direction toggle */}
        <div>
          <p className="mb-1.5 text-sm font-medium text-slate-700">Dirección</p>
          <div className="flex overflow-hidden rounded-lg border border-slate-300">
            {['THEY_OWE_ME', 'I_OWE'].map((dir) => (
              <button
                key={dir}
                type="button"
                onClick={() => setForm((prev) => ({ ...prev, direction: dir }))}
                className={[
                  'flex-1 py-2.5 text-sm font-medium transition-colors',
                  form.direction === dir
                    ? 'bg-emerald-600 text-white'
                    : 'bg-white text-slate-600 hover:bg-slate-50',
                ].join(' ')}
              >
                {DIRECTION_LABELS[dir]}
              </button>
            ))}
          </div>
        </div>

        <Field
          label="Persona / entidad"
          name="counterparty"
          type="text"
          placeholder="Ej.: Ana, Banco Sabadell"
          value={form.counterparty}
          onChange={handleChange}
          error={fieldErrors.counterparty}
        />

        <Field
          label="Concepto"
          name="concept"
          type="text"
          placeholder="Ej.: Cena del viernes"
          value={form.concept}
          onChange={handleChange}
          error={fieldErrors.concept}
        />

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Importe original"
            name="originalAmount"
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0.01"
            placeholder="0,00"
            value={form.originalAmount}
            onChange={handleChange}
            error={fieldErrors.originalAmount}
          />
          <Field
            label="Fecha"
            name="date"
            type="date"
            value={form.date}
            onChange={handleChange}
            error={fieldErrors.date}
          />
        </div>

        <SubmitButton loading={saving} loadingText="Guardando…">
          {isEdit ? 'Guardar cambios' : 'Crear deuda'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// PaymentForm — add an abono to a debt
// ---------------------------------------------------------------------------

function PaymentForm({ debt, onClose, onSaved }) {
  const [form, setForm] = useState({ amount: '', date: todayISO(), note: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function validate() {
    const errors = {}
    const amt = Number(form.amount.replace(',', '.'))
    if (!form.amount.trim() || Number.isNaN(amt) || amt <= 0) {
      errors.amount = 'Introduce un importe válido y positivo'
    }
    if (!form.date) errors.date = 'Introduce una fecha'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)

    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = {
      amount: Number(form.amount.replace(',', '.')),
      date: form.date,
      note: form.note.trim() || undefined,
    }

    setSaving(true)
    try {
      await addPayment(debt.id, payload)
      onSaved()
    } catch (err) {
      setError(translateDebtError(err.message))
    } finally {
      setSaving(false)
    }
  }

  const pending = Number(debt.pendingAmount)

  return (
    <Modal
      title={`Registrar abono — ${debt.counterparty}`}
      onClose={onClose}
      dismissable={!saving}
    >
      <p className="mb-4 text-sm text-slate-500">
        Pendiente:{' '}
        <span className="font-semibold text-slate-800">{formatCurrency(pending)}</span>
      </p>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Importe abonado"
            name="amount"
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0.01"
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
        </div>

        <Field
          label="Nota (opcional)"
          name="note"
          type="text"
          placeholder="Ej.: Transferencia Bizum"
          value={form.note}
          onChange={handleChange}
          error={fieldErrors.note}
        />

        <SubmitButton loading={saving} loadingText="Guardando…">
          Registrar abono
        </SubmitButton>
      </form>
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// PaymentsPanel — lists and deletes payments for one debt
// ---------------------------------------------------------------------------

function PaymentsPanel({ debt, onClose, onChanged }) {
  const [payments, setPayments] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [toDelete, setToDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)
  const [actionError, setActionError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const loading = loadedKey !== reloadKey

  useEffect(() => {
    let cancelled = false
    const key = reloadKey
    listPayments(debt.id)
      .then((list) => {
        if (!cancelled) {
          setPayments(list)
          setLoadError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err.message || 'No se han podido cargar los abonos')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [debt.id, reloadKey])

  async function handleDelete() {
    setDeleting(true)
    setActionError(null)
    try {
      await removePayment(debt.id, toDelete.id)
      setToDelete(null)
      setReloadKey((k) => k + 1)
      onChanged()
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar el abono')
    } finally {
      setDeleting(false)
    }
  }

  return (
    <Modal title={`Abonos — ${debt.counterparty}`} onClose={onClose}>
      {loading && !payments && <LoadingState label="Cargando abonos…" />}

      {loadError && !loading && (
        <ErrorState message={loadError} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {!loadError && payments && payments.length === 0 && (
        <p className="py-4 text-center text-sm text-slate-500">Aún no hay abonos registrados.</p>
      )}

      {!loadError && payments && payments.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {payments.map((p) => (
            <li key={p.id} className="flex items-center gap-3 py-2.5">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold tabular-nums text-slate-900">
                  {formatCurrency(p.amount)}
                </p>
                <p className="text-xs text-slate-500">
                  {formatDate(p.date)}
                  {p.note ? ` · ${p.note}` : ''}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setToDelete(p)}
                aria-label={`Eliminar abono de ${formatCurrency(p.amount)}`}
                className="shrink-0 rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
              >
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

      {toDelete && (
        <ConfirmDialog
          title="Eliminar abono"
          message={`¿Seguro que quieres eliminar el abono de ${formatCurrency(toDelete.amount)} del ${formatDate(toDelete.date)}? Si la deuda estaba saldada, volverá a estar pendiente.`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setToDelete(null)}
          onConfirm={handleDelete}
        />
      )}
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// ProgressBar
// ---------------------------------------------------------------------------

function ProgressBar({ paid, original }) {
  const pct = original > 0 ? Math.min(100, (paid / original) * 100) : 0
  return (
    <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
      <div
        className="h-full rounded-full bg-emerald-500 transition-all"
        style={{ width: `${pct}%` }}
        aria-label={`${Math.round(pct)}% abonado`}
        role="progressbar"
        aria-valuenow={Math.round(pct)}
        aria-valuemin={0}
        aria-valuemax={100}
      />
    </div>
  )
}

// ---------------------------------------------------------------------------
// DebtCard
// ---------------------------------------------------------------------------

function DebtCard({ debt, onEdit, onPay, onViewPayments, onDelete }) {
  const paid = Number(debt.paidAmount)
  const pending = Number(debt.pendingAmount)
  const original = Number(debt.originalAmount)

  return (
    <li className="rounded-2xl border border-slate-200 bg-white p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="truncate text-sm font-semibold text-slate-900">{debt.counterparty}</p>
            {debt.settled && (
              <span className="shrink-0 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
                Saldada
              </span>
            )}
          </div>
          <p className="mt-0.5 truncate text-xs text-slate-500">{debt.concept}</p>
          <p className="mt-0.5 text-xs text-slate-400">{formatDate(debt.date)}</p>
        </div>

        <div className="shrink-0 text-right">
          <p className="text-xs text-slate-500">Original</p>
          <p className="text-sm font-bold tabular-nums text-slate-800">{formatCurrency(original)}</p>
        </div>
      </div>

      {/* Amounts */}
      <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
        <div className="rounded-lg bg-slate-50 p-2 text-center">
          <p className="text-slate-500">Pagado</p>
          <p className="font-semibold tabular-nums text-slate-800">{formatCurrency(paid)}</p>
        </div>
        <div className="rounded-lg bg-slate-50 p-2 text-center">
          <p className="text-slate-500">Pendiente</p>
          <p className={['font-semibold tabular-nums', pending > 0 ? 'text-amber-600' : 'text-emerald-600'].join(' ')}>
            {formatCurrency(pending)}
          </p>
        </div>
      </div>

      <ProgressBar paid={paid} original={original} />

      {/* Actions */}
      <div className="mt-3 flex items-center gap-1">
        {!debt.settled && (
          <button
            type="button"
            onClick={() => onPay(debt)}
            className="flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-emerald-700"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-3.5 w-3.5" aria-hidden="true">
              <path d="M12 5v14M5 12h14" />
            </svg>
            Abonar
          </button>
        )}
        <button
          type="button"
          onClick={() => onViewPayments(debt)}
          aria-label={`Ver abonos de ${debt.counterparty}`}
          className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
            <path d="M9 11l3 3L22 4" />
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
          </svg>
        </button>
        <button
          type="button"
          onClick={() => onEdit(debt)}
          aria-label={`Editar deuda con ${debt.counterparty}`}
          className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
            <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
          </svg>
        </button>
        <button
          type="button"
          onClick={() => onDelete(debt)}
          aria-label={`Eliminar deuda con ${debt.counterparty}`}
          className="ml-auto rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
            <path d="M3 6h18" />
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
            <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
        </button>
      </div>
    </li>
  )
}

// ---------------------------------------------------------------------------
// DebtSection — one column (THEY_OWE_ME or I_OWE)
// ---------------------------------------------------------------------------

function DebtSection({ title, debts, onEdit, onPay, onViewPayments, onDelete }) {
  if (debts.length === 0) {
    return (
      <section>
        <h2 className="mb-3 text-base font-semibold text-slate-700">{title}</h2>
        <p className="rounded-2xl border border-dashed border-slate-300 bg-white px-4 py-8 text-center text-sm text-slate-500">
          No hay deudas en esta sección.
        </p>
      </section>
    )
  }

  return (
    <section>
      <h2 className="mb-3 text-base font-semibold text-slate-700">
        {title}
        <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
          {debts.length}
        </span>
      </h2>
      <ul className="space-y-3">
        {debts.map((d) => (
          <DebtCard
            key={d.id}
            debt={d}
            onEdit={onEdit}
            onPay={onPay}
            onViewPayments={onViewPayments}
            onDelete={onDelete}
          />
        ))}
      </ul>
    </section>
  )
}

// ---------------------------------------------------------------------------
// DebtsPage — main export
// ---------------------------------------------------------------------------

export default function DebtsPage() {
  const [debts, setDebts] = useState(null)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const loading = loadedKey !== reloadKey

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [paying, setPaying] = useState(null)
  const [viewingPayments, setViewingPayments] = useState(null)
  const [toDelete, setToDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    const key = reloadKey
    listDebts()
      .then((list) => {
        if (!cancelled) {
          setDebts(list)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar las deudas')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  function reload() {
    setReloadKey((k) => k + 1)
  }

  function handleSaved() {
    setFormOpen(false)
    setEditing(null)
    setActionError(null)
    reload()
  }

  function handlePaymentSaved() {
    setPaying(null)
    reload()
  }

  async function handleDelete() {
    setDeleting(true)
    setActionError(null)
    try {
      await removeDebt(toDelete.id)
      reload()
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar la deuda')
    } finally {
      setToDelete(null)
      setDeleting(false)
    }
  }

  const theyOweMe = (debts ?? []).filter((d) => d.direction === 'THEY_OWE_ME')
  const iOwe = (debts ?? []).filter((d) => d.direction === 'I_OWE')

  // Summary totals
  const totalOwedToMe = theyOweMe.reduce((s, d) => s + Number(d.pendingAmount), 0)
  const totalIOwe = iOwe.reduce((s, d) => s + Number(d.pendingAmount), 0)

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Deudas</h1>
        <button
          type="button"
          onClick={() => {
            setEditing(null)
            setFormOpen(true)
          }}
          className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Nueva deuda
        </button>
      </div>

      {/* Summary cards */}
      {debts && (
        <div className="grid grid-cols-2 gap-3">
          <section className="rounded-2xl border border-emerald-100 bg-emerald-50 p-4 text-center">
            <p className="text-xs font-medium text-emerald-700">Me deben (pendiente)</p>
            <p className="mt-1 text-2xl font-bold tabular-nums tracking-tight text-emerald-700">
              {formatCurrency(totalOwedToMe)}
            </p>
          </section>
          <section className="rounded-2xl border border-amber-100 bg-amber-50 p-4 text-center">
            <p className="text-xs font-medium text-amber-700">Debo (pendiente)</p>
            <p className="mt-1 text-2xl font-bold tabular-nums tracking-tight text-amber-700">
              {formatCurrency(totalIOwe)}
            </p>
          </section>
        </div>
      )}

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {loading && !debts && <LoadingState label="Cargando deudas…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={reload} />
      )}

      {!error && debts && (
        <div className="grid gap-6 sm:grid-cols-2">
          <DebtSection
            title="Me deben"
            debts={theyOweMe}
            onEdit={(d) => { setEditing(d); setFormOpen(true) }}
            onPay={setPaying}
            onViewPayments={setViewingPayments}
            onDelete={setToDelete}
          />
          <DebtSection
            title="Debo"
            debts={iOwe}
            onEdit={(d) => { setEditing(d); setFormOpen(true) }}
            onPay={setPaying}
            onViewPayments={setViewingPayments}
            onDelete={setToDelete}
          />
        </div>
      )}

      {/* Modals */}
      {formOpen && (
        <DebtForm
          debt={editing}
          onClose={() => { setFormOpen(false); setEditing(null) }}
          onSaved={handleSaved}
        />
      )}

      {paying && (
        <PaymentForm
          debt={paying}
          onClose={() => setPaying(null)}
          onSaved={handlePaymentSaved}
        />
      )}

      {viewingPayments && (
        <PaymentsPanel
          debt={viewingPayments}
          onClose={() => setViewingPayments(null)}
          onChanged={reload}
        />
      )}

      {toDelete && (
        <ConfirmDialog
          title="Eliminar deuda"
          message={`¿Seguro que quieres eliminar la deuda con «${toDelete.counterparty}»? Se eliminarán también todos sus abonos y esta acción no se puede deshacer.`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setToDelete(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}
