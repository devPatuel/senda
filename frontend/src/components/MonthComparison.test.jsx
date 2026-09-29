import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import MonthComparison from './MonthComparison'
import { getSummary } from '../api/transactions'

vi.mock('../api/transactions', () => ({ getSummary: vi.fn() }))

function summary(year, month, totalExpense, totalIncome = 0) {
  return {
    year, month, totalIncome, totalExpense, balance: totalIncome - totalExpense,
    fixedExpenseTotal: 0, variableExpenseTotal: totalExpense, fixedExpensePercentage: null,
    transfersIn: 0, transfersOut: 0, topExpenseCategory: null, byCategory: [],
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('MonthComparison', () => {
  it('shows how this month compares with the previous one', async () => {
    // 400 this month against 500 last month: 100 less, a 20% drop
    getSummary
      .mockResolvedValueOnce(summary(2026, 9, 400))
      .mockResolvedValueOnce(summary(2026, 8, 500))

    render(<MonthComparison year={2026} month={9} spaceId={3} />)

    const change = await screen.findByTestId('expense-change')
    expect(change).toHaveAttribute('data-direction', 'down')
    expect(change).toHaveTextContent('20')
  })

  it('marks an increase in spending as up', async () => {
    getSummary
      .mockResolvedValueOnce(summary(2026, 9, 600))
      .mockResolvedValueOnce(summary(2026, 8, 500))

    render(<MonthComparison year={2026} month={9} spaceId={3} />)

    expect(await screen.findByTestId('expense-change')).toHaveAttribute('data-direction', 'up')
  })

  it('asks for the previous month across a year boundary', async () => {
    getSummary
      .mockResolvedValueOnce(summary(2026, 1, 100))
      .mockResolvedValueOnce(summary(2025, 12, 100))

    render(<MonthComparison year={2026} month={1} spaceId={3} />)

    await screen.findByTestId('expense-change')
    expect(getSummary).toHaveBeenCalledWith(2025, 12, 3)
  })

  it('says there is nothing to compare when the previous month is empty', async () => {
    getSummary
      .mockResolvedValueOnce(summary(2026, 9, 400))
      .mockResolvedValueOnce(summary(2026, 8, 0))

    render(<MonthComparison year={2026} month={9} spaceId={3} />)

    const change = await screen.findByTestId('expense-change')
    expect(change).toHaveAttribute('data-direction', 'none')
  })
})
