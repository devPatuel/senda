import { useEffect, useState } from 'react'
import { getEnvelopes, savePlan, distribute } from '../api/allocation'
import { formatCurrency } from '../lib/format'
import { Field, FormError, SubmitButton } from '../components/form'
import { ErrorState, LoadingState, Notice } from '../components/ui'

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

function sum(envelopes) {
  return envelopes.reduce((acc, e) => acc + Number(e.percentage || 0), 0)
}

function newLine() {
  return { _key: Math.random(), id: null, name: '', percentage: '' }
}

// -------------------------------------------------------------------------
// Envelope editor (plan)
// -------------------------------------------------------------------------

function EnvelopeEditor({ initialEnvelopes, onSaved }) {
  const [lines, setLines] = useState(() =>
    initialEnvelopes.length > 0
      ? initialEnvelopes.map((e) => ({
          _key: e.id,
          id: e.id,
          name: e.name,
          percentage: String(e.percentage),
        }))
      : [newLine()],
  )
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const total = sum(lines)
  const sumValid = Math.abs(total - 100) < 0.001

  function updateLine(index, field, value) {
    setLines((prev) => prev.map((l, i) => (i === index ? { ...l, [field]: value } : l)))
  }

  function addLine() {
    setLines((prev) => [...prev, newLine()])
  }

  function removeLine(index) {
    setLines((prev) => prev.filter((_, i) => i !== index))
  }

  async function handleSave() {
    setError(null)
    if (!sumValid) return
    setSaving(true)
    try {
      const envelopes = lines.map((l) => ({
        ...(l.id !== null ? { id: l.id } : {}),
        name: l.name.trim(),
        percentage: Number(l.percentage),
      }))
      await savePlan(envelopes)
      onSaved()
    } catch (err) {
      setError(err.message || 'No se ha podido guardar el plan')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-4">
      <FormError message={error} />

      <ul className="space-y-2">
        {lines.map((line, i) => (
          <li key={line._key} className="flex items-end gap-2">
            <div className="flex-1">
              <Field
                label={i === 0 ? 'Nombre del sobre' : undefined}
                name={`name-${i}`}
                type="text"
                placeholder="Ej.: Ahorro"
                value={line.name}
                onChange={(e) => updateLine(i, 'name', e.target.value)}
              />
            </div>
            <div className="w-28">
              <Field
                label={i === 0 ? 'Porcentaje' : undefined}
                name={`pct-${i}`}
                type="number"
                inputMode="decimal"
                min="0"
                max="100"
                step="0.01"
                placeholder="0"
                value={line.percentage}
                onChange={(e) => updateLine(i, 'percentage', e.target.value)}
              />
            </div>
            <button
              type="button"
              onClick={() => removeLine(i)}
              disabled={lines.length === 1}
              aria-label={`Eliminar sobre ${line.name || i + 1}`}
              className="mb-0.5 rounded-lg p-2 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
                <path d="M3 6h18" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </svg>
            </button>
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

      <div className="flex gap-2">
        <button
          type="button"
          onClick={addLine}
          className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:border-emerald-300 hover:text-emerald-700"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-4 w-4" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Añadir sobre
        </button>

        <button
          type="button"
          onClick={handleSave}
          disabled={saving || !sumValid}
          className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
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
    </div>
  )
}

// -------------------------------------------------------------------------
// Distribution simulator
// -------------------------------------------------------------------------

function DistributePanel({ envelopes, onPersisted }) {
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

  if (envelopes.length === 0) {
    return (
      <p className="text-sm text-slate-500">Define y guarda un plan de reparto primero.</p>
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
        if (!cancelled) setError(err.message || 'No se han podido cargar los sobres')
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
    setNotice('Cobro registrado. Los saldos han sido actualizados.')
    setReloadKey((k) => k + 1)
  }

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

      {loading && !envelopes && <LoadingState label="Cargando sobres…" />}

      {error && !loading && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!error && envelopes && (
        <>
          {/* Envelope balances summary */}
          {envelopes.length > 0 && (
            <section className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
                Saldos acumulados
              </h2>
              <ul className="divide-y divide-slate-100">
                {envelopes.map((e) => (
                  <li key={e.id} className="flex items-center justify-between py-2.5 text-sm">
                    <span className="flex items-center gap-2 text-slate-700">
                      <span className="inline-flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-50 text-xs font-semibold text-emerald-700">
                        {e.percentage}%
                      </span>
                      {e.name}
                    </span>
                    <span className="font-semibold tabular-nums text-slate-900">
                      {formatCurrency(e.balance)}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* Plan editor */}
          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Plan de reparto
            </h2>
            <EnvelopeEditor initialEnvelopes={envelopes} onSaved={handlePlanSaved} />
          </section>

          {/* Distribution simulator */}
          <section className="rounded-2xl border border-slate-200 bg-white p-5">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Simular cobro
            </h2>
            <DistributePanel envelopes={envelopes} onPersisted={handlePersisted} />
          </section>
        </>
      )}
    </div>
  )
}
