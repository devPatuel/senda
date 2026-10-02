// Month overview for a couple space: what came in as transfers, what was
// actually spent, and where it went. CSS bars (no chart lib), matching the
// breakdown bars used elsewhere in the app.
import { useEffect, useState } from 'react'
import { getSummary } from '../api/transactions'
import { listAccounts } from '../api/accounts'
import { getBudget } from '../api/categories'
import { formatCurrency, formatMonthLabel } from '../lib/format'
import { ErrorState } from './ui'
import CategoryAvailability from './CategoryAvailability'
import MonthComparison from './MonthComparison'
import SpendingPace from './SpendingPace'
import YearOverview from './YearOverview'

function currentYearMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

function Stat({ label, value, hint, tone = 'neutral' }) {
  const valueClasses = {
    positive: 'text-emerald-600',
    negative: 'text-red-600',
    neutral: 'text-slate-900',
  }[tone]
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={['mt-1 text-xl font-semibold tabular-nums', valueClasses].join(' ')}>
        {formatCurrency(value)}
      </p>
      {hint && <p className="text-xs text-slate-400">{hint}</p>}
    </section>
  )
}

export default function SpaceSummary({ spaceId, onAssign }) {
  const [{ year, month }, setYearMonth] = useState(currentYearMonth)
  const [summary, setSummary] = useState(null)
  const [balance, setBalance] = useState(null)
  const [budget, setBudget] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    getSummary(year, month, spaceId)
      .then((data) => {
        if (!cancelled) {
          setSummary(data)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se ha podido cargar el resumen')
      })
    return () => {
      cancelled = true
    }
  }, [spaceId, year, month])

  // Fetched here rather than inside CategoryAvailability because the pace block
  // needs the assigned total from the same payload.
  useEffect(() => {
    let cancelled = false
    getBudget(spaceId)
      .then((data) => {
        if (!cancelled) setBudget(data)
      })
      .catch(() => {
        // The envelope blocks drop out; the month figures still stand
      })
    return () => {
      cancelled = true
    }
  }, [spaceId])

  useEffect(() => {
    let cancelled = false
    listAccounts({ spaceId })
      .then((accounts) => {
        if (cancelled) return
        setBalance(accounts.reduce((sum, a) => sum + Number(a.balance), 0))
      })
      .catch(() => {
        // The balance tile is simply omitted
      })
    return () => {
      cancelled = true
    }
  }, [spaceId])

  function shiftMonth(delta) {
    setYearMonth((prev) => {
      const next = prev.month + delta
      if (next < 1) return { year: prev.year - 1, month: 12 }
      if (next > 12) return { year: prev.year + 1, month: 1 }
      return { year: prev.year, month: next }
    })
  }

  if (error) return <ErrorState message={error} />
  if (!summary) return null

  const totalExpense = Number(summary.totalExpense)
  const transfersIn = Number(summary.transfersIn ?? 0)
  const expenses = summary.byCategory
    .filter((c) => c.type === 'EXPENSE' && !c.transfer)
    .sort((a, b) => Number(b.total) - Number(a.total))

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {balance != null && <Stat label="Saldo en cuentas" value={balance} />}
        <Stat label="Gastado" value={totalExpense} tone={totalExpense > 0 ? 'negative' : 'neutral'} />
        <Stat label="Aportado" value={transfersIn} hint="traspasos vuestros" />
        <Stat
          label="Gasto fijo"
          value={summary.fixedExpenseTotal}
          hint={`variable ${formatCurrency(summary.variableExpenseTotal)}`}
        />
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold text-slate-700">Gasto por categoría</h2>
          <div className="flex items-center gap-1">
            <button
              type="button"
              aria-label="Mes anterior"
              onClick={() => shiftMonth(-1)}
              className="rounded-lg border border-slate-200 px-2 py-1 text-sm text-slate-600 transition-colors hover:bg-slate-100"
            >
              ‹
            </button>
            <span className="min-w-28 text-center text-sm font-medium text-slate-700">
              {formatMonthLabel(year, month)}
            </span>
            <button
              type="button"
              aria-label="Mes siguiente"
              onClick={() => shiftMonth(1)}
              className="rounded-lg border border-slate-200 px-2 py-1 text-sm text-slate-600 transition-colors hover:bg-slate-100"
            >
              ›
            </button>
          </div>
        </div>

        {expenses.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">Sin gastos este mes.</p>
        ) : (
          <ul className="space-y-3">
            {expenses.map((c) => {
              // Share of the month's spending, not of the widest bar: the number
              // next to the bar has to mean the same thing as its length.
              const share = totalExpense > 0 ? Math.round((Number(c.total) / totalExpense) * 100) : 0
              return (
                <li
                  key={c.categoryId}
                  data-testid="category-bar"
                  data-category={c.categoryName}
                  data-share={String(share)}
                >
                  <div className="flex items-baseline justify-between gap-3 text-sm">
                    <span className="flex min-w-0 items-center gap-2">
                      {c.categoryEmoji && <span aria-hidden="true">{c.categoryEmoji}</span>}
                      <span className="truncate text-slate-700">{c.categoryName}</span>
                      {c.fixed && (
                        <span className="shrink-0 rounded-full bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500">
                          fijo
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 tabular-nums text-slate-900">{formatCurrency(c.total)}</span>
                  </div>
                  <div className="mt-1 flex items-center gap-2">
                    <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                      <div
                        className="h-full rounded-full"
                        style={{ width: `${share}%`, backgroundColor: c.categoryColor }}
                        title={`${c.categoryName}: ${formatCurrency(c.total)} (${share}%)`}
                      />
                    </div>
                    <span className="w-9 shrink-0 text-right text-xs tabular-nums text-slate-400">{share}%</span>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {budget && (
        <SpendingPace
          spent={totalExpense}
          fixed={summary.fixedExpenseTotal}
          assigned={budget.totalAssigned}
          year={year}
          month={month}
        />
      )}

      <MonthComparison year={year} month={month} spaceId={spaceId} />

      {budget && Number(budget.toAssign) !== 0 && (
        <ToAssignNotice amount={Number(budget.toAssign)} onAssign={onAssign} />
      )}

      {budget && <CategoryAvailability spaceId={spaceId} budget={budget} />}

      <YearOverview year={year} spaceId={spaceId} />
    </div>
  )
}

// Contributions land here first: money that is in the space but in no envelope
// yet. Negative means the envelopes promise more than was ever contributed.
function ToAssignNotice({ amount, onAssign }) {
  const negative = amount < 0
  return (
    <section
      data-testid="to-assign"
      className={[
        'flex flex-wrap items-center justify-between gap-3 rounded-2xl border p-5',
        negative ? 'border-red-200 bg-red-50' : 'border-emerald-200 bg-emerald-50',
      ].join(' ')}
    >
      <div>
        <p className={['text-xs', negative ? 'text-red-700' : 'text-emerald-700'].join(' ')}>
          {negative ? 'Asignado de más' : 'Por asignar'}
        </p>
        <p
          className={[
            'mt-1 text-xl font-semibold tabular-nums',
            negative ? 'text-red-600' : 'text-emerald-700',
          ].join(' ')}
        >
          {formatCurrency(amount)}
        </p>
        <p className="mt-1 text-xs text-slate-500">
          {negative
            ? 'Los sobres guardan más de lo que habéis aportado: saca dinero de alguno.'
            : 'Aportado que todavía no está en ninguna categoría.'}
        </p>
      </div>
      {onAssign && (
        <button
          type="button"
          onClick={onAssign}
          className="rounded-lg bg-emerald-600 px-3.5 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-700"
        >
          Repartir en categorías
        </button>
      )}
    </section>
  )
}
