import { useEffect, useState } from 'react'
import { listUsers, resetUserPassword } from '../api/admin'
import { useAuth } from '../auth/useAuth'
import { Field, FormError, SubmitButton } from '../components/form'
import { ErrorState, LoadingState, Modal, Notice } from '../components/ui'
import { formatDate } from '../lib/format'

function ResetPasswordModal({ target, onClose, onDone }) {
  const [password, setPassword] = useState('')
  const [fieldError, setFieldError] = useState(null)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    if (password.length < 8) {
      setFieldError('Debe tener al menos 8 caracteres')
      return
    }
    setFieldError(null)
    setSaving(true)
    try {
      await resetUserPassword(target.id, password)
      onDone()
    } catch (err) {
      setError(err.message || 'No se ha podido cambiar la contraseña')
      setSaving(false)
    }
  }

  return (
    <Modal title={`Cambiar la contraseña de ${target.name}`} onClose={onClose} dismissable={!saving}>
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <p className="text-sm text-slate-500">
          Se cerrarán sus sesiones abiertas. Pásale la nueva y podrá cambiarla después desde Contraseña.
        </p>
        <FormError message={error} />
        <Field
          label="Nueva contraseña"
          name="newPassword"
          type="password"
          autoComplete="new-password"
          value={password}
          error={fieldError}
          onChange={(e) => setPassword(e.target.value)}
        />
        <SubmitButton loading={saving} loadingText="Guardando…">
          Cambiar contraseña
        </SubmitButton>
      </form>
    </Modal>
  )
}

export default function AdminPage() {
  const { user } = useAuth()
  const [users, setUsers] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [target, setTarget] = useState(null)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    let cancelled = false
    listUsers()
      .then((result) => {
        if (!cancelled) {
          setUsers(result)
          setError(null)
        }
      })
      .catch((err) => {
        if (cancelled) return
        setError(err.status === 403 ? 'Esta pantalla es solo para administradores' : err.message || 'No se han podido cargar las cuentas')
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Administración</h1>
      <p className="text-sm text-slate-500">Cuentas creadas. Desde aquí no se ven los datos de nadie.</p>

      {notice && (
        <Notice tone="success" onClose={() => setNotice(null)}>
          {notice}
        </Notice>
      )}

      {error && <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />}
      {!error && !users && <LoadingState label="Cargando las cuentas…" />}

      {!error && users && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
          {users.map((u) => (
            <li key={u.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-slate-900">
                  {u.name}
                  {u.admin && <span className="ml-2 text-xs font-normal text-emerald-600">Admin</span>}
                </p>
                <p className="truncate text-xs text-slate-500">{u.email}</p>
                <p className="text-xs text-slate-400">Alta: {formatDate(u.createdAt.slice(0, 10))}</p>
              </div>
              {u.id !== user?.id && (
                <button
                  type="button"
                  onClick={() => setTarget(u)}
                  className="shrink-0 rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
                >
                  Cambiar contraseña
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {target && (
        <ResetPasswordModal
          target={target}
          onClose={() => setTarget(null)}
          onDone={() => {
            setNotice(`Contraseña de ${target.name} cambiada.`)
            setTarget(null)
          }}
        />
      )}
    </div>
  )
}
