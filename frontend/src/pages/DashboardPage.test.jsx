import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from './DashboardPage'
import { getSummary } from '../api/transactions'
import { formatCurrency, formatMonthLabel } from '../lib/format'

vi.mock('../api/transactions', () => ({
  getSummary: vi.fn(),
}))

// Testing Library normalizes whitespace in the DOM, so the non-breaking
// space Intl puts before "€" must be normalized in the expected string too.
function visibleCurrency(value) {
  return formatCurrency(value).replace(/[\u00a0\u202f]/g, ' ')
}

const now = new Date()
const year = now.getFullYear()
const month = now.getMonth() + 1

function buildSummary(overrides = {}) {
  return {
    year,
    month,
    totalIncome: 1500,
    totalExpense: 350.25,
    balance: 1149.75,
    byCategory: [
      { categoryId: 1, categoryName: 'Comida', categoryColor: '#ef4444', type: 'EXPENSE', total: 350.25 },
      { categoryId: 2, categoryName: 'Nómina', categoryColor: '#10b981', type: 'INCOME', total: 1500 },
    ],
    ...overrides,
  }
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    getSummary.mockReset()
  })

  it('renders the monthly summary for the current month', async () => {
    getSummary.mockResolvedValue(buildSummary())
    renderDashboard()

    expect(await screen.findByText(visibleCurrency(1149.75))).toBeInTheDocument()
    // Income total appears in the card and in the breakdown row
    expect(screen.getAllByText(visibleCurrency(1500)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(350.25)).length).toBeGreaterThan(0)
    expect(screen.getByText('Comida')).toBeInTheDocument()
    expect(screen.getByText('Nómina')).toBeInTheDocument()
    expect(screen.getByText(formatMonthLabel(year, month))).toBeInTheDocument()
    expect(getSummary).toHaveBeenCalledWith(year, month)
  })

  it('navigates to the previous month and fetches its summary', async () => {
    getSummary.mockResolvedValue(buildSummary())
    const user = userEvent.setup()
    renderDashboard()

    await screen.findByText(formatMonthLabel(year, month))
    await user.click(screen.getByRole('button', { name: 'Mes anterior' }))

    const prev = month === 1 ? { year: year - 1, month: 12 } : { year, month: month - 1 }
    expect(await screen.findByText(formatMonthLabel(prev.year, prev.month))).toBeInTheDocument()
    expect(getSummary).toHaveBeenLastCalledWith(prev.year, prev.month)
  })

  it('shows a friendly empty state when the month has no transactions', async () => {
    getSummary.mockResolvedValue(
      buildSummary({ totalIncome: 0, totalExpense: 0, balance: 0, byCategory: [] }),
    )
    renderDashboard()

    expect(await screen.findByText('Sin movimientos este mes')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Añadir un movimiento' })).toHaveAttribute(
      'href',
      '/movimientos',
    )
  })
})
