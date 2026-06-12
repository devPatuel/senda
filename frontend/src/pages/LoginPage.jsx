import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import AuthShell from '../components/AuthShell'
import { Field, SubmitButton, FormError } from '../components/form'

export default function LoginPage() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function validate() {
    const errors = {}
    if (!form.email.trim()) errors.email = 'Introduce tu email'
    else if (!/^\S+@\S+\.\S+$/.test(form.email)) errors.email = 'El email no es válido'
    if (!form.password) errors.password = 'Introduce tu contraseña'
    return errors
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)

    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setLoading(true)
    try {
      await login(form.email.trim(), form.password)
      const from = location.state?.from?.pathname || '/'
      navigate(from, { replace: true })
    } catch (err) {
      setError(err.message || 'No se ha podido iniciar sesión')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell
      title="Inicia sesión"
      subtitle="Accede a tu cuenta para gestionar tus finanzas"
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <Field
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          value={form.email}
          onChange={handleChange}
          error={fieldErrors.email}
          placeholder="tu@email.com"
        />
        <Field
          label="Contraseña"
          name="password"
          type="password"
          autoComplete="current-password"
          value={form.password}
          onChange={handleChange}
          error={fieldErrors.password}
          placeholder="••••••••"
        />

        <SubmitButton loading={loading} loadingText="Entrando…">
          Entrar
        </SubmitButton>
      </form>

      <p className="mt-6 text-center text-sm text-slate-500">
        ¿No tienes cuenta?{' '}
        <Link to="/register" className="font-medium text-emerald-600 hover:text-emerald-700">
          Regístrate
        </Link>
      </p>
    </AuthShell>
  )
}
