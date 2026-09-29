import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getSummary, getTrends } from '../api/transactions'
import { getNetWorth, getNetWorthHistory } from '../api/networth'
import { getRecurring } from '../api/recurring'
import { listCategories } from '../api/categories'
import { getAlerts } from '../api/alerts'
import { formatCurrency, formatMonthLabel, formatDate } from '../lib/format'
import { EmptyState, ErrorState, LoadingState } from '../components/ui'
import TransactionForm from '../components/TransactionForm'
import TrendsChart from '../components/TrendsChart'
import NetWorthChart from '../components/NetWorthChart'

function currentYearMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

function previousMonth(year, month) {
  return month === 1 ? { year: year - 1, month: 12 } : { year, month: month - 1 }
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

// Recurring payments whose cancellation deadline falls within the next `days` days.
function endingWithin(payments, days) {
  if (!payments) return []
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const horizon = new Date(today)
  horizon.setDate(horizon.getDate() + days)
  return payments.filter((p) => {
    if (!p.endDate) return false
    const end = new Date(`${p.endDate}T00:00:00`)
    return end >= today && end <= horizon
  })
}

// Month-over-month change for a category, relative to the same category last month.
function CategoryDelta({ current, prev, type }) {
  if (prev == null || prev <= 0) {
    return <span className="text-xs text-slate-400">nuevo</span>
  }
  const pct = Math.round(((current - prev) / prev) * 100)
  if (pct === 0) {
    return <span className="text-xs text-slate-400">sin cambio</span>
  }
  const up = pct > 0
  // For expenses, spending more is "worse" (red); for incomes it is the opposite.
  const tone =
    type === 'EXPENSE'
      ? up
        ? 'text-red-600'
        : 'text-emerald-600'
      : up
        ? 'text-emerald-600'
        : 'text-red-600'
  return (
    <span className={['text-xs font-medium tabular-nums', tone].join(' ')}>
      {up ? '▲' : '▼'} {up ? '+' : '−'}
      {Math.abs(pct)}%
    </span>
  )
}

function CategoryBreakdown({ title, items, prevById, type }) {
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
              <span className="flex shrink-0 items-center gap-2">
                {prevById && (
                  <CategoryDelta
                    current={Number(item.total)}
                    prev={prevById.has(item.categoryId) ? prevById.get(item.categoryId) : null}
                    type={type}
                  />
                )}
                <span className="font-semibold text-slate-900">{formatCurrency(item.total)}</span>
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

  // Bumped after a quick-add to refresh both "now" data and the monthly summary.
  const [reloadKey, setReloadKey] = useState(0)

  // "Now" data (net worth + recurring + categories + series) — independent of the month.
  const [netWorth, setNetWorth] = useState(null)
  const [recurring, setRecurring] = useState(null)
  const [categories, setCategories] = useState([])
  const [trends, setTrends] = useState(null)
  const [history, setHistory] = useState(null)
  const [alerts, setAlerts] = useState(null)
  const [nowError, setNowError] = useState(null)

  // Monthly summary — depends on the selected month. We also fetch the previous
  // month to show each category's variation.
  const [summary, setSummary] = useState(null)
  const [prevSummary, setPrevSummary] = useState(null)
  const [summaryError, setSummaryError] = useState(null)
  const [summaryLoadedKey, setSummaryLoadedKey] = useState(null)
  const summaryKey = `${year}-${month}-${reloadKey}`
  const summaryLoading = summaryLoadedKey !== summaryKey

  const [formOpen, setFormOpen] = useState(false)

  useEffect(() => {
    let cancelled = false
    // Core "now" data: a failure here surfaces as nowError.
    Promise.all([getNetWorth(), getRecurring(), listCategories({ includeInactive: true })])
      .then(([nw, rec, cats]) => {
        if (!cancelled) {
          setNetWorth(nw)
          setRecurring(rec)
          setCategories(cats)
          setNowError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setNowError(err.message || 'No se han podido cargar los datos')
      })
    // Charts are secondary: a failure only hides them, never the core data.
    getTrends(6)
      .then((tr) => { if (!cancelled) setTrends(tr) })
      .catch(() => { if (!cancelled) setTrends(null) })
    getNetWorthHistory(30)
      .then((hist) => { if (!cancelled) setHistory(hist) })
      .catch(() => { if (!cancelled) setHistory(null) })
    getAlerts()
      .then((al) => { if (!cancelled) setAlerts(al) })
      .catch(() => { if (!cancelled) setAlerts(null) })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  useEffect(() => {
    let cancelled = false
    const key = `${year}-${month}-${reloadKey}`
    const prev = previousMonth(year, month)
    Promise.all([getSummary(year, month), getSummary(prev.year, prev.month)])
      .then(([current, previous]) => {
        if (!cancelled) {
          setSummary(current)
          setPrevSummary(previous)
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
  }, [year, month, reloadKey])

  function changeMonth(delta) {
    setYearMonth((prev) => {
      const next = prev.month + delta
      if (next < 1) return { year: prev.year - 1, month: 12 }
      if (next > 12) return { year: prev.year + 1, month: 1 }
      return { year: prev.year, month: next }
    })
  }

  function handleSaved() {
    setFormOpen(false)
    // Refresh both the monthly summary and the "now" figures (net worth).
    setReloadKey((k) => k + 1)
  }

  const monthLabel = formatMonthLabel(year, month)
  const isEmptyMonth = summary && summary.byCategory.length === 0
  const balance = summary ? Number(summary.balance) : 0
  const expenses = summary ? summary.byCategory.filter((c) => c.type === 'EXPENSE') : []
  const incomes = summary ? summary.byCategory.filter((c) => c.type === 'INCOME') : []
  const prevById = prevSummary
    ? new Map(prevSummary.byCategory.map((c) => [c.categoryId, Number(c.total)]))
    : null
  const upcoming = upcomingWithin(recurring, 30)
  const cancellations = endingWithin(recurring, 14)
  const antExpenses = alerts?.antExpenses ?? []
  const forgotten = alerts?.forgottenSubscriptions ?? []
  const hasAlerts = antExpenses.length + forgotten.length > 0
  const debtsNet = netWorth ? Number(netWorth.debtsInFavor) - Number(netWorth.debtsAgainst) : 0
  // Half of the couple accounts' balance is part of the net worth. Hiding it made
  // the breakdown fail to add up to the headline figure.
  const coupleShare = netWorth ? Number(netWorth.coupleShare ?? 0) : 0
  // The headline is the money you can actually use; investments only appear as a
  // small "with investments" total so they do not blur how much cash there is.
  const withoutInvestments = netWorth ? Number(netWorth.net) - Number(netWorth.investments) : 0

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Inicio</h1>

      {/* Net worth */}
      {nowError && <ErrorState message={nowError} onRetry={() => setReloadKey((k) => k + 1)} />}

      {!nowError && !netWorth && <LoadingState label="Cargando patrimonio…" />}

      {!nowError && netWorth && (
        <section className="rounded-2xl border border-slate-200 bg-white p-6">
          <div className="text-center">
            <p className="text-sm text-slate-500">Patrimonio sin inversiones</p>
            <p
              className={[
                'mt-1 text-4xl font-bold tracking-tight',
                withoutInvestments >= 0 ? 'text-emerald-600' : 'text-red-600',
              ].join(' ')}
            >
              {formatCurrency(withoutInvestments)}
            </p>
          </div>
          {Number(netWorth.investments) !== 0 && (
            <p className="mt-1 text-center text-xs text-slate-400">
              <span>Con inversiones </span>
              <span className="font-medium tabular-nums text-slate-500">{formatCurrency(netWorth.net)}</span>
            </p>
          )}
          <div
            className={[
              'mt-4 grid gap-2 border-t border-slate-100 pt-4',
              coupleShare !== 0 ? 'grid-cols-3' : 'grid-cols-2',
            ].join(' ')}
          >
            <NetWorthStat label="Líquido" value={netWorth.liquid} />
            {coupleShare !== 0 && <NetWorthStat label="Pareja (50%)" value={coupleShare} />}
            <NetWorthStat
              label="Deudas"
              value={debtsNet}
              tone={debtsNet < 0 ? 'negative' : debtsNet > 0 ? 'positive' : 'neutral'}
            />
          </div>
        </section>
      )}

      {/* Net worth evolution */}
      {!nowError && history && history.length >= 2 && <NetWorthChart history={history} />}

      {/* Income vs expense trends */}
      {!nowError && trends && trends.length > 0 && <TrendsChart trends={trends} />}

      {/* Cancellation reminders */}
      {cancellations.length > 0 && (
        <section className="overflow-hidden rounded-2xl border border-amber-200 bg-amber-50">
          <div className="flex items-center gap-2 border-b border-amber-100 px-4 py-3">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 text-amber-500" aria-hidden="true">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" />
              <path d="M12 9v4" />
              <path d="M12 17h.01" />
            </svg>
            <h2 className="text-sm font-semibold text-amber-800">Recordatorios de baja (14 días)</h2>
          </div>
          <ul className="divide-y divide-amber-100">
            {cancellations.map((p) => (
              <li key={p.id} className="flex items-center justify-between gap-3 px-4 py-3">
                <p className="min-w-0 truncate text-sm text-amber-900">
                  Cancelar <span className="font-medium">«{p.name}»</span> antes del {formatDate(p.endDate)}
                </p>
                <Link to="/recurrentes" className="shrink-0 text-xs font-medium text-amber-700 hover:text-amber-900">
                  Ver
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Spending alerts (ant expenses + forgotten subscriptions) */}
      {hasAlerts && (
        <section className="overflow-hidden rounded-2xl border border-amber-200 bg-white">
          <div className="flex items-center gap-2 border-b border-slate-100 px-4 py-3">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4 text-amber-500" aria-hidden="true">
              <path d="M12 9v4" />
              <path d="M12 17h.01" />
              <circle cx="12" cy="12" r="9" />
            </svg>
            <h2 className="text-sm font-semibold text-slate-700">Avisos</h2>
          </div>
          <ul className="divide-y divide-slate-100">
            {antExpenses.map((a) => (
              <li key={`ant-${a.categoryId}`} className="flex items-center gap-3 px-4 py-3">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: a.categoryColor }}
                  aria-hidden="true"
                />
                <p className="min-w-0 flex-1 text-sm text-slate-700">
                  Muchos gastos pequeños en <span className="font-medium">{a.categoryName}</span>:{' '}
                  {a.count} por {formatCurrency(a.total)} este mes
                </p>
              </li>
            ))}
            {forgotten.map((f) => (
              <li key={`forgotten-${f.recurringId}`} className="flex items-center gap-3 px-4 py-3">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: f.categoryColor }}
                  aria-hidden="true"
                />
                <p className="min-w-0 flex-1 text-sm text-slate-700">
                  Pagas <span className="font-medium">«{f.name}»</span> pero no usas {f.categoryName} (sin
                  gastos en 2 meses)
                </p>
                <Link to="/recurrentes" className="shrink-0 text-xs font-medium text-emerald-600 hover:text-emerald-700">
                  Ver
                </Link>
              </li>
            ))}
          </ul>
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
          <ErrorState message={summaryError} onRetry={() => setReloadKey((k) => k + 1)} />
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

            {(summary.fixedExpensePercentage != null || summary.topExpenseCategory) && (
              <div className="grid grid-cols-2 gap-3">
                {summary.fixedExpensePercentage != null && (
                  <section className="rounded-2xl border border-slate-200 bg-white p-4">
                    <p className="text-sm text-slate-500">Gasto fijo</p>
                    <p className="mt-1 text-xl font-semibold text-slate-900">
                      {Number(summary.fixedExpensePercentage).toFixed(0)}%
                    </p>
                    <p className="text-xs text-slate-400">de tus ingresos</p>
                  </section>
                )}
                {summary.topExpenseCategory && (
                  <section className="rounded-2xl border border-slate-200 bg-white p-4">
                    <p className="text-sm text-slate-500">Mayor gasto</p>
                    <p className="mt-1 truncate text-xl font-semibold text-slate-900">
                      {summary.topExpenseCategory.categoryName}
                    </p>
                    <p className="text-xs text-slate-400">
                      {formatCurrency(summary.topExpenseCategory.total)}
                    </p>
                  </section>
                )}
              </div>
            )}

            <CategoryBreakdown title="Gastos por categoría" items={expenses} prevById={prevById} type="EXPENSE" />
            <CategoryBreakdown title="Ingresos por categoría" items={incomes} prevById={prevById} type="INCOME" />
          </>
        )}
      </section>

      {/* Quick-add floating action button */}
      <button
        type="button"
        onClick={() => setFormOpen(true)}
        aria-label="Nuevo movimiento"
        className="fixed bottom-20 right-4 z-20 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-600 text-white shadow-lg transition-colors hover:bg-emerald-700 md:bottom-6"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-6 w-6" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>

      {formOpen && (
        <TransactionForm
          categories={categories}
          transaction={null}
          onClose={() => setFormOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </div>
  )
}
