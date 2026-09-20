// What is left in each category envelope: assigned minus everything spent.
// A negative row is not an error — it is how much has to be moved in to cover
// the category, so it is shown as a figure to act on, not hidden.
import { useEffect, useState } from 'react'
import { getBudget } from '../api/categories'
import { formatCurrency } from '../lib/format'
import { ErrorState } from './ui'

export default function CategoryAvailability({ spaceId, budget: given }) {
  const [fetched, setFetched] = useState(null)
  const [error, setError] = useState(null)
  // The page above may already hold the budget (it needs the totals for the
  // pace block); reuse it rather than asking for the same thing twice.
  const budget = given ?? fetched

  useEffect(() => {
    if (given) return undefined
    let cancelled = false
    getBudget(spaceId)
      .then((data) => {
        if (!cancelled) {
          setFetched(data)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'No se ha podido cargar el presupuesto')
      })
    return () => {
      cancelled = true
    }
  }, [spaceId, given])

  if (error) return <ErrorState message={error} />
  if (!budget) return null

  const categories = [...budget.categories].sort(
    (a, b) => Number(a.available) - Number(b.available),
  )
  const overspent = categories
    .filter((c) => Number(c.available) < 0)
    .reduce((sum, c) => sum + Math.abs(Number(c.available)), 0)

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <h2 className="mb-4 text-sm font-semibold text-slate-700">Disponible por categoría</h2>

      {overspent > 0 && (
        <p
          data-testid="overspent-notice"
          className="mb-4 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700"
        >
          Te faltan {formatCurrency(overspent)} por cubrir en las categorías en rojo.
        </p>
      )}

      {categories.length === 0 ? (
        <p className="py-6 text-center text-sm text-slate-500">Todavía no hay categorías de gasto.</p>
      ) : (
        <ul className="space-y-3">
          {categories.map((c) => {
            const assigned = Number(c.balance)
            const spent = Number(c.spent)
            const available = Number(c.available)
            const negative = available < 0
            // Share of the envelope already used. With nothing assigned, any
            // spending is 100% used: the bar fills rather than dividing by zero.
            const used = assigned > 0 ? Math.min(100, Math.round((spent / assigned) * 100)) : spent > 0 ? 100 : 0
            return (
              <li
                key={c.id}
                data-testid={`availability-${c.id}`}
                data-available={String(available)}
                data-negative={String(negative)}
              >
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="truncate text-slate-700">{c.name}</span>
                  <span
                    className={[
                      'shrink-0 font-medium tabular-nums',
                      negative ? 'text-red-600' : 'text-slate-900',
                    ].join(' ')}
                  >
                    {formatCurrency(available)}
                  </span>
                </div>
                <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className="h-full rounded-full"
                    style={{
                      width: `${used}%`,
                      backgroundColor: negative ? '#dc2626' : c.color,
                    }}
                    title={`${c.name}: ${formatCurrency(spent)} de ${formatCurrency(assigned)}`}
                  />
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {formatCurrency(spent)} gastado de {formatCurrency(assigned)}
                </p>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
