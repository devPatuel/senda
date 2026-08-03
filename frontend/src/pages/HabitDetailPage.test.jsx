import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HabitDetailPage from './HabitDetailPage'

vi.mock('../api/habits', () => ({ getHistory: vi.fn() }))
import { getHistory } from '../api/habits'

describe('HabitDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getHistory.mockResolvedValue({
      habit: { id: 1, name: 'Leer', emoji: '📖', type: 'CHECK', unit: null, target: null },
      entries: [{ date: '2026-08-02', value: null, done: true }],
      currentStreak: 5,
      bestStreak: 12,
      completionRate: 80,
    })
  })

  it('shows the streaks and the completion rate', async () => {
    render(
      <MemoryRouter initialEntries={['/habitos/1']}>
        <Routes>
          <Route path="/habitos/:id" element={<HabitDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('Leer')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByText(/80\s*%/)).toBeInTheDocument()
  })
})
