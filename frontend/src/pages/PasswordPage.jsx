import { useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { Field, SubmitButton, FormError } from '../components/form'
import { Notice } from '../components/ui'

const EMPTY = { current: '', next: '', repeat: '' }

export default function PasswordPage() {
  const { changePassword } = useAuth()
  const [form, setForm] = useState(EMPTY)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function validate() {
    const errors = {}
    if (!form.current) errors.current = 'Introduce tu contraseña actual'
    if (form.next.length < 8) errors.next = 'Debe tener al menos 8 caracteres'
    if (form.repeat !== form.next) errors.repeat = 'Las dos contraseñas no coinciden'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setDone(false)

    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setLoading(true)
    try {
      await changePassword(form.current, form.next)
      setForm(EMPTY)
      setDone(true)
    } catch (err) {
      // The API answers 422 (not 401) so a wrong current password does not log out
      if (err.status === 422) setFieldErrors({ current: 'La contraseña actual no es correcta' })
      else setError(err.message || 'No se ha podido cambiar la contraseña')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Contraseña</h1>
      <p className="text-sm text-slate-500">
        Al cambiarla se cierran todas las sesiones abiertas en otros dispositivos; este sigue
        dentro. Los tokens personales no cambian: se revocan desde Tokens.
      </p>

      {done && (
        <Notice onClose={() => setDone(false)}>
          Contraseña cambiada. Las demás sesiones se han cerrado.
        </Notice>
      )}

      <form
        onSubmit={handleSubmit}
        noValidate
        className="max-w-md space-y-4 rounded-2xl border border-slate-200 bg-white p-5"
      >
        <FormError message={error} />
        <Field
          label="Contraseña actual"
          name="current"
          type="password"
          autoComplete="current-password"
          value={form.current}
          onChange={handleChange}
          error={fieldErrors.current}
        />
        <Field
          label="Contraseña nueva"
          name="next"
          type="password"
          autoComplete="new-password"
          value={form.next}
          onChange={handleChange}
          error={fieldErrors.next}
        />
        <Field
          label="Repite la contraseña nueva"
          name="repeat"
          type="password"
          autoComplete="new-password"
          value={form.repeat}
          onChange={handleChange}
          error={fieldErrors.repeat}
        />
        <SubmitButton loading={loading} loadingText="Cambiando…">
          Cambiar contraseña
        </SubmitButton>
      </form>
    </div>
  )
}
