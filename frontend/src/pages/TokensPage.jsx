import { useEffect, useState } from 'react'
import { listTokens, createToken, revokeToken } from '../api/tokens'
import { Field, SubmitButton } from '../components/form'
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, Notice } from '../components/ui'

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

/** Highlighted block shown once with the freshly created clear token value. */
function NewTokenNotice({ token, onDismiss }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(token.value)
      setCopied(true)
    } catch {
      // clipboard unavailable: the value is visible for manual copy
    }
  }

  return (
    <div className="space-y-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
      <p className="text-sm font-medium text-emerald-900">
        Token «{token.name}» creado. Cópialo ahora: no volverás a verlo.
      </p>
      <div className="flex items-center gap-2">
        <code className="min-w-0 flex-1 truncate rounded-lg bg-white px-3 py-2 text-sm text-slate-800">
          {token.value}
        </code>
        <button
          type="button"
          onClick={copy}
          className="shrink-0 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
        >
          {copied ? 'Copiado' : 'Copiar'}
        </button>
      </div>
      <button
        type="button"
        onClick={onDismiss}
        className="text-sm text-emerald-800 underline underline-offset-2"
      >
        Ya lo he guardado
      </button>
    </div>
  )
}

export default function TokensPage() {
  const [tokens, setTokens] = useState(null)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [name, setName] = useState('')
  const [fieldError, setFieldError] = useState(null)
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState(null)
  const [toRevoke, setToRevoke] = useState(null)
  const [revoking, setRevoking] = useState(false)
  const loading = tokens === null

  useEffect(() => {
    let cancelled = false
    listTokens()
      .then((list) => {
        if (!cancelled) {
          setTokens(list)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar los tokens')
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  async function handleCreate(e) {
    e.preventDefault()
    setFieldError(null)
    if (!name.trim()) {
      setFieldError('Ponle un nombre (ej.: iPhone de Jordi)')
      return
    }
    setCreating(true)
    setActionError(null)
    try {
      const token = await createToken(name.trim())
      setCreated(token)
      setName('')
      setReloadKey((k) => k + 1)
    } catch (err) {
      setActionError(err.message || 'No se ha podido crear el token')
    } finally {
      setCreating(false)
    }
  }

  async function handleRevoke() {
    setRevoking(true)
    setActionError(null)
    try {
      await revokeToken(toRevoke.id)
      setReloadKey((k) => k + 1)
    } catch (err) {
      setActionError(err.message || 'No se ha podido revocar el token')
    } finally {
      setToRevoke(null)
      setRevoking(false)
    }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Tokens</h1>
      <p className="text-sm text-slate-500">
        Genera un token personal para capturar gastos desde el iPhone (Apple Shortcut) sin abrir la
        app. Solo sirve para eso: con él se pueden leer tus categorías y apuntar un gasto, pero no
        ver tus movimientos ni tocar nada más. Aun así, si pierdes el móvil, revócalo aquí.
      </p>

      {actionError && (
        <Notice tone="error" onClose={() => setActionError(null)}>
          {actionError}
        </Notice>
      )}

      {created && <NewTokenNotice token={created} onDismiss={() => setCreated(null)} />}

      <form onSubmit={handleCreate} noValidate className="flex items-end gap-2">
        <div className="flex-1">
          <Field
            label="Nombre del token"
            name="name"
            type="text"
            placeholder="iPhone de Jordi"
            value={name}
            onChange={(e) => setName(e.target.value)}
            error={fieldError}
          />
        </div>
        <SubmitButton loading={creating} loadingText="Generando…">
          Generar
        </SubmitButton>
      </form>

      {loading && <LoadingState label="Cargando tokens…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!error && tokens && tokens.length === 0 && (
        <EmptyState
          title="Aún no hay tokens"
          message="Genera uno para configurar el atajo de captura rápida en tu iPhone."
        />
      )}

      {!error && tokens && tokens.length > 0 && (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white">
          {tokens.map((t) => (
            <li key={t.id} className="flex items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900">{t.name}</p>
                <p className="text-xs text-slate-500">
                  Creado {formatDate(t.createdAt)} · Último uso {formatDate(t.lastUsedAt)}
                  {t.revokedAt && ' · Revocado'}
                </p>
              </div>
              {t.revokedAt ? (
                <span className="shrink-0 rounded-full bg-slate-100 px-2.5 py-1 text-xs text-slate-500">
                  Revocado
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => setToRevoke(t)}
                  className="shrink-0 rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-red-600 transition-colors hover:border-red-200 hover:bg-red-50"
                >
                  Revocar
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {toRevoke && (
        <ConfirmDialog
          title="Revocar token"
          message={`¿Revocar «${toRevoke.name}»? El atajo que lo use dejará de funcionar.`}
          confirmLabel="Revocar"
          loading={revoking}
          onCancel={() => setToRevoke(null)}
          onConfirm={handleRevoke}
        />
      )}
    </div>
  )
}
