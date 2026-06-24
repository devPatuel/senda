// Net-worth evolution as a simple SVG line chart. Needs at least two points to
// draw a line; a single snapshot renders nothing (handled by the caller).
import { formatCurrency, formatDate } from '../lib/format'

const W = 300
const H = 90
const PAD = 8

export default function NetWorthChart({ history }) {
  if (!history || history.length < 2) return null

  const values = history.map((p) => Number(p.net))
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1 // avoid /0 when every value is equal

  const x = (i) => PAD + (i / (history.length - 1)) * (W - 2 * PAD)
  const y = (v) => PAD + (1 - (v - min) / span) * (H - 2 * PAD)

  const points = values.map((v, i) => `${x(i)},${y(v)}`).join(' ')
  const areaPoints = `${x(0)},${H - PAD} ${points} ${x(values.length - 1)},${H - PAD}`
  const last = history[history.length - 1]
  const first = history[0]

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">Evolución del patrimonio</h2>
        <span className="text-xs text-slate-400">{history.length} días</span>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="h-28 w-full" role="img" aria-label="Evolución del patrimonio neto">
        <polygon points={areaPoints} fill="rgb(16 185 129 / 0.12)" />
        <polyline
          points={points}
          fill="none"
          stroke="rgb(16 185 129)"
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <div className="mt-2 flex items-center justify-between text-xs text-slate-400">
        <span>{formatDate(first.date)}</span>
        <span className="font-semibold text-slate-700">{formatCurrency(last.net)}</span>
        <span>{formatDate(last.date)}</span>
      </div>
    </section>
  )
}
