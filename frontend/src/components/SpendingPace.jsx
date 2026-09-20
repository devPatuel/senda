// Spending pace for the month in progress: what today's rate would add up to by
// the last day. Knowing you have spent 500 means little on its own; knowing it
// projects to 1500 against 1000 assigned is what lets you change course.
import { formatCurrency } from '../lib/format'

export default function SpendingPace({ spent, assigned, year, month }) {
  const now = new Date()
  const isCurrentMonth = now.getFullYear() === year && now.getMonth() + 1 === month
  const daysInMonth = new Date(year, month, 0).getDate()
  const dayOfMonth = now.getDate()
  const daysLeft = daysInMonth - dayOfMonth

  const spentNumber = Number(spent)
  const assignedNumber = Number(assigned)
  // Projecting a month that is already over would just restate its total.
  const projected = isCurrentMonth ? (spentNumber / dayOfMonth) * daysInMonth : null
  const status = projected != null && assignedNumber > 0 && projected > assignedNumber ? 'over' : 'ok'

  return (
    <section
      className="rounded-2xl border border-slate-200 bg-white p-5"
      data-testid="pace"
      data-status={status}
    >
      <h2 className="mb-4 text-sm font-semibold text-slate-700">Ritmo de gasto</h2>

      <div className="flex flex-wrap items-baseline gap-x-6 gap-y-2">
        <div>
          <p className="text-xs text-slate-400">Llevas gastado</p>
          <p className="mt-1 text-xl font-semibold tabular-nums text-slate-900">
            {formatCurrency(spentNumber)}
          </p>
        </div>

        {projected != null && (
          <div>
            <p className="text-xs text-slate-400">Acabarás el mes en</p>
            <p
              data-testid="projected"
              data-value={String(Math.round(projected))}
              className={[
                'mt-1 text-xl font-semibold tabular-nums',
                status === 'over' ? 'text-red-600' : 'text-slate-900',
              ].join(' ')}
            >
              {formatCurrency(projected)}
            </p>
          </div>
        )}

        {isCurrentMonth && (
          <p data-testid="days-left" className="text-sm text-slate-500">
            Quedan {daysLeft} días
          </p>
        )}
      </div>

      {status === 'over' && (
        <p className="mt-3 text-sm text-red-700">
          A este ritmo te pasas {formatCurrency(projected - assignedNumber)} de lo asignado.
        </p>
      )}
    </section>
  )
}
