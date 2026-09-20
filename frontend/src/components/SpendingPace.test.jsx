import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import SpendingPace from './SpendingPace'

beforeEach(() => {
  vi.useFakeTimers()
  // 10th of a 30-day month: a third of September 2026 is gone
  vi.setSystemTime(new Date(2026, 8, 10, 12, 0, 0))
})

afterEach(() => {
  vi.useRealTimers()
})

describe('SpendingPace', () => {
  it('projects the month from what has been spent so far', () => {
    // 300 in 10 of 30 days -> 30/day -> 900 projected
    render(<SpendingPace spent={300} assigned={1200} year={2026} month={9} />)

    expect(screen.getByTestId('projected')).toHaveAttribute('data-value', '900')
  })

  it('warns when the projection overruns what is assigned', () => {
    render(<SpendingPace spent={500} assigned={1000} year={2026} month={9} />)

    // 500 in 10 days projects 1500 against 1000 assigned
    expect(screen.getByTestId('pace')).toHaveAttribute('data-status', 'over')
  })

  it('stays calm when the projection fits the budget', () => {
    render(<SpendingPace spent={200} assigned={1000} year={2026} month={9} />)

    expect(screen.getByTestId('pace')).toHaveAttribute('data-status', 'ok')
  })

  it('shows the days left in the month', () => {
    render(<SpendingPace spent={200} assigned={1000} year={2026} month={9} />)

    expect(screen.getByTestId('days-left')).toHaveTextContent('20')
  })

  it('does not project a past month', () => {
    // Looking back at August from September: the month is closed, no projection
    render(<SpendingPace spent={800} assigned={1000} year={2026} month={8} />)

    expect(screen.queryByTestId('projected')).not.toBeInTheDocument()
  })
})
