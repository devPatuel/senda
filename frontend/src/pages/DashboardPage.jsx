import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getSummary } from '../api/transactions'
import { formatCurrency, formatMonthLabel } from '../lib/format'
import { EmptyState, ErrorState, LoadingState } from '../components/ui'

function currentYearMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

function CategoryBreakdown({ title, items }) {
  if (items.length === 0) return null
  const max = Math.max(...items.map((item) => Number(item.total)))

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <h2 className="mb-4 text-sm font-semibold text-slate-700">{title}</h2>
      <ul className="space-y-4">
        {items.map((item) => (
          <li key={item.categoryId} className="space-y-1.5">
            <div className="flex items-center justify-between gap-3 text-sm">
              <span className="flex min-w-0 items-center gap-2">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: item.categoryColor }}
                  aria-hidden="true"
                />
                <span className="truncate font-medium text-slate-700">{item.categoryName}</span>
              </span>
              <span className="shrink-0 font-semibold text-slate-900">
                {formatCurrency(item.total)}
              </span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full"
                style={{
                  // Keep a minimum width so small amounts stay visible
                  width: `${Math.max((Number(item.total) / max) * 100, 4)}%`,
                  backgroundColor: item.categoryColor,
                }}
              />
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

export default function DashboardPage() {
  const [{ year, month }, setYearMonth] = useState(currentYearMonth)
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  // "loading" is derived: the request in flight has not been marked as loaded
  const [loadedKey, setLoadedKey] = useState(null)
  const requestKey = `${year}-${month}-${reloadKey}`
  const loading = loadedKey !== requestKey

  useEffect(() => {
    let cancelled = false
    const key = `${year}-${month}-${reloadKey}`
    getSummary(year, month)
      .then((data) => {
        if (!cancelled) {
          setSummary(data)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se ha podido cargar el resumen')
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [year, month, reloadKey])

  function changeMonth(delta) {
    setYearMonth((prev) => {
      const next = prev.month + delta
      if (next < 1) return { year: prev.year - 1, month: 12 }
      if (next > 12) return { year: prev.year + 1, month: 1 }
      return { year: prev.year, month: next }
    })
  }

  const monthLabel = formatMonthLabel(year, month)
  const isEmpty = summary && summary.byCategory.length === 0
  const balance = summary ? Number(summary.balance) : 0
  const expenses = summary ? summary.byCategory.filter((c) => c.type === 'EXPENSE') : []
  const incomes = summary ? summary.byCategory.filter((c) => c.type === 'INCOME') : []

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Inicio</h1>

        <div className="flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-1 py-1">
          <button
            type="button"
            onClick={() => changeMonth(-1)}
            aria-label="Mes anterior"
            className="rounded-lg p-1.5 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
              <path d="m15 6-6 6 6 6" />
            </svg>
          </button>
          <span className="min-w-36 text-center text-sm font-medium text-slate-700">
            {monthLabel}
          </span>
          <button
            type="button"
            onClick={() => changeMonth(1)}
            aria-label="Mes siguiente"
            className="rounded-lg p-1.5 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
              <path d="m9 6 6 6-6 6" />
            </svg>
          </button>
        </div>
      </div>

      {loading && <LoadingState label="Cargando el resumen…" />}

      {!loading && error && (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      )}

      {!loading && !error && isEmpty && (
        <EmptyState
          title="Sin movimientos este mes"
          message={`Cuando registres movimientos en ${monthLabel} verás aquí su resumen.`}
          action={
            <Link
              to="/movimientos"
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-700"
            >
              Añadir un movimiento
            </Link>
          }
        />
      )}

      {!loading && !error && summary && !isEmpty && (
        <>
          <section className="rounded-2xl border border-slate-200 bg-white p-6 text-center">
            <p className="text-sm text-slate-500">Balance del mes</p>
            <p
              className={[
                'mt-1 text-4xl font-bold tracking-tight',
                balance >= 0 ? 'text-emerald-600' : 'text-red-600',
              ].join(' ')}
            >
              {formatCurrency(summary.balance)}
            </p>
          </section>

          <div className="grid grid-cols-2 gap-3">
            <section className="rounded-2xl border border-slate-200 bg-white p-4">
              <div className="flex items-center gap-1.5 text-sm text-slate-500">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 text-emerald-500" aria-hidden="true">
                  <path d="M12 19V5" />
                  <path d="m6 11 6-6 6 6" />
                </svg>
                Ingresos
              </div>
              <p className="mt-1 text-xl font-semibold text-emerald-600">
                {formatCurrency(summary.totalIncome)}
              </p>
            </section>
            <section className="rounded-2xl border border-slate-200 bg-white p-4">
              <div className="flex items-center gap-1.5 text-sm text-slate-500">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 text-red-500" aria-hidden="true">
                  <path d="M12 5v14" />
                  <path d="m6 13 6 6 6-6" />
                </svg>
                Gastos
              </div>
              <p className="mt-1 text-xl font-semibold text-red-600">
                {formatCurrency(summary.totalExpense)}
              </p>
            </section>
          </div>

          <CategoryBreakdown title="Gastos por categoría" items={expenses} />
          <CategoryBreakdown title="Ingresos por categoría" items={incomes} />
        </>
      )}
    </div>
  )
}
