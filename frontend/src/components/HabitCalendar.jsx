// GitHub-style calendar for a habit. Hand-drawn SVG: the project has no chart
// library and this module does not add one.
const CELL = 14
const GAP = 3

function isoDate(date) {
  return date.toLocaleDateString('sv-SE') // sv-SE renders as YYYY-MM-DD
}

export default function HabitCalendar({ entries, weeks = 12, endDate }) {
  const end = endDate ? new Date(`${endDate}T00:00:00`) : new Date()
  const days = weeks * 7
  const doneByDate = new Map(entries.map((entry) => [entry.date, entry.done]))

  const cells = []
  for (let offset = days - 1; offset >= 0; offset--) {
    const day = new Date(end)
    day.setDate(end.getDate() - offset)
    const key = isoDate(day)
    cells.push({ key, done: doneByDate.get(key) === true })
  }

  const width = weeks * (CELL + GAP)
  const height = 7 * (CELL + GAP)

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full max-w-md" role="img"
         aria-label={`Últimas ${weeks} semanas`}>
      {cells.map((cell, index) => (
        <rect
          key={cell.key}
          data-testid="habit-day"
          data-done={String(cell.done)}
          x={Math.floor(index / 7) * (CELL + GAP)}
          y={(index % 7) * (CELL + GAP)}
          width={CELL}
          height={CELL}
          rx="3"
          className={cell.done ? 'fill-emerald-500' : 'fill-slate-200'}
        >
          <title>{cell.key}</title>
        </rect>
      ))}
    </svg>
  )
}
