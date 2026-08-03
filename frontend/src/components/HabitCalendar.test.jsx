import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import HabitCalendar from './HabitCalendar'

describe('HabitCalendar', () => {
  it('draws one cell per day of the window', () => {
    render(<HabitCalendar entries={[]} weeks={4} endDate="2026-08-03" />)
    expect(screen.getAllByTestId('habit-day')).toHaveLength(28)
  })

  it('marks the completed days', () => {
    render(
      <HabitCalendar
        entries={[{ date: '2026-08-03', value: null, done: true }]}
        weeks={1}
        endDate="2026-08-03"
      />,
    )
    expect(screen.getAllByTestId('habit-day').filter((c) => c.dataset.done === 'true')).toHaveLength(1)
  })
})
