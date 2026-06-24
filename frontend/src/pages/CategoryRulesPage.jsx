import { useEffect, useState } from 'react'
import { listRules, createRule, updateRule, removeRule } from '../api/categoryRules'
import { listCategories } from '../api/categories'
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

function RuleForm({ rule, categories, onClose, onSaved }) {
  const isEdit = Boolean(rule)
  const [form, setForm] = useState(() =>
    rule
      ? { matchText: rule.matchText, categoryId: String(rule.categoryId) }
      : { matchText: '', categoryId: String(categories[0]?.id ?? '') },
  )
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    const errors = {}
    if (!form.matchText.trim()) errors.matchText = 'Introduce un texto'
    if (!form.categoryId) errors.categoryId = 'Elige una categoría'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    const payload = { matchText: form.matchText.trim(), categoryId: Number(form.categoryId) }
    setSaving(true)
    try {
      if (isEdit) await updateRule(rule.id, payload)
      else await createRule(payload)
      onSaved()
    } catch (err) {
      setError(
        err.status === 409
          ? 'Ya existe una regla con ese texto.'
          : err.message || 'No se ha podido guardar la regla',
      )
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={isEdit ? 'Editar regla' : 'Nueva regla'} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />
        <Field
          label="Si la descripción contiene"
          name="matchText"
          type="text"
          placeholder="Ej.: MERCADONA"
          value={form.matchText}
          onChange={handleChange}
          error={fieldErrors.matchText}
        />
        <SelectField
          label="Asignar categoría"
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
        <SubmitButton loading={saving} loadingText="Guardando…">
          {isEdit ? 'Guardar cambios' : 'Crear regla'}
        </SubmitButton>
      </form>
    </Modal>
  )
}

export default function CategoryRulesPage() {
  const [rules, setRules] = useState(null)
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
    Promise.all([listRules(), listCategories({})])
      .then(([rs, cats]) => {
        if (!cancelled) {
          setRules(rs)
          setCategories(cats)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar las reglas')
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
      await removeRule(toDelete.id)
      setReloadKey((k) => k + 1)
    } catch (err) {
      setActionError(err.message || 'No se ha podido eliminar la regla')
    } finally {
      setToDelete(null)
      setDeleting(false)
    }
  }

  const noCategories = !loading && categories.length === 0

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Reglas de categoría</h1>
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
          Nueva regla
        </button>
      </div>

      <p className="text-sm text-slate-500">
        Al importar un extracto, los movimientos cuya descripción contenga el texto de una regla se
        asignan automáticamente a su categoría.
      </p>

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {loading && !rules && <LoadingState label="Cargando reglas…" />}

      {error && !loading && <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />}

      {!error && rules && rules.length === 0 && (
        <EmptyState
          title="Aún no hay reglas"
          message="Crea reglas como «MERCADONA → Comida» para auto-categorizar tus importaciones."
        />
      )}

      {!error && rules && rules.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
          {rules.map((r) => (
            <li key={r.id} className="flex items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900">«{r.matchText}»</p>
              </div>
              <span className="flex shrink-0 items-center gap-1.5 text-sm text-slate-600">
                <span
                  className="h-2.5 w-2.5 rounded-full"
                  style={{ backgroundColor: r.categoryColor }}
                  aria-hidden="true"
                />
                {r.categoryName}
              </span>
              <div className="flex shrink-0 items-center gap-0.5">
                <button
                  type="button"
                  onClick={() => {
                    setEditing(r)
                    setFormOpen(true)
                  }}
                  aria-label={`Editar ${r.matchText}`}
                  className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                    <path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
                  </svg>
                </button>
                <button
                  type="button"
                  onClick={() => setToDelete(r)}
                  aria-label={`Eliminar ${r.matchText}`}
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

      {noCategories && !error && (
        <EmptyState
          title="Necesitas una categoría"
          message="Crea al menos una categoría para poder definir reglas."
        />
      )}

      {formOpen && (
        <RuleForm
          rule={editing}
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
          title="Eliminar regla"
          message={`¿Seguro que quieres eliminar la regla «${toDelete.matchText}»?`}
          confirmLabel="Eliminar"
          loading={deleting}
          onCancel={() => setToDelete(null)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}
