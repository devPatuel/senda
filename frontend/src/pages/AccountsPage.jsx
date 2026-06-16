import { useEffect, useState } from 'react'
import {
  listAccounts,
  createAccount,
  updateAccount,
  removeAccount,
} from '../api/accounts'
import { formatCurrency } from '../lib/format'
import { Field, FormError, SubmitButton } from '../components/form'
import { ConfirmDialog, ErrorState, LoadingState, Modal, Notice, SelectField } from '../components/ui'

const TYPE_LABELS = { BANK: 'Banco', CASH: 'Efectivo' }

function AccountForm({ account, onClose, onSaved }) {
  const isEdit = Boolean(account)
  const [form, setForm] = useState(() =>
    account
      ? {
          name: account.name,
          type: account.type,
          balance: String(account.balance),
          currency: account.currency,
        }
      : { name: '', type: 'BANK', balance: '', currency: 'EUR' },
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
    const balance = Number(form.balance.replace(',', '.'))
    if (!form.name.trim()) errors.name = 'Introduce un nombre'
    if (!form.balance.trim() || Number.isNaN(balance)) {
      errors.balance = 'Introduce un saldo válido'
    }
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
      type: form.type,
      balance: Number(form.balance.replace(',', '.')),
      currency: form.currency.trim().toUpperCase() || 'EUR',
    }

    setSaving(true)
    try {
      if (isEdit) {
        await updateAccount(account.id, { ...payload, archived: account.archived })
      } else {
        await createAccount(payload)
      }
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido guardar la cuenta')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={isEdit ? 'Editar cuenta' : 'Nueva cuenta'} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <Field
          label="Nombre"
          name="name"
          type="text"
          placeholder="Ej.: cuenta nómina"
          value={form.name}
          onChange={handleChange}
          error={fieldErrors.name}
        />

        <SelectField label="Tipo" name="type" value={form.type} onChange={handleChange}>
          <option value="BANK">Banco</option>
          <option value="CASH">Efectivo</option>
        </SelectField>

        <div className="grid grid-cols-3 gap-3">
          <div className="col-span-2">
            <Field
              label="Saldo"
              name="balance"
              type="number"
              inputMode="decimal"
              step="0.01"
              placeholder="0,00"
              value={form.balance}
              onChange={handleChange}
              error={fieldErrors.balance}
            />
          </div>
          <Field
            label="Moneda"
            name="currency"
            type="text"
            maxLength={3}
            placeholder="EUR"
            value={form.currency}
            onChange={handleChange}
            error={fieldErrors.currency}
          />
        </div>

        <SubmitButton loading={saving} loadingText="Guardando…">
          {isEdit ? 'Guardar cambios' : 'Crear cuenta'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

export default function AccountsPage() {
  const [accounts, setAccounts] = useState(null)
  const [showArchived, setShowArchived] = useState(false)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const requestKey = `${showArchived}-${reloadKey}`
  const loading = loadedKey !== requestKey
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [toDelete, setToDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    const key = `${showArchived}-${reloadKey}`
    listAccounts(showArchived ? { includeArchived: true } : {})
      .then((list) => {
        if (!cancelled) {
          setAccounts(list)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar las cuentas')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [showArchived, reloadKey])

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
      await removeAccount(toDelete.id)
      setReloadKey((k) => k + 1)
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar la cuenta')
    } finally {
      setToDelete(null)
      setDeleting(false)
    }
  }

  async function setArchived(account, archived) {
    setActionError(null)
    try {
      await updateAccount(account.id, {
        name: account.name,
        type: account.type,
        balance: account.balance,
        currency: account.currency,
        archived,
      })
      setReloadKey((k) => k + 1)
    } catch (err) {
      setActionError(err.message || 'No se ha podido actualizar la cuenta')
    }
  }

  // Total of non-archived accounts only (matches the backend /balance endpoint)
  const total = (accounts ?? [])
    .filter((a) => !a.archived)
    .reduce((sum, a) => sum + Number(a.balance), 0)

  const sorted = (accounts ?? [])
    .slice()
    .sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name, 'es'))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Cuentas</h1>
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
          Nueva cuenta
        </button>
      </div>

      {accounts && (
        <section className="rounded-2xl border border-slate-200 bg-white p-6 text-center">
          <p className="text-sm text-slate-500">Saldo total</p>
          <p className="mt-1 text-4xl font-bold tracking-tight text-emerald-600">
            {formatCurrency(total)}
          </p>
        </section>
      )}

      <label className="flex w-fit cursor-pointer items-center gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          checked={showArchived}
          onChange={(e) => setShowArchived(e.target.checked)}
          className="h-4 w-4 rounded border-slate-300 accent-emerald-600"
        />
        Mostrar archivadas
      </label>

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {loading && !accounts && <LoadingState label="Cargando cuentas…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!error && accounts && accounts.length === 0 && (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-slate-300 bg-white px-4 py-12 text-center">
          <p className="text-sm font-semibold text-slate-700">Aún no hay cuentas</p>
          <p className="max-w-sm text-sm text-slate-500">
            Añade tu banco o tu efectivo para ver tu dinero líquido.
          </p>
        </div>
      )}

      {!error && accounts && accounts.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
          {sorted.map((a) => (
            <li key={a.id} className="flex items-center gap-3 px-4 py-3">
              <span
                className={[
                  'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg',
                  a.type === 'BANK' ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600',
                ].join(' ')}
                aria-hidden="true"
              >
                {a.type === 'BANK' ? (
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5">
                    <path d="M3 21h18" />
                    <path d="M5 21V9l7-5 7 5v12" />
                    <path d="M9 21v-6h6v6" />
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5">
                    <rect x="2" y="6" width="20" height="12" rx="2" />
                    <circle cx="12" cy="12" r="2.5" />
                  </svg>
                )}
              </span>
              <div className="min-w-0 flex-1">
                <p
                  className={[
                    'truncate text-sm font-medium',
                    a.archived ? 'text-slate-400 line-through' : 'text-slate-900',
                  ].join(' ')}
                >
                  {a.name}
                </p>
                <p className="truncate text-xs text-slate-500">
                  {TYPE_LABELS[a.type]}
                  {a.currency !== 'EUR' ? ` · ${a.currency}` : ''}
                </p>
              </div>
              {a.archived && (
                <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                  Archivada
                </span>
              )}
              <p className="shrink-0 text-sm font-semibold tabular-nums text-slate-900">
                {formatCurrency(a.balance)}
              </p>
              <div className="flex shrink-0 items-center gap-0.5">
                {a.archived ? (
                  <button
                    type="button"
                    onClick={() => setArchived(a, false)}
                    aria-label={`Restaurar ${a.name}`}
                    className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
                  >
                    Restaurar
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      onClick={() => {
                        setEditing(a)
                        setFormOpen(true)
                      }}
                      aria-label={`Editar ${a.name}`}
                      className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                        <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      onClick={() => setArchived(a, true)}
                      aria-label={`Archivar ${a.name}`}
                      className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                        <rect x="3" y="4" width="18" height="4" rx="1" />
                        <path d="M5 8v11a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8" />
                        <path d="M10 12h4" />
                      </svg>
                    </button>
                    <button
                      type="button"
                      onClick={() => setToDelete(a)}
                      aria-label={`Eliminar ${a.name}`}
                      className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                        <path d="M3 6h18" />
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                        <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                      </svg>
                    </button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {formOpen && (
        <AccountForm
          account={editing}
          onClose={() => {
            setFormOpen(false)
            setEditing(null)
          }}
          onSaved={handleSaved}
        />
      )}

      {toDelete && (
        <ConfirmDialog
          title="Eliminar cuenta"
          message={`¿Seguro que quieres eliminar «${toDelete.name}»? Esta acción no se puede deshacer. Si solo quieres ocultarla, archívala en su lugar.`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setToDelete(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}
