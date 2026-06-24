import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from './DashboardPage'
import { getSummary, createTransaction, getTrends } from '../api/transactions'
import { getNetWorth, getNetWorthHistory } from '../api/networth'
import { getRecurring } from '../api/recurring'
import { listCategories } from '../api/categories'
import { getAlerts } from '../api/alerts'
import { formatCurrency, formatMonthLabel } from '../lib/format'

vi.mock('../api/transactions', () => ({
  getSummary: vi.fn(),
  createTransaction: vi.fn(),
  getTrends: vi.fn(),
}))
vi.mock('../api/networth', () => ({ getNetWorth: vi.fn(), getNetWorthHistory: vi.fn() }))
vi.mock('../api/recurring', () => ({ getRecurring: vi.fn() }))
vi.mock('../api/categories', () => ({ listCategories: vi.fn() }))
vi.mock('../api/alerts', () => ({ getAlerts: vi.fn() }))

const NO_ALERTS = { antExpenses: [], forgottenSubscriptions: [] }

const TRENDS = [
  { year: 2026, month: 1, income: 1000, expense: 400, balance: 600 },
  { year: 2026, month: 2, income: 1200, expense: 500, balance: 700 },
]

// Testing Library normalizes whitespace in the DOM, so the non-breaking
// space Intl puts before "€" must be normalized in the expected string too.
function visibleCurrency(value) {
  return formatCurrency(value).replace(/[  ]/g, ' ')
}

const now = new Date()
const year = now.getFullYear()
const month = now.getMonth() + 1
const prev = month === 1 ? { year: year - 1, month: 12 } : { year, month: month - 1 }

// ISO date `days` from today (for date-relative assertions).
function isoInDays(days) {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() + days)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const NET_WORTH = {
  liquid: 1000,
  investments: 0,
  investmentsHoldings: 0,
  investmentsNfts: 0,
  debtsInFavor: 0,
  debtsAgainst: 0,
  net: 1000,
}

const CATEGORIES = [
  { id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', active: true },
  { id: 2, name: 'Nómina', type: 'INCOME', color: '#10b981', active: true },
]

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
    vi.clearAllMocks()
    getSummary.mockResolvedValue(buildSummary())
    getNetWorth.mockResolvedValue(NET_WORTH)
    getRecurring.mockResolvedValue([])
    listCategories.mockResolvedValue(CATEGORIES)
    getTrends.mockResolvedValue(TRENDS)
    getNetWorthHistory.mockResolvedValue([])
    getAlerts.mockResolvedValue(NO_ALERTS)
  })

  it('shows the spending alerts section when there are alerts', async () => {
    getAlerts.mockResolvedValue({
      antExpenses: [
        { categoryId: 1, categoryName: 'Comida', categoryColor: '#ef4444', count: 8, total: 56 },
      ],
      forgottenSubscriptions: [
        { recurringId: 5, name: 'Revista', categoryId: 3, categoryName: 'Ocio', categoryColor: '#f59e0b' },
      ],
    })
    renderDashboard()

    expect(await screen.findByText('Avisos')).toBeInTheDocument()
    expect(screen.getByText(/Muchos gastos pequeños en/)).toBeInTheDocument()
    expect(screen.getByText(/Pagas/)).toBeInTheDocument()
    expect(screen.getByText('«Revista»')).toBeInTheDocument()
  })

  it('renders the income/expense trends chart', async () => {
    renderDashboard()
    expect(await screen.findByText('Tendencias (2 meses)')).toBeInTheDocument()
  })

  it('renders the monthly summary for the current month', async () => {
    renderDashboard()

    expect(await screen.findByText(visibleCurrency(1149.75))).toBeInTheDocument()
    expect(screen.getAllByText(visibleCurrency(1500)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(350.25)).length).toBeGreaterThan(0)
    expect(screen.getByText('Comida')).toBeInTheDocument()
    expect(screen.getByText('Nómina')).toBeInTheDocument()
    expect(screen.getByText(formatMonthLabel(year, month))).toBeInTheDocument()
    expect(getSummary).toHaveBeenCalledWith(year, month)
  })

  it('also fetches the previous month to compute deltas', async () => {
    renderDashboard()

    await screen.findByText(formatMonthLabel(year, month))
    // The summary effect fetches both the current and the previous month
    expect(getSummary).toHaveBeenCalledWith(prev.year, prev.month)
  })

  it('shows the per-category variation vs the previous month', async () => {
    getSummary.mockImplementation((y, m) => {
      if (y === year && m === month) return Promise.resolve(buildSummary())
      // Previous month: Comida was 200 -> 350.25 is +75%
      return Promise.resolve(
        buildSummary({
          byCategory: [
            { categoryId: 1, categoryName: 'Comida', categoryColor: '#ef4444', type: 'EXPENSE', total: 200 },
            { categoryId: 2, categoryName: 'Nómina', categoryColor: '#10b981', type: 'INCOME', total: 1500 },
          ],
        }),
      )
    })
    renderDashboard()

    await screen.findByText('Comida')
    expect(await screen.findByText(/75%/)).toBeInTheDocument()
  })

  it('navigates to the previous month', async () => {
    const user = userEvent.setup()
    renderDashboard()

    await screen.findByText(formatMonthLabel(year, month))
    await user.click(screen.getByRole('button', { name: 'Mes anterior' }))

    expect(await screen.findByText(formatMonthLabel(prev.year, prev.month))).toBeInTheDocument()
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

  it('shows cancellation reminders for recurring payments ending soon', async () => {
    getRecurring.mockResolvedValue([
      {
        id: 9,
        name: 'Netflix',
        amount: 12.99,
        frequency: 'MONTHLY',
        categoryId: 2,
        categoryName: 'Ocio',
        categoryColor: '#f59e0b',
        dayOfMonth: 5,
        month: null,
        dayOfWeek: null,
        nextDueDate: isoInDays(40),
        monthlyEquivalent: 12.99,
        endDate: isoInDays(5),
      },
    ])
    renderDashboard()

    expect(await screen.findByText('Recordatorios de baja (14 días)')).toBeInTheDocument()
    expect(screen.getByText(/Cancelar/)).toBeInTheDocument()
    expect(screen.getByText('«Netflix»')).toBeInTheDocument()
  })

  it('creates a movement from the quick-add FAB and refreshes the summary', async () => {
    createTransaction.mockResolvedValue({})
    const user = userEvent.setup()
    renderDashboard()

    await screen.findByText(formatMonthLabel(year, month))
    const callsBefore = getSummary.mock.calls.length

    await user.click(screen.getByRole('button', { name: 'Nuevo movimiento' }))
    // The shared transaction form opens
    await screen.findByText('Nuevo movimiento', { selector: 'h2' })

    await user.selectOptions(screen.getByLabelText('Categoría'), '1')
    await user.type(screen.getByLabelText('Importe (€)'), '20')
    await user.click(screen.getByRole('button', { name: 'Crear movimiento' }))

    await waitFor(() => expect(createTransaction).toHaveBeenCalledTimes(1))
    // Saving triggers a refresh: the summary is fetched again
    await waitFor(() => expect(getSummary.mock.calls.length).toBeGreaterThan(callsBefore))
  })
})
