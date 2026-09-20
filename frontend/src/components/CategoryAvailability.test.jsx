import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import CategoryAvailability from './CategoryAvailability'
import { getBudget } from '../api/categories'
import { formatCurrency } from '../lib/format'

vi.mock('../api/categories', () => ({ getBudget: vi.fn() }))

function visibleCurrency(value) {
  return formatCurrency(value).replace(/ /g, ' ')
}

const BUDGET = {
  totalAccounts: 1412.35,
  totalAssigned: 410,
  toAssign: 1002.35,
  categories: [
    {
      id: 78, name: 'Supermercados', color: '#10b981',
      balance: 400, spentThisMonth: 120, spent: 120, available: 280,
      targetPercentage: null, targetAmount: null,
    },
    {
      id: 82, name: 'Parking', color: '#0ea5e9',
      balance: 10, spentThisMonth: 120, spent: 120, available: -110,
      targetPercentage: null, targetAmount: null,
    },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
  getBudget.mockResolvedValue(BUDGET)
})

describe('CategoryAvailability', () => {
  it('shows assigned, spent and what is left for each category', async () => {
    render(<CategoryAvailability spaceId={3} />)

    const row = await screen.findByTestId('availability-78')
    expect(within(row).getByText('Supermercados')).toBeInTheDocument()
    expect(within(row).getByText(visibleCurrency(280))).toBeInTheDocument()
    expect(row).toHaveAttribute('data-available', '280')
  })

  it('marks an overspent category as negative', async () => {
    render(<CategoryAvailability spaceId={3} />)

    const row = await screen.findByTestId('availability-82')
    expect(row).toHaveAttribute('data-negative', 'true')
    expect(within(row).getByText(visibleCurrency(-110))).toBeInTheDocument()
  })

  it('reads the budget of the given space', async () => {
    render(<CategoryAvailability spaceId={3} />)

    await waitFor(() => expect(getBudget).toHaveBeenCalledWith(3))
  })

  it('uses a budget it is given instead of fetching its own', async () => {
    render(<CategoryAvailability spaceId={3} budget={BUDGET} />)

    expect(await screen.findByTestId('availability-78')).toBeInTheDocument()
    expect(getBudget).not.toHaveBeenCalled()
  })

  it('shows the total left to cover when something is overspent', async () => {
    render(<CategoryAvailability spaceId={3} />)

    // 110 is what has to be moved into Parking to leave it at zero
    expect(await screen.findByTestId('overspent-notice')).toHaveTextContent('110')
  })
})
