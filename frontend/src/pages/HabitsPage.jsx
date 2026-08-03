// Habits page: what is due today, one tap away, plus the create/edit form.
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { clearEntry, createHabit, getToday, incrementEntry, recordEntry } from '../api/habits'
import { Field, FormError, SubmitButton } from '../components/form'
import { EmptyState, ErrorState, LoadingState, Modal, SelectField } from '../components/ui'

/** sv-SE renders as YYYY-MM-DD, which is what the API expects, in local time. */
function todayIso() {
  return new Date().toLocaleDateString('sv-SE')
}

const DAY_LABELS = ['L', 'M', 'X', 'J', 'V', 'S', 'D']

function HabitModal({ onClose, onSaved }) {
  const [form, setForm] = useState({
    name: '',
    emoji: '',
    type: 'CHECK',
    target: '',
    unit: '',
    scheduleType: 'WEEKDAYS',
    weekdays: [true, true, true, true, true, true, true],
    intervalDays: '2',
    weeklyTarget: '3',
  })
  const [errors, setErrors] = useState({})
  const [apiError, setApiError] = useState(null)
  const [loading, setLoading] = useState(false)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const toggleDay = (index) =>
    setForm((f) => ({
      ...f,
      weekdays: f.weekdays.map((on, i) => (i === index ? !on : on)),
    }))

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.name.trim()) {
      setErrors({ name: 'El nombre es obligatorio' })
      return
    }
    if (form.scheduleType === 'WEEKDAYS' && !form.weekdays.some(Boolean)) {
      setErrors({ weekdays: 'Marca al menos un día' })
      return
    }
    setErrors({})
    setLoading(true)
    setApiError(null)
    try {
      // The fields of the modes not chosen travel as null: sending them filled would
      // suggest they are stored, and the backend drops them anyway.
      await createHabit({
        name: form.name.trim(),
        emoji: form.emoji.trim() || null,
        type: form.type,
        target: form.type === 'COUNTER' ? Number(form.target) : null,
        unit: form.type === 'CHECK' ? null : form.unit.trim() || null,
        scheduleType: form.scheduleType,
        weekdays:
          form.scheduleType === 'WEEKDAYS'
            ? form.weekdays.map((on) => (on ? '1' : '0')).join('')
            : null,
        intervalDays: form.scheduleType === 'INTERVAL' ? Number(form.intervalDays) : null,
        weeklyTarget: form.scheduleType === 'WEEKLY_COUNT' ? Number(form.weeklyTarget) : null,
        sortOrder: 0,
      })
      onSaved()
    } catch (err) {
      setApiError(err?.message ?? 'Error al guardar')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal title="Nuevo hábito" onClose={onClose} dismissable={!loading}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <FormError message={apiError} />
        <Field label="Nombre" name="name" value={form.name} onChange={set('name')} error={errors.name} />
        <Field label="Emoji" name="emoji" value={form.emoji} onChange={set('emoji')} placeholder="📖" />

        <SelectField label="Tipo" name="type" value={form.type} onChange={set('type')}>
          <option value="CHECK">Hecho o no</option>
          <option value="COUNTER">Contador con objetivo</option>
          <option value="MEASURE">Medición (guarda un número)</option>
        </SelectField>

        {form.type === 'COUNTER' && (
          <Field label="Objetivo" name="target" type="number" step="0.01" min="0.01"
                 value={form.target} onChange={set('target')} />
        )}
        {form.type !== 'CHECK' && (
          <Field label="Unidad" name="unit" value={form.unit} onChange={set('unit')}
                 placeholder="vasos, min, kg…" />
        )}

        <SelectField label="Cuándo toca" name="scheduleType" value={form.scheduleType}
                     onChange={set('scheduleType')}>
          <option value="WEEKDAYS">Días de la semana</option>
          <option value="INTERVAL">Cada N días</option>
          <option value="WEEKLY_COUNT">N veces por semana</option>
        </SelectField>

        {form.scheduleType === 'WEEKDAYS' && (
          <div>
            <span className="mb-1.5 block text-sm font-medium text-slate-700">Días</span>
            <div className="flex gap-1.5">
              {DAY_LABELS.map((label, index) => (
                <button
                  key={label}
                  type="button"
                  aria-pressed={form.weekdays[index]}
                  aria-label={`Día ${label}`}
                  onClick={() => toggleDay(index)}
                  className={[
                    'h-9 w-9 rounded-lg border text-sm',
                    form.weekdays[index]
                      ? 'border-emerald-500 bg-emerald-500 text-white'
                      : 'border-slate-300 bg-white text-slate-600',
                  ].join(' ')}
                >
                  {label}
                </button>
              ))}
            </div>
            {errors.weekdays && <p className="mt-1.5 text-sm text-red-600">{errors.weekdays}</p>}
          </div>
        )}

        {form.scheduleType === 'INTERVAL' && (
          <Field label="Cada cuántos días" name="intervalDays" type="number" min="1" max="365"
                 value={form.intervalDays} onChange={set('intervalDays')} />
        )}
        {form.scheduleType === 'WEEKLY_COUNT' && (
          <Field label="Veces por semana" name="weeklyTarget" type="number" min="1" max="7"
                 value={form.weeklyTarget} onChange={set('weeklyTarget')} />
        )}

        <SubmitButton loading={loading} loadingText="Guardando…">Guardar</SubmitButton>
      </form>
    </Modal>
  )
}

function HabitRow({ habit, onChanged }) {
  const [busy, setBusy] = useState(false)
  const [measure, setMeasure] = useState(habit.value != null ? String(habit.value) : '')

  async function run(action) {
    setBusy(true)
    try {
      await action()
      await onChanged()
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="flex items-center gap-3 rounded-lg border border-slate-200 p-3">
      <span aria-hidden="true" className="text-xl">{habit.emoji}</span>

      <Link to={`/habitos/${habit.id}`} className="flex-1 font-medium hover:underline">
        {habit.name}
      </Link>

      {habit.type === 'COUNTER' && (
        <span className="text-sm text-slate-500">
          {`${habit.value ?? 0} / ${habit.target} ${habit.unit ?? ''}`.trim()}
        </span>
      )}

      <span data-testid={`streak-${habit.id}`} className="text-sm text-slate-500">
        🔥 {habit.currentStreak}
      </span>

      {habit.type === 'CHECK' && (
        <button
          type="button"
          disabled={busy}
          aria-label={habit.done ? `Desmarcar ${habit.name}` : `Marcar ${habit.name}`}
          className={habit.done
            ? 'rounded bg-emerald-500 px-3 py-1 text-white'
            : 'rounded border border-slate-300 px-3 py-1'}
          onClick={() => run(() => (habit.done
            ? clearEntry(habit.id, todayIso())
            : recordEntry(habit.id, todayIso(), null)))}
        >
          ✓
        </button>
      )}

      {habit.type === 'COUNTER' && (
        <button
          type="button"
          disabled={busy}
          aria-label={`Sumar uno a ${habit.name}`}
          className="rounded border border-slate-300 px-3 py-1"
          onClick={() => run(() => incrementEntry(habit.id, todayIso(), 1))}
        >
          +1
        </button>
      )}

      {habit.type === 'MEASURE' && (
        <form
          className="flex items-center gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (measure === '') return
            run(() => recordEntry(habit.id, todayIso(), Number(measure)))
          }}
        >
          <input
            type="number"
            step="0.1"
            aria-label={`Valor de ${habit.name}`}
            className="w-24 rounded border border-slate-300 px-2 py-1"
            value={measure}
            onChange={(event) => setMeasure(event.target.value)}
          />
          <button type="submit" disabled={busy} className="rounded border border-slate-300 px-3 py-1">
            Guardar
          </button>
        </form>
      )}
    </li>
  )
}

export default function HabitsPage() {
  const [habits, setHabits] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [creating, setCreating] = useState(false)

  const load = useCallback(async () => {
    try {
      setHabits(await getToday())
      setError(null)
    } catch (err) {
      setError(err?.message ?? 'Error al cargar')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  if (loading) return <LoadingState />
  if (error) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Hábitos de hoy</h1>
        <button
          type="button"
          onClick={() => setCreating(true)}
          className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white"
        >
          Nuevo hábito
        </button>
      </div>

      {habits.length === 0 ? (
        <EmptyState title="Nada pendiente por hoy" />
      ) : (
        <ul className="space-y-2">
          {habits.map((habit) => (
            <HabitRow key={habit.id} habit={habit} onChanged={load} />
          ))}
        </ul>
      )}

      {creating && (
        <HabitModal
          onClose={() => setCreating(false)}
          onSaved={() => { setCreating(false); load() }}
        />
      )}
    </div>
  )
}
