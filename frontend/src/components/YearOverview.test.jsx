import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import YearOverview from './YearOverview'
import { getYearSummary } from '../api/transactions'
import { formatCurrency } from '../lib/format'

vi.mock('../api/transactions', () => ({ getYearSummary: vi.fn() }))

function visibleCurrency(value) {
  return formatCurrency(value).replace(/ /g, ' ')
}

const YEAR = {
  year: 2026,
  totalIncome: 24000,
  totalExpense: 12000,
  balance: 12000,
  monthlyAverageExpense: 1000,
  months: Array.from({ length: 12 }, (_, i) => ({
    year: 2026, month: i + 1, income: 2000, expense: i === 5 ? 3000 : 818.18,
    balance: 0,
  })),
  byCategory: [
    { categoryId: 78, categoryName: 'Supermercados', categoryColor: '#10b981', type: 'EXPENSE', total: 4000, fixed: false, transfer: false },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
  getYearSummary.mockResolvedValue(YEAR)
})

describe('YearOverview', () => {
  it('shows the year totals and the monthly average', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    expect(await screen.findByTestId('year-expense')).toHaveTextContent(visibleCurrency(12000))
    expect(screen.getByTestId('year-average')).toHaveTextContent(visibleCurrency(1000))
  })

  it('draws one bar per month', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    await waitFor(() => expect(screen.getAllByTestId(/^month-bar-/)).toHaveLength(12))
  })

  it('scales each bar against the worst month', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    // June is the highest at 3000, so it is the full-height reference
    const june = await screen.findByTestId('month-bar-6')
    expect(june).toHaveAttribute('data-height', '100')
  })

  it('moves to another year', async () => {
    const user = userEvent.setup()
    render(<YearOverview year={2026} spaceId={3} />)
    await screen.findByTestId('year-expense')

    await user.click(screen.getByRole('button', { name: /año anterior/i }))

    await waitFor(() => expect(getYearSummary).toHaveBeenCalledWith(2025, 3))
  })
})
