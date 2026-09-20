// The year at a glance: totals, monthly average and a bar per month. Twelve
// bars scaled against the worst month show the shape of the year — which months
// blow up and which carry it — in a way a single total never can.
import { useEffect, useState } from 'react'
import { getYearSummary } from '../api/transactions'
import { formatCurrency } from '../lib/format'
import { ErrorState } from './ui'

const MONTH_INITIALS = ['E', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D']

export default function YearOverview({ year: initialYear, spaceId }) {
  const [year, setYear] = useState(initialYear)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    getYearSummary(year, spaceId)
      .then((res) => {
        if (!cancelled) {
          setData(res)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se ha podido cargar el año')
      })
    return () => {
      cancelled = true
    }
  }, [year, spaceId])

  if (error) return <ErrorState message={error} />
  if (!data) return null

  const worst = Math.max(...data.months.map((m) => Number(m.expense)), 0)

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">El año</h2>
        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label="Año anterior"
            onClick={() => setYear((y) => y - 1)}
            className="rounded-lg border border-slate-200 px-2 py-1 text-sm text-slate-600 transition-colors hover:bg-slate-100"
          >
            ‹
          </button>
          <span className="min-w-14 text-center text-sm font-medium text-slate-700">{year}</span>
          <button
            type="button"
            aria-label="Año siguiente"
            onClick={() => setYear((y) => y + 1)}
            className="rounded-lg border border-slate-200 px-2 py-1 text-sm text-slate-600 transition-colors hover:bg-slate-100"
          >
            ›
          </button>
        </div>
      </div>

      <div className="mb-5 grid grid-cols-3 gap-4">
        <div>
          <p className="text-xs text-slate-400">Gastado</p>
          <p data-testid="year-expense" className="mt-1 text-lg font-semibold tabular-nums text-slate-900">
            {formatCurrency(data.totalExpense)}
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Ingresado</p>
          <p className="mt-1 text-lg font-semibold tabular-nums text-slate-900">
            {formatCurrency(data.totalIncome)}
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Media al mes</p>
          <p data-testid="year-average" className="mt-1 text-lg font-semibold tabular-nums text-slate-900">
            {formatCurrency(data.monthlyAverageExpense)}
          </p>
        </div>
      </div>

      <div className="flex h-32 items-end gap-1.5">
        {data.months.map((m) => {
          const expense = Number(m.expense)
          const height = worst > 0 ? Math.round((expense / worst) * 100) : 0
          return (
            <div key={m.month} className="flex flex-1 flex-col items-center gap-1">
              <div className="flex h-24 w-full items-end">
                <div
                  data-testid={`month-bar-${m.month}`}
                  data-height={String(height)}
                  className="w-full rounded-t bg-emerald-500"
                  style={{ height: `${height}%` }}
                  title={`${MONTH_INITIALS[m.month - 1]}: ${formatCurrency(expense)}`}
                />
              </div>
              <span className="text-[10px] text-slate-400">{MONTH_INITIALS[m.month - 1]}</span>
            </div>
          )
        })}
      </div>
    </section>
  )
}
