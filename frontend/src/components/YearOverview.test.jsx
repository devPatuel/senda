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
  totalExpense: 3742.35,
  totalTransfersIn: 6000,
  months: Array.from({ length: 12 }, (_, i) => ({
    year: 2026,
    month: i + 1,
    expense: i === 5 ? 3000 : i === 8 ? 742.35 : 0,
    transfersIn: i === 8 ? 1800 : i === 5 ? 3910 : 0,
  })),
}

beforeEach(() => {
  vi.clearAllMocks()
  getYearSummary.mockResolvedValue(YEAR)
})

describe('YearOverview', () => {
  it('shows what was spent and what was put in over the year', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    expect(await screen.findByTestId('year-expense')).toHaveTextContent(visibleCurrency(3742.35))
    expect(screen.getByTestId('year-transfers')).toHaveTextContent(visibleCurrency(6000))
  })

  it('does not show a yearly average', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    await screen.findByTestId('year-expense')
    // Dividing by twelve would count empty months as cheap ones
    expect(screen.queryByText(/media al mes/i)).not.toBeInTheDocument()
  })

  it('puts a spending bar and a contributions bar on every month', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    await waitFor(() => expect(screen.getAllByTestId(/^month-row-/)).toHaveLength(12))
    const june = screen.getByTestId('month-row-6')
    expect(within(june).getByTestId('bar-expense-6')).toBeInTheDocument()
    expect(within(june).getByTestId('bar-transfers-6')).toBeInTheDocument()
  })

  it('writes each amount on its bar', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    const september = await screen.findByTestId('month-row-9')
    expect(within(september).getByTestId('bar-expense-9')).toHaveTextContent(visibleCurrency(742.35))
    expect(within(september).getByTestId('bar-transfers-9')).toHaveTextContent(visibleCurrency(1800))
  })

  it('leaves the bars of an empty month without a figure', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    const january = await screen.findByTestId('month-row-1')
    expect(within(january).getByTestId('bar-expense-1')).toHaveTextContent('')
  })

  it('scales both kinds of bar against the same maximum', async () => {
    render(<YearOverview year={2026} spaceId={3} />)

    // June: 3000 spent, 3910 in. The 3910 is the widest figure of the year,
    // so spending at 3000 must read as shorter, not as a full bar.
    const june = await screen.findByTestId('month-row-6')
    expect(within(june).getByTestId('bar-transfers-6')).toHaveAttribute('data-width', '100')
    expect(within(june).getByTestId('bar-expense-6')).toHaveAttribute('data-width', '77')
  })

  it('moves to another year', async () => {
    const user = userEvent.setup()
    render(<YearOverview year={2026} spaceId={3} />)
    await screen.findByTestId('year-expense')

    await user.click(screen.getByRole('button', { name: /año anterior/i }))

    await waitFor(() => expect(getYearSummary).toHaveBeenCalledWith(2025, 3))
  })
})
