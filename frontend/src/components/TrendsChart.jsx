// Income-vs-expense bar chart for the last N months. CSS bars (no chart lib),
// matching the breakdown bars used elsewhere in the app.
import { formatCurrency } from '../lib/format'

const MONTH_ABBR = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

export default function TrendsChart({ trends }) {
  if (!trends || trends.length === 0) return null
  // Guard against an all-zero series so bars never divide by zero.
  const max = Math.max(1, ...trends.flatMap((t) => [Number(t.income), Number(t.expense)]))

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">Tendencias ({trends.length} meses)</h2>
        <div className="flex items-center gap-3 text-xs text-slate-500">
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-emerald-500" aria-hidden="true" />
            Ingresos
          </span>
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full bg-red-400" aria-hidden="true" />
            Gastos
          </span>
        </div>
      </div>
      <div className="flex items-end gap-2">
        {trends.map((t) => (
          <div key={`${t.year}-${t.month}`} className="flex min-w-0 flex-1 flex-col items-center gap-1">
            <div className="flex h-32 w-full items-end justify-center gap-0.5">
              <div
                className="w-1/2 rounded-t bg-emerald-500"
                style={{ height: `${(Number(t.income) / max) * 100}%` }}
                title={`Ingresos ${formatCurrency(t.income)}`}
              />
              <div
                className="w-1/2 rounded-t bg-red-400"
                style={{ height: `${(Number(t.expense) / max) * 100}%` }}
                title={`Gastos ${formatCurrency(t.expense)}`}
              />
            </div>
            <span className="text-[10px] tabular-nums text-slate-400">{MONTH_ABBR[t.month - 1]}</span>
          </div>
        ))}
      </div>
    </section>
  )
}
