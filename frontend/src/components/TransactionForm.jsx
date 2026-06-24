// Create/edit form for a transaction, shared by the Movimientos page and the
// quick-add flow on Inicio. Extracted so both entry points stay in sync.
import { useState } from 'react'
import { createTransaction, updateTransaction } from '../api/transactions'
import { todayISO } from '../lib/format'
import { Field, FormError, SubmitButton } from './form'
import { Modal, SelectField } from './ui'

export default function TransactionForm({ categories, transaction, onClose, onSaved }) {
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
  // The transaction's category may have been deactivated (soft delete): keep it
  // visible as the selected option, but disabled so it cannot be re-picked.
  const inactiveCurrentCategory = isEdit
    ? categories.find(
        (c) => c.id === transaction.categoryId && !c.active && c.type === form.type,
      )
    : null

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
      // The backend's 409 ("Category is inactive") is in English: translate it
      setError(
        err.status === 409
          ? 'La categoría seleccionada está desactivada. Elige una categoría activa.'
          : err.message || 'No se ha podido guardar el movimiento',
      )
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={isEdit ? 'Editar movimiento' : 'Nuevo movimiento'}
      onClose={onClose}
      dismissable={!saving}
    >
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
          {inactiveCurrentCategory && (
            <option value={inactiveCurrentCategory.id} disabled>
              {inactiveCurrentCategory.name} (inactiva)
            </option>
          )}
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
