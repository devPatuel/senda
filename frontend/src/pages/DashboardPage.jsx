import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getSummary } from '../api/transactions'
import { getNetWorth } from '../api/networth'
import { getRecurring } from '../api/recurring'
import { formatCurrency, formatMonthLabel, formatDate } from '../lib/format'
import { EmptyState, ErrorState, LoadingState } from '../components/ui'

function currentYearMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

// Recurring payments due within the next `days` days (list is pre-sorted by date).
function upcomingWithin(payments, days) {
  if (!payments) return []
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const horizon = new Date(today)
  horizon.setDate(horizon.getDate() + days)
  return payments.filter((p) => {
    const due = new Date(`${p.nextDueDate}T00:00:00`)
    return due >= today && due <= horizon
  })
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

// Small labelled figure used in the net-worth breakdown row.
function NetWorthStat({ label, value, tone = 'neutral' }) {
  const valueClasses = {
    positive: 'text-emerald-600',
    negative: 'text-red-600',
    neutral: 'text-slate-900',
  }[tone]
  return (
    <div className="text-center">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={['mt-0.5 text-sm font-semibold tabular-nums', valueClasses].join(' ')}>
        {formatCurrency(value)}
      </p>
    </div>
  )
}

export default function DashboardPage() {
  const [{ year, month }, setYearMonth] = useState(currentYearMonth)

  // "Now" data (net worth + recurring) — independent of the selected month.
  const [netWorth, setNetWorth] = useState(null)
  const [recurring, setRecurring] = useState(null)
  const [nowError, setNowError] = useState(null)

  // Monthly summary — depends on the selected month.
  const [summary, setSummary] = useState(null)
  const [summaryError, setSummaryError] = useState(null)
  const [summaryLoadedKey, setSummaryLoadedKey] = useState(null)
  const summaryKey = `${year}-${month}`
  const summaryLoading = summaryLoadedKey !== summaryKey

  useEffect(() => {
    let cancelled = false
    Promise.all([getNetWorth(), getRecurring()])
      .then(([nw, rec]) => {
        if (!cancelled) {
          setNetWorth(nw)
          setRecurring(rec)
          setNowError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setNowError(err.message || 'No se han podido cargar los datos')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const key = `${year}-${month}`
    getSummary(year, month)
      .then((data) => {
        if (!cancelled) {
          setSummary(data)
          setSummaryError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setSummaryError(err.message || 'No se ha podido cargar el resumen')
      })
      .finally(() => {
        if (!cancelled) setSummaryLoadedKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [year, month])

  function changeMonth(delta) {
    setYearMonth((prev) => {
      const next = prev.month + delta
      if (next < 1) return { year: prev.year - 1, month: 12 }
      if (next > 12) return { year: prev.year + 1, month: 1 }
      return { year: prev.year, month: next }
    })
  }

  const monthLabel = formatMonthLabel(year, month)
  const isEmptyMonth = summary && summary.byCategory.length === 0
  const balance = summary ? Number(summary.balance) : 0
  const expenses = summary ? summary.byCategory.filter((c) => c.type === 'EXPENSE') : []
  const incomes = summary ? summary.byCategory.filter((c) => c.type === 'INCOME') : []
  const upcoming = upcomingWithin(recurring, 30)
  const debtsNet = netWorth ? Number(netWorth.debtsInFavor) - Number(netWorth.debtsAgainst) : 0

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Inicio</h1>

      {/* Net worth */}
      {nowError && <ErrorState message={nowError} />}

      {!nowError && !netWorth && <LoadingState label="Cargando patrimonio…" />}

      {!nowError && netWorth && (
        <section className="rounded-2xl border border-slate-200 bg-white p-6">
          <p className="text-center text-sm text-slate-500">Patrimonio neto</p>
          <p
            className={[
              'mt-1 text-center text-4xl font-bold tracking-tight',
              Number(netWorth.net) >= 0 ? 'text-emerald-600' : 'text-red-600',
            ].join(' ')}
          >
            {formatCurrency(netWorth.net)}
          </p>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-4">
            <NetWorthStat label="Líquido" value={netWorth.liquid} />
            <NetWorthStat label="Inversiones" value={netWorth.investments} />
            <NetWorthStat
              label="Deudas"
              value={debtsNet}
              tone={debtsNet < 0 ? 'negative' : debtsNet > 0 ? 'positive' : 'neutral'}
            />
          </div>
        </section>
      )}

      {/* Upcoming recurring payments */}
      {upcoming.length > 0 && (
        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-700">Próximos pagos (30 días)</h2>
            <Link to="/recurrentes" className="text-xs font-medium text-emerald-600 hover:text-emerald-700">
              Ver todos
            </Link>
          </div>
          <ul className="divide-y divide-slate-100">
            {upcoming.map((p) => (
              <li key={p.id} className="flex items-center gap-3 px-4 py-3">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: p.categoryColor }}
                  aria-hidden="true"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-900">{p.name}</p>
                  <p className="truncate text-xs text-slate-500">{formatDate(p.nextDueDate)}</p>
                </div>
                <p className="shrink-0 text-sm font-semibold tabular-nums text-slate-900">
                  {formatCurrency(p.amount)}
                </p>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Monthly summary */}
      <section className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Resumen del mes</h2>
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
            <span className="min-w-36 text-center text-sm font-medium text-slate-700">{monthLabel}</span>
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

        {summaryLoading && !summary && <LoadingState label="Cargando el resumen…" />}

        {!summaryLoading && summaryError && (
          <ErrorState message={summaryError} onRetry={() => setSummaryLoadedKey(null)} />
        )}

        {!summaryError && isEmptyMonth && (
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

        {!summaryError && summary && !isEmptyMonth && (
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
      </section>
    </div>
  )
}
