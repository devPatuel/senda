import { useEffect, useState } from 'react'
import {
  listSpaces,
  createSpace,
  addMember,
  acceptSpace,
  declineSpace,
  leaveSpace,
  listMembers,
} from '../api/spaces'
import { Field, FormError, SubmitButton } from '../components/form'
import { ConfirmDialog, ErrorState, LoadingState, Notice } from '../components/ui'
import AccountsPage from './AccountsPage'
import CategoriesPage from './CategoriesPage'
import TransactionsPage from './TransactionsPage'

const TABS = [
  { key: 'cuentas', label: 'Cuentas' },
  { key: 'categorias', label: 'Categorías' },
  { key: 'movimientos', label: 'Movimientos' },
]

// Card to bootstrap a couple space when the user has none.
function CreateSpaceCard({ onCreated }) {
  const [name, setName] = useState('Pareja')
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Introduce un nombre')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await createSpace(name.trim())
      onCreated()
    } catch (err) {
      setError(err.message || 'No se ha podido crear el espacio')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="text-lg font-semibold tracking-tight text-slate-900">Crear espacio de pareja</h2>
      <p className="mt-1 text-sm text-slate-500">
        Un espacio compartido para las cuentas, categorías y movimientos que lleváis a medias.
      </p>
      <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-4">
        <FormError message={error} />
        <Field
          label="Nombre del espacio"
          name="name"
          type="text"
          placeholder="Pareja"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <SubmitButton loading={saving} loadingText="Creando…">
          Crear espacio
        </SubmitButton>
      </form>
    </section>
  )
}

// Banner shown when someone invited the user to their space.
function PendingInvite({ space, onChanged }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  async function respond(action) {
    setBusy(true)
    setError(null)
    try {
      await action(space.id)
      onChanged()
    } catch (err) {
      setError(err.message || 'No se ha podido responder a la invitación')
      setBusy(false)
    }
  }

  return (
    <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-6">
      <h2 className="text-lg font-semibold tracking-tight text-emerald-900">
        Tienes una invitación
      </h2>
      <p className="mt-1 text-sm text-emerald-800">
        Te han invitado al espacio de pareja «{space.name}».
      </p>
      {error && (
        <div className="mt-3">
          <FormError message={error} />
        </div>
      )}
      <div className="mt-4 flex gap-2">
        <button
          type="button"
          onClick={() => respond(acceptSpace)}
          disabled={busy}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Aceptar
        </button>
        <button
          type="button"
          onClick={() => respond(declineSpace)}
          disabled={busy}
          className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-slate-400 hover:text-slate-900 disabled:opacity-60"
        >
          Rechazar
        </button>
      </div>
    </section>
  )
}

// Form to invite a partner by email into the active space.
function InviteForm({ spaceId, onInvited }) {
  const [email, setEmail] = useState('')
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    if (!email.trim()) {
      setError('Introduce un email')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await addMember(spaceId, email.trim())
      setEmail('')
      onInvited()
    } catch (err) {
      setError(err.message || 'No se ha podido enviar la invitación')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-3">
      <FormError message={error} />
      <Field
        label="Invitar por email"
        name="email"
        type="email"
        placeholder="pareja@correo.com"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <SubmitButton loading={saving} loadingText="Enviando…">
        Invitar
      </SubmitButton>
    </form>
  )
}

const STATUS_LABELS = { ACTIVE: 'Activo', PENDING: 'Pendiente' }

// Header of the active space: name, members, invite form and leave button.
function SpaceHeader({ space, onLeave }) {
  const [members, setMembers] = useState(null)
  const [membersError, setMembersError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [confirmLeave, setConfirmLeave] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [actionError, setActionError] = useState(null)

  useEffect(() => {
    let cancelled = false
    listMembers(space.id)
      .then((list) => {
        if (!cancelled) {
          setMembers(list)
          setMembersError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setMembersError(err.message || 'No se han podido cargar los miembros')
      })
    return () => {
      cancelled = true
    }
  }, [space.id, reloadKey])

  async function handleLeave() {
    setLeaving(true)
    setActionError(null)
    try {
      await leaveSpace(space.id)
      setConfirmLeave(false)
      onLeave()
    } catch (err) {
      setActionError(err.message || 'No se ha podido salir del espacio')
      setConfirmLeave(false)
    } finally {
      setLeaving(false)
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{space.name}</h1>
        <button
          type="button"
          onClick={() => setConfirmLeave(true)}
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-600 transition-colors hover:border-red-300 hover:text-red-600"
        >
          Salir del espacio
        </button>
      </div>

      {actionError && (
        <div className="mt-3">
          <Notice tone="error" onClose={() => setActionError(null)}>
            {actionError}
          </Notice>
        </div>
      )}

      <div className="mt-4">
        <p className="text-sm font-medium text-slate-700">Miembros</p>
        {membersError && (
          <p className="mt-1 text-sm text-red-600">{membersError}</p>
        )}
        {members && (
          <ul className="mt-2 divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200">
            {members.map((m) => (
              <li key={m.userId} className="flex items-center justify-between gap-3 px-3 py-2">
                <div className="min-w-0">
                  {m.name && <p className="truncate text-sm font-medium text-slate-900">{m.name}</p>}
                  <p className="truncate text-xs text-slate-500">{m.email}</p>
                </div>
                <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
                  {STATUS_LABELS[m.status] ?? m.status}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <InviteForm spaceId={space.id} onInvited={() => setReloadKey((k) => k + 1)} />

      {confirmLeave && (
        <ConfirmDialog
          title="Salir del espacio"
          message="¿Seguro que quieres salir de este espacio de pareja? Dejarás de ver sus cuentas, categorías y movimientos."
          confirmLabel="Salir"
          loading={leaving}
          onCancel={() => setConfirmLeave(false)}
          onConfirm={handleLeave}
        />
      )}
    </section>
  )
}

// Tabbed resource pages, reused with the space's id.
function SpaceResources({ spaceId }) {
  const [activeTab, setActiveTab] = useState('cuentas')

  return (
    <div className="space-y-4">
      <div className="flex gap-1 rounded-2xl border border-slate-200 bg-white p-1">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            className={[
              'flex-1 rounded-xl px-3 py-2 text-sm font-medium transition-colors',
              activeTab === tab.key
                ? 'bg-emerald-600 text-white'
                : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'cuentas' && <AccountsPage spaceId={spaceId} />}
      {activeTab === 'categorias' && <CategoriesPage spaceId={spaceId} />}
      {activeTab === 'movimientos' && <TransactionsPage spaceId={spaceId} />}
    </div>
  )
}

export default function SpacePage() {
  const [spaces, setSpaces] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    listSpaces()
      .then((list) => {
        if (!cancelled) {
          setSpaces(list)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar los espacios')
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  function reload() {
    setReloadKey((k) => k + 1)
  }

  if (error) {
    return <ErrorState message={error} onRetry={reload} />
  }

  if (!spaces) {
    return <LoadingState label="Cargando espacio…" />
  }

  const activeSpace = spaces.find((s) => s.myStatus === 'ACTIVE')
  const pendingSpace = spaces.find((s) => s.myStatus === 'PENDING')

  if (activeSpace) {
    return (
      <div className="space-y-4">
        {/* A pending invite stays actionable even once the user has their own
            active space, otherwise it would be orphaned and impossible to
            accept or decline. */}
        {pendingSpace && <PendingInvite space={pendingSpace} onChanged={reload} />}
        <SpaceHeader space={activeSpace} onLeave={reload} />
        <SpaceResources spaceId={activeSpace.id} />
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {pendingSpace && <PendingInvite space={pendingSpace} onChanged={reload} />}
      <CreateSpaceCard onCreated={reload} />
    </div>
  )
}
