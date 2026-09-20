import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SpaceSummary from './SpaceSummary'
import { getSummary, getYearSummary } from '../api/transactions'
import { listAccounts } from '../api/accounts'
import { getBudget } from '../api/categories'
import { formatCurrency } from '../lib/format'

vi.mock('../api/transactions', () => ({ getSummary: vi.fn(), getYearSummary: vi.fn() }))
vi.mock('../api/accounts', () => ({ listAccounts: vi.fn() }))
vi.mock('../api/categories', () => ({ getBudget: vi.fn() }))

// Testing Library normalizes whitespace, so the non-breaking space Intl puts
// before "€" must be normalized in the expected string too.
function visibleCurrency(value) {
  return formatCurrency(value).replace(/\u00a0/g, ' ')
}

const now = new Date()
const year = now.getFullYear()
const month = now.getMonth() + 1

const SUMMARY = {
  year,
  month,
  totalIncome: 0,
  totalExpense: 388.05,
  balance: -388.05,
  fixedExpenseTotal: 58.90,
  variableExpenseTotal: 329.15,
  fixedExpensePercentage: null,
  transfersIn: 1800,
  transfersOut: 0,
  topExpenseCategory: {
    categoryId: 78, categoryName: 'Supermercados', categoryColor: '#10b981',
    type: 'EXPENSE', total: 236.4, fixed: false, transfer: false,
  },
  byCategory: [
    { categoryId: 64, categoryName: 'Aportaciones', categoryColor: '#14b8a6', type: 'INCOME', total: 1800, fixed: false, transfer: true },
    { categoryId: 78, categoryName: 'Supermercados', categoryColor: '#10b981', type: 'EXPENSE', total: 236.4, fixed: false, transfer: false },
    { categoryId: 82, categoryName: 'Parking', categoryColor: '#0ea5e9', type: 'EXPENSE', total: 92.75, fixed: false, transfer: false },
    { categoryId: 81, categoryName: 'Seguros', categoryColor: '#64748b', type: 'EXPENSE', total: 58.90, fixed: true, transfer: false },
  ],
}

const BUDGET = {
  totalAccounts: 1412.35,
  totalAssigned: 410,
  toAssign: 1002.35,
  categories: [
    {
      id: 78, name: 'Supermercados', color: '#10b981',
      balance: 10, spentThisMonth: 236.4, spent: 236.4, available: -226.4,
      targetPercentage: null, targetAmount: null,
    },
  ],
}

const YEAR_SUMMARY = {
  year,
  totalIncome: 0,
  totalExpense: 1164.15,
  balance: -1164.15,
  monthlyAverageExpense: 97.01,
  months: Array.from({ length: 12 }, (_, i) => ({
    year, month: i + 1, income: 0, expense: i === month - 1 ? 388.05 : 0, balance: 0,
  })),
  byCategory: [],
}

beforeEach(() => {
  vi.clearAllMocks()
  getSummary.mockResolvedValue(SUMMARY)
  getYearSummary.mockResolvedValue(YEAR_SUMMARY)
  getBudget.mockResolvedValue(BUDGET)
  listAccounts.mockResolvedValue([
    { id: 1, name: 'Cuenta común', balance: 1412.35, archived: false },
  ])
})

describe('SpaceSummary', () => {
  it('shows the month figures for the space', async () => {
    render(<SpaceSummary spaceId={7} />)

    await waitFor(() => expect(getSummary).toHaveBeenCalledWith(year, month, 7))
    // The month's spending also shows up in the pace block, so anchor on the tile
    const spentTile = (await screen.findByText('Gastado')).closest('section')
    expect(within(spentTile).getByText(visibleCurrency(388.05))).toBeInTheDocument()
    expect(screen.getByText(visibleCurrency(1412.35))).toBeInTheDocument()
  })

  it('reports transfers apart from spending, never as income', async () => {
    render(<SpaceSummary spaceId={7} />)

    expect(await screen.findByText('Aportado')).toBeInTheDocument()
    expect(screen.getByText(visibleCurrency(1800))).toBeInTheDocument()
    // The transfer category must not show up among the month's spending
    expect(screen.queryByText('Aportaciones')).not.toBeInTheDocument()
  })

  it('ranks expense categories with their share of the month', async () => {
    render(<SpaceSummary spaceId={7} />)

    const rows = await screen.findAllByTestId('category-bar')
    expect(rows.map((r) => r.getAttribute('data-category'))).toEqual([
      'Supermercados', 'Parking', 'Seguros',
    ])
    // 236.40 of 388.05 spent
    expect(rows[0]).toHaveAttribute('data-share', '45')
  })

  it('moves to the previous month on demand', async () => {
    const user = userEvent.setup()
    render(<SpaceSummary spaceId={7} />)

    await screen.findByText('Aportado')
    await user.click(screen.getByRole('button', { name: 'Mes anterior' }))

    const prev = month === 1 ? [year - 1, 12] : [year, month - 1]
    await waitFor(() => expect(getSummary).toHaveBeenCalledWith(prev[0], prev[1], 7))
  })

  it('shows what is left in each category', async () => {
    render(<SpaceSummary spaceId={7} />)

    const row = await screen.findByTestId('availability-78')
    expect(row).toHaveAttribute('data-negative', 'true')
    // The budget is fetched once and shared with the availability block
    await waitFor(() => expect(getBudget).toHaveBeenCalledTimes(1))
  })

  it('shows the year and the spending pace', async () => {
    render(<SpaceSummary spaceId={7} />)

    expect(await screen.findByTestId('year-expense')).toHaveTextContent(visibleCurrency(1164.15))
    expect(screen.getByTestId('pace')).toBeInTheDocument()
    await waitFor(() => expect(getYearSummary).toHaveBeenCalledWith(year, 7))
  })

  it('compares the month with the previous one', async () => {
    render(<SpaceSummary spaceId={7} />)

    expect(await screen.findByTestId('expense-change')).toBeInTheDocument()
  })

  it('tells an empty month apart from a month with no spending', async () => {
    getSummary.mockResolvedValue({
      ...SUMMARY, totalExpense: 0, transfersIn: 0, byCategory: [], topExpenseCategory: null,
    })
    render(<SpaceSummary spaceId={7} />)

    expect(await screen.findByText(/Sin gastos este mes/i)).toBeInTheDocument()
  })
})
