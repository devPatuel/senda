import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HabitsPage from './HabitsPage'

vi.mock('../api/habits', () => ({
  getToday: vi.fn(),
  getHabits: vi.fn(),
  createHabit: vi.fn(),
  updateHabit: vi.fn(),
  archiveHabit: vi.fn(),
  removeHabit: vi.fn(),
  recordEntry: vi.fn(),
  incrementEntry: vi.fn(),
  clearEntry: vi.fn(),
  getHistory: vi.fn(),
}))

import { getToday, incrementEntry, recordEntry } from '../api/habits'

const TODAY = [
  { id: 1, name: 'Meditar', emoji: '🧘', type: 'CHECK', target: null, unit: null, value: null, done: false, currentStreak: 4 },
  { id: 2, name: 'Agua', emoji: '💧', type: 'COUNTER', target: 8, unit: 'vasos', value: 5, done: false, currentStreak: 2 },
  { id: 3, name: 'Pesarme', emoji: '⚖️', type: 'MEASURE', target: null, unit: 'kg', value: 78.4, done: true, currentStreak: 9 },
]

function renderPage() {
  return render(
    <MemoryRouter>
      <HabitsPage />
    </MemoryRouter>,
  )
}

describe('HabitsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getToday.mockResolvedValue(TODAY)
    recordEntry.mockResolvedValue({ date: '2026-08-03', value: null, done: true })
    incrementEntry.mockResolvedValue({ date: '2026-08-03', value: 6, done: false })
  })

  it('lists what is due today with its streak', async () => {
    renderPage()
    expect(await screen.findByText('Meditar')).toBeInTheDocument()
    expect(screen.getByText('Agua')).toBeInTheDocument()
    expect(screen.getByText('5 / 8 vasos')).toBeInTheDocument()
    // By test id, not by text: "4" also appears inside "78,4 kg".
    expect(screen.getByTestId('streak-1')).toHaveTextContent('4')
  })

  it('marks a check habit as done', async () => {
    renderPage()
    await screen.findByText('Meditar')
    fireEvent.click(screen.getByRole('button', { name: /marcar meditar/i }))
    await waitFor(() => expect(recordEntry).toHaveBeenCalledWith(1, expect.any(String), null))
  })

  it('adds one to a counter habit', async () => {
    renderPage()
    await screen.findByText('Agua')
    fireEvent.click(screen.getByRole('button', { name: /sumar uno a agua/i }))
    await waitFor(() => expect(incrementEntry).toHaveBeenCalledWith(2, expect.any(String), 1))
  })

  it('shows the empty state when nothing is due', async () => {
    getToday.mockResolvedValue([])
    renderPage()
    expect(await screen.findByText(/nada pendiente/i)).toBeInTheDocument()
  })
})
