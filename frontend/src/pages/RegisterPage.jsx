import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import AuthShell from '../components/AuthShell'
import { Field, SubmitButton, FormError } from '../components/form'

export default function RegisterPage() {
  const { register, isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
  })
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
    if (!form.name.trim()) errors.name = 'Introduce tu nombre'
    if (!form.email.trim()) errors.email = 'Introduce tu email'
    else if (!/^\S+@\S+\.\S+$/.test(form.email)) errors.email = 'El email no es válido'
    if (!form.password) errors.password = 'Introduce una contraseña'
    else if (form.password.length < 8)
      errors.password = 'La contraseña debe tener al menos 8 caracteres'
    if (form.confirmPassword !== form.password)
      errors.confirmPassword = 'Las contraseñas no coinciden'
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
      await register(form.name.trim(), form.email.trim(), form.password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message || 'No se ha podido completar el registro')
      if (err.fieldErrors) setFieldErrors(err.fieldErrors)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell
      title="Crea tu cuenta"
      subtitle="Empieza a controlar tus gastos e ingresos"
    >
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error} />

        <Field
          label="Nombre"
          name="name"
          type="text"
          autoComplete="name"
          value={form.name}
          onChange={handleChange}
          error={fieldErrors.name}
          placeholder="Tu nombre"
        />
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
          autoComplete="new-password"
          value={form.password}
          onChange={handleChange}
          error={fieldErrors.password}
          placeholder="Mínimo 8 caracteres"
        />
        <Field
          label="Repite la contraseña"
          name="confirmPassword"
          type="password"
          autoComplete="new-password"
          value={form.confirmPassword}
          onChange={handleChange}
          error={fieldErrors.confirmPassword}
          placeholder="••••••••"
        />

        <SubmitButton loading={loading} loadingText="Creando cuenta…">
          Crear cuenta
        </SubmitButton>
      </form>

      <p className="mt-6 text-center text-sm text-slate-500">
        ¿Ya tienes cuenta?{' '}
        <Link to="/login" className="font-medium text-emerald-600 hover:text-emerald-700">
          Inicia sesión
        </Link>
      </p>
    </AuthShell>
  )
}
