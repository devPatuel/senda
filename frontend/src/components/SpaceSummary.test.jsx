import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SpaceSummary from './SpaceSummary'
import { getSummary } from '../api/transactions'
import { listAccounts } from '../api/accounts'
import { formatCurrency } from '../lib/format'

vi.mock('../api/transactions', () => ({ getSummary: vi.fn() }))
vi.mock('../api/accounts', () => ({ listAccounts: vi.fn() }))

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

beforeEach(() => {
  vi.clearAllMocks()
  getSummary.mockResolvedValue(SUMMARY)
  listAccounts.mockResolvedValue([
    { id: 1, name: 'Cuenta común', balance: 1412.35, archived: false },
  ])
})

describe('SpaceSummary', () => {
  it('shows the month figures for the space', async () => {
    render(<SpaceSummary spaceId={7} />)

    await waitFor(() => expect(getSummary).toHaveBeenCalledWith(year, month, 7))
    expect(await screen.findByText(visibleCurrency(388.05))).toBeInTheDocument()
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

  it('tells an empty month apart from a month with no spending', async () => {
    getSummary.mockResolvedValue({
      ...SUMMARY, totalExpense: 0, transfersIn: 0, byCategory: [], topExpenseCategory: null,
    })
    render(<SpaceSummary spaceId={7} />)

    expect(await screen.findByText(/Sin gastos este mes/i)).toBeInTheDocument()
  })
})
