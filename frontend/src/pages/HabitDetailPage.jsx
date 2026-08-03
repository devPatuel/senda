// Detail of one habit: streaks, completion rate, 12-week calendar and, for MEASURE
// habits, the evolution of the recorded number. All SVG, no chart library.
import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getHistory } from '../api/habits'
import HabitCalendar from '../components/HabitCalendar'
import { ErrorState, LoadingState } from '../components/ui'

const WEEKS = 12

function isoDate(date) {
  return date.toLocaleDateString('sv-SE')
}

function Figure({ label, value }) {
  return (
    <div className="rounded-lg border border-slate-200 p-3 text-center">
      <div className="text-2xl font-semibold">{value}</div>
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  )
}

function MeasureChart({ entries }) {
  const points = entries.filter((entry) => entry.value != null)
  if (points.length < 2) return null

  const values = points.map((point) => Number(point.value))
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1

  const path = points
    .map((point, index) => {
      const x = (index / (points.length - 1)) * 100
      const y = 30 - ((Number(point.value) - min) / span) * 28
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')

  return (
    <svg viewBox="0 0 100 30" className="w-full max-w-md" role="img" aria-label="Evolución">
      <polyline points={path} fill="none" stroke="currentColor" strokeWidth="1"
                className="text-emerald-600" />
    </svg>
  )
}

export default function HabitDetailPage() {
  const { id } = useParams()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    const to = new Date()
    const from = new Date()
    from.setDate(to.getDate() - WEEKS * 7 + 1)
    try {
      setData(await getHistory(id, isoDate(from), isoDate(to)))
      setError(null)
    } catch (err) {
      setError(err?.message ?? 'Error al cargar')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { load() }, [load])

  if (loading) return <LoadingState />
  if (error) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="space-y-6">
      <h1 className="flex items-center gap-2 text-xl font-semibold">
        <span aria-hidden="true">{data.habit.emoji}</span>
        {data.habit.name}
      </h1>

      <div className="grid max-w-md grid-cols-3 gap-3">
        <Figure label="Racha actual" value={data.currentStreak} />
        <Figure label="Mejor racha" value={data.bestStreak} />
        <Figure label="Cumplimiento" value={`${data.completionRate} %`} />
      </div>

      <HabitCalendar entries={data.entries} weeks={WEEKS} endDate={isoDate(new Date())} />

      {data.habit.type === 'MEASURE' && <MeasureChart entries={data.entries} />}
    </div>
  )
}
