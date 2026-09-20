import { useEffect, useState } from 'react'
import { getEnvelopes, savePlan, distribute } from '../api/allocation'
import { formatCurrency } from '../lib/format'
import { Field, FormError, SubmitButton } from '../components/form'
import { ErrorState, LoadingState, Notice } from '../components/ui'

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

function parseDecimal(raw) {
  return Number(String(raw).replace(',', '.'))
}

function sum(lines) {
  return lines.reduce((acc, l) => acc + (parseDecimal(l.percentage) || 0), 0)
}

// -------------------------------------------------------------------------
// Plan editor — assigns a percentage to each expense category
// -------------------------------------------------------------------------

function PlanEditor({ envelopes, onSaved }) {
  // Envelope figures are read straight from the server data, never from the
  // form state: editing a percentage must not look like it moved money.
  const byId = new Map(envelopes.map((e) => [e.id, e]))
  // One row per expense category; percentage is the target share (blank = 0).
  const [lines, setLines] = useState(() =>
    envelopes.map((e) => ({
      id: e.id,
      name: e.name,
      color: e.color,
      percentage: Number(e.percentage) > 0 ? String(e.percentage) : '',
    })),
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const total = sum(lines)
  const sumValid = Math.abs(total - 100) < 0.001

  function updateLine(index, value) {
    setLines((prev) => prev.map((l, i) => (i === index ? { ...l, percentage: value } : l)))
  }

  async function handleSave() {
    setError(null)
    if (!sumValid) return
    setSaving(true)
    try {
      // Only categories with a positive share are part of the plan.
      const plan = lines
        .filter((l) => (parseDecimal(l.percentage) || 0) > 0)
        .map((l) => ({ categoryId: l.id, percentage: parseDecimal(l.percentage) }))
      await savePlan(plan)
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido guardar el plan')
    } finally {
      setSaving(false)
    }
  }

  if (lines.length === 0) {
    return (
      <p className="text-sm text-slate-500">
        Crea primero categorías de gasto en la pantalla de Categorías.
      </p>
    )
  }

  return (
    <div className="space-y-4">
      <FormError message={error} />

      <ul className="space-y-2">
        {lines.map((line, i) => (
          <li key={line.id} className="flex items-center gap-3">
            <span
              className="h-4 w-4 shrink-0 rounded-full border border-slate-200"
              style={{ backgroundColor: line.color }}
              aria-hidden="true"
            />
            <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-700">
              {line.name}
            </span>
            {byId.get(line.id)?.available != null && (
              <span
                data-testid={`envelope-available-${line.id}`}
                data-negative={String(Number(byId.get(line.id).available) < 0)}
                title={`${formatCurrency(byId.get(line.id).spent)} gastado de ${formatCurrency(byId.get(line.id).balance)}`}
                className={[
                  'shrink-0 text-xs tabular-nums',
                  Number(byId.get(line.id).available) < 0 ? 'text-red-600' : 'text-slate-400',
                ].join(' ')}
              >
                {formatCurrency(byId.get(line.id).available)}
              </span>
            )}
            <div className="w-24">
              <Field
                label={undefined}
                name={`pct-${line.id}`}
                type="number"
                inputMode="decimal"
                min="0"
                max="100"
                step="0.01"
                placeholder="0"
                value={line.percentage}
                onChange={(e) => updateLine(i, e.target.value)}
              />
            </div>
            <span className="w-4 text-sm text-slate-400">%</span>
          </li>
        ))}
      </ul>

      {/* Live total indicator */}
      <div
        className={[
          'flex items-center justify-between rounded-lg border px-3.5 py-2.5 text-sm font-medium',
          sumValid
            ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
            : 'border-red-200 bg-red-50 text-red-700',
        ].join(' ')}
        aria-live="polite"
      >
        <span>Total asignado</span>
        <span>{total.toFixed(2)} %</span>
      </div>

      <button
        type="button"
        onClick={handleSave}
        disabled={saving || !sumValid}
        className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {saving && (
          <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" className="opacity-25" />
            <path d="M4 12a8 8 0 0 1 8-8" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
          </svg>
        )}
        {saving ? 'Guardando…' : 'Guardar plan'}
      </button>
    </div>
  )
}

// -------------------------------------------------------------------------
// Distribution simulator
// -------------------------------------------------------------------------

function DistributePanel({ hasPlan, onPersisted }) {
  const [amount, setAmount] = useState('')
  const [result, setResult] = useState(null)
  const [simulating, setSimulating] = useState(false)
  const [persisting, setPersisting] = useState(false)
  const [error, setError] = useState(null)

  async function handleSimulate(e) {
    e.preventDefault()
    setError(null)
    setResult(null)
    if (!amount || Number(amount) <= 0) return
    setSimulating(true)
    try {
      const data = await distribute(Number(amount), false)
      setResult(data)
    } catch (err) {
      setError(err.message || 'Error al simular el reparto')
    } finally {
      setSimulating(false)
    }
  }

  async function handlePersist() {
    setError(null)
    setPersisting(true)
    try {
      await distribute(Number(amount), true)
      setResult(null)
      setAmount('')
      onPersisted()
    } catch (err) {
      setError(err.message || 'Error al registrar el cobro')
    } finally {
      setPersisting(false)
    }
  }

  if (!hasPlan) {
    return (
      <p className="text-sm text-slate-500">
        Asigna porcentajes a tus categorías y guarda el plan primero.
      </p>
    )
  }

  return (
    <div className="space-y-4">
      <FormError message={error} />

      <form onSubmit={handleSimulate} className="flex gap-2">
        <div className="flex-1">
          <Field
            label="Importe a repartir"
            name="distribute-amount"
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            placeholder="0,00"
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value)
              setResult(null)
            }}
          />
        </div>
        <div className="flex items-end">
          <SubmitButton loading={simulating} loadingText="Calculando…">
            Calcular
          </SubmitButton>
        </div>
      </form>

      {result && (
        <div className="space-y-3">
          <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white">
            {result.lines.map((line) => (
              <li key={line.envelopeId} className="flex items-center gap-3 px-4 py-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-emerald-50 text-xs font-semibold text-emerald-700">
                  {line.percentage}%
                </span>
                <span className="min-w-0 flex-1 truncate text-sm text-slate-700">{line.envelopeName}</span>
                <span className="shrink-0 text-sm font-semibold tabular-nums text-emerald-700">
                  +{formatCurrency(line.allocated)}
                </span>
              </li>
            ))}
          </ul>

          <div className="flex items-center justify-between rounded-lg border border-emerald-200 bg-emerald-50 px-3.5 py-2.5 text-sm font-medium text-emerald-800">
            <span>Total a repartir</span>
            <span>{formatCurrency(result.amount)}</span>
          </div>

          <button
            type="button"
            onClick={handlePersist}
            disabled={persisting}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {persisting && (
              <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" className="opacity-25" />
                <path d="M4 12a8 8 0 0 1 8-8" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
              </svg>
            )}
            {persisting ? 'Registrando…' : 'Registrar cobro'}
          </button>
        </div>
      )}
    </div>
  )
}

// -------------------------------------------------------------------------
// Main page
// -------------------------------------------------------------------------

export default function AllocationPage() {
  const [envelopes, setEnvelopes] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [loadedKey, setLoadedKey] = useState(null)
  const loading = loadedKey !== reloadKey

  useEffect(() => {
    let cancelled = false
    const key = reloadKey
    getEnvelopes()
      .then((list) => {
        if (!cancelled) {
          setEnvelopes(list)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se han podido cargar las categorías')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  function handlePlanSaved() {
    setNotice('Plan guardado correctamente.')
    setReloadKey((k) => k + 1)
  }

  function handlePersisted() {
    setNotice('Cobro registrado. Los saldos de las categorías han sido actualizados.')
    setReloadKey((k) => k + 1)
  }

  const hasPlan = (envelopes ?? []).some((e) => Number(e.percentage) > 0)

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Reparto de sueldo</h1>
      </div>

      {notice && (
        <Notice tone="success" onClose={() => setNotice(null)}>
          {notice}
        </Notice>
      )}

      {loading && !envelopes && <LoadingState label="Cargando categorías…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!error && envelopes && (
        <>
          {/* Plan editor */}
          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Plan de reparto
            </h2>
            <p className="mb-4 text-sm text-slate-500">
              Reparte tu sueldo entre tus categorías de gasto. Los porcentajes deben sumar 100.
            </p>
            <PlanEditor envelopes={envelopes} onSaved={handlePlanSaved} />
          </section>

          {/* Distribution simulator */}
          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Simular cobro
            </h2>
            <DistributePanel hasPlan={hasPlan} onPersisted={handlePersisted} />
          </section>
        </>
      )}
    </div>
  )
}
