// This month against the previous one. A single number in isolation says
// little; the same number next to last month's says whether things are going
// better or worse.
import { useEffect, useState } from 'react'
import { getSummary } from '../api/transactions'
import { formatCurrency, formatMonthLabel } from '../lib/format'

function previousMonth(year, month) {
  return month === 1 ? { year: year - 1, month: 12 } : { year, month: month - 1 }
}

export default function MonthComparison({ year, month, spaceId }) {
  const [pair, setPair] = useState(null)

  useEffect(() => {
    let cancelled = false
    const prev = previousMonth(year, month)
    Promise.all([getSummary(year, month, spaceId), getSummary(prev.year, prev.month, spaceId)])
      .then(([current, previous]) => {
        if (!cancelled) setPair({ current, previous, prev })
      })
      .catch(() => {
        // The comparison is an extra: if it cannot load, the rest of the
        // summary still works and the block simply stays out.
      })
    return () => {
      cancelled = true
    }
  }, [year, month, spaceId])

  if (!pair) return null

  const now = Number(pair.current.totalExpense)
  const before = Number(pair.previous.totalExpense)
  const diff = now - before
  // With nothing to compare against, a percentage would be meaningless
  // (everything is "+infinity% more than zero").
  const direction = before === 0 ? 'none' : diff > 0 ? 'up' : diff < 0 ? 'down' : 'same'
  const percentage = before === 0 ? null : Math.abs(Math.round((diff / before) * 100))

  const tone = {
    up: 'text-red-600',
    down: 'text-emerald-600',
    same: 'text-slate-500',
    none: 'text-slate-500',
  }[direction]

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <h2 className="mb-4 text-sm font-semibold text-slate-700">Comparativa con el mes anterior</h2>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <p className="text-xs text-slate-400">{formatMonthLabel(year, month)}</p>
          <p className="mt-1 text-xl font-semibold tabular-nums text-slate-900">{formatCurrency(now)}</p>
        </div>
        <div>
          <p className="text-xs text-slate-400">{formatMonthLabel(pair.prev.year, pair.prev.month)}</p>
          <p className="mt-1 text-xl font-semibold tabular-nums text-slate-500">{formatCurrency(before)}</p>
        </div>
      </div>

      <p
        data-testid="expense-change"
        data-direction={direction}
        className={['mt-3 text-sm font-medium', tone].join(' ')}
      >
        {direction === 'none'
          ? 'Sin gasto el mes anterior, no hay con qué comparar.'
          : direction === 'same'
            ? 'Mismo gasto que el mes anterior.'
            : `${diff > 0 ? 'Has gastado' : 'Has gastado'} ${formatCurrency(Math.abs(diff))} ${
                diff > 0 ? 'más' : 'menos'
              } (${percentage}%)`}
      </p>
    </section>
  )
}
