// The year month by month: what went out and what was put in, side by side.
// Horizontal bars because the amount is written on the bar itself, and twelve
// vertical bars leave no room to read a figure.
import { useEffect, useState } from 'react'
import { getYearSummary } from '../api/transactions'
import { formatCurrency } from '../lib/format'
import { ErrorState } from './ui'

const MONTHS_ES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic']

function Bar({ testId, amount, max, color }) {
  const value = Number(amount)
  // Both kinds of bar share one scale: otherwise a small figure on its own
  // scale would look the same size as a big one, and the comparison lies.
  const width = max > 0 ? Math.round((value / max) * 100) : 0
  return (
    <div className="h-5 flex-1 overflow-hidden rounded bg-slate-50">
      <div
        data-testid={testId}
        data-width={String(width)}
        className={[
          'flex h-full items-center justify-end rounded px-1.5 text-[10px] font-medium tabular-nums',
          width > 45 ? 'text-white' : 'text-slate-500',
        ].join(' ')}
        // A zero-width bar still has to show its figure outside, so the label
        // sits in the same box and simply has no fill behind it.
        style={{ width: `${Math.max(width, value > 0 ? 12 : 0)}%`, backgroundColor: value > 0 ? color : 'transparent' }}
      >
        {value > 0 ? formatCurrency(value) : ''}
      </div>
    </div>
  )
}

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

  const max = Math.max(
    ...data.months.map((m) => Math.max(Number(m.expense), Number(m.transfersIn))),
    0,
  )

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

      <div className="mb-5 grid grid-cols-2 gap-4">
        <div>
          <p className="text-xs text-slate-400">Gastado</p>
          <p data-testid="year-expense" className="mt-1 text-lg font-semibold tabular-nums text-red-600">
            {formatCurrency(data.totalExpense)}
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-400">Aportado</p>
          <p data-testid="year-transfers" className="mt-1 text-lg font-semibold tabular-nums text-emerald-600">
            {formatCurrency(data.totalTransfersIn)}
          </p>
        </div>
      </div>

      <ul className="space-y-1.5">
        {data.months.map((m) => (
          <li key={m.month} data-testid={`month-row-${m.month}`} className="flex items-center gap-2">
            <span className="w-8 shrink-0 text-[11px] text-slate-400">{MONTHS_ES[m.month - 1]}</span>
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <Bar testId={`bar-expense-${m.month}`} amount={m.expense} max={max} color="#ef4444" />
              <Bar testId={`bar-transfers-${m.month}`} amount={m.transfersIn} max={max} color="#10b981" />
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
