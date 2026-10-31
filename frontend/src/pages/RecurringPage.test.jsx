import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RecurringPage from './RecurringPage'
import { getRecurring, createRecurring } from '../api/recurring'
import { listCategories } from '../api/categories'
import { getNetWorth } from '../api/networth'

vi.mock('../api/recurring', () => ({
  getRecurring: vi.fn(),
  createRecurring: vi.fn(),
  updateRecurring: vi.fn(),
  removeRecurring: vi.fn(),
}))

vi.mock('../api/categories', () => ({
  listCategories: vi.fn(),
}))

vi.mock('../api/networth', () => ({
  getNetWorth: vi.fn(),
}))

const CATEGORIES = [
  { id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', active: true },
  { id: 2, name: 'Ocio', type: 'EXPENSE', color: '#f59e0b', active: true },
]

// ISO date `days` from today (so date-relative assertions stay stable).
function isoInDays(days) {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() + days)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

const WEEKLY_PAYMENT = {
  id: 1,
  name: 'Limpieza',
  amount: 10,
  frequency: 'WEEKLY',
  categoryId: 1,
  categoryName: 'Comida',
  categoryColor: '#ef4444',
  dayOfMonth: 1,
  month: null,
  dayOfWeek: 1,
  nextDueDate: isoInDays(3),
  monthlyEquivalent: 43.33,
  endDate: null,
}

const ENDING_PAYMENT = {
  id: 2,
  name: 'Netflix',
  amount: 12.99,
  frequency: 'MONTHLY',
  categoryId: 2,
  categoryName: 'Ocio',
  categoryColor: '#f59e0b',
  dayOfMonth: 5,
  month: null,
  dayOfWeek: null,
  nextDueDate: isoInDays(10),
  monthlyEquivalent: 12.99,
  endDate: isoInDays(5),
}

beforeEach(() => {
  vi.clearAllMocks()
  getNetWorth.mockResolvedValue({ liquid: 0, investments: 0 })
  listCategories.mockResolvedValue(CATEGORIES)
})

describe('RecurringPage', () => {
  describe('fixed costs', () => {
    const rent = { ...WEEKLY_PAYMENT, id: 10, name: 'Alquiler', categoryId: 3, categoryName: 'Vivienda', monthlyEquivalent: 800, endDate: null }
    const course = { ...WEEKLY_PAYMENT, id: 11, name: 'Curso', categoryId: 4, categoryName: 'Estudios', monthlyEquivalent: 150, endDate: null }
    const series = { ...WEEKLY_PAYMENT, id: 12, name: 'Series', categoryId: 5, categoryName: 'Suscripciones', monthlyEquivalent: 50, endDate: null }
    const finished = { ...WEEKLY_PAYMENT, id: 13, name: 'Antiguo', categoryId: 3, categoryName: 'Vivienda', monthlyEquivalent: 500, endDate: isoInDays(-1) }

    beforeEach(() => {
      getRecurring.mockResolvedValue([rent, course, series, finished])
      getNetWorth.mockResolvedValue({ liquid: 4567, investments: 3000 })
    })

    it('shows the months of margin with and without investments, rounded down', async () => {
      render(<RecurringPage />)

      const runway = await screen.findByTestId('fixed-cost-runway')
      // 4567 / 1000 = 4.567 -> 4,5 and (4567 + 3000) / 1000 = 7.567 -> 7,5
      expect(runway).toHaveTextContent('4,5 meses')
      expect(runway).toHaveTextContent('7,5 meses')
    })

    it('shows the total per category and filters by several at once', async () => {
      const user = userEvent.setup()
      render(<RecurringPage />)

      const estudios = await screen.findByRole('button', { name: /Estudios/ })
      const suscripciones = screen.getByRole('button', { name: /Suscripciones/ })
      expect(estudios).toHaveTextContent('150,00')
      expect(screen.getByRole('button', { name: /Vivienda/ })).toHaveTextContent('800,00')

      await user.click(estudios)
      await user.click(suscripciones)

      expect(screen.getByText('Curso')).toBeInTheDocument()
      expect(screen.getByText('Series')).toBeInTheDocument()
      expect(screen.queryByText('Alquiler')).not.toBeInTheDocument()
      expect(screen.getByText('Coste mensual de lo seleccionado').parentElement).toHaveTextContent('200,00')
      // The margin measures the whole cushion, so filtering does not change it
      expect(screen.getByTestId('fixed-cost-runway')).toHaveTextContent('4,5 meses')

      await user.click(estudios)
      await user.click(suscripciones)
      expect(screen.getByText('Alquiler')).toBeInTheDocument()
    })

    it('never shows negative months and hides them if the money cannot be loaded', async () => {
      getNetWorth.mockResolvedValue({ liquid: -100, investments: 0 })
      const { unmount } = render(<RecurringPage />)
      expect(await screen.findByTestId('fixed-cost-runway')).toHaveTextContent('0 meses')
      unmount()

      getNetWorth.mockRejectedValue(new Error('caído'))
      render(<RecurringPage />)
      await screen.findByText('Alquiler')
      expect(screen.queryByTestId('fixed-cost-runway')).not.toBeInTheDocument()
    })
  })

  it('shows weekly weekday and the cancellation badge for payments ending soon', async () => {
    getRecurring.mockResolvedValue([WEEKLY_PAYMENT, ENDING_PAYMENT])
    render(<RecurringPage />)

    expect(await screen.findByText('Limpieza')).toBeInTheDocument()
    // Weekly subtitle includes the weekday name
    expect(screen.getByText(/Semanal \(lunes\)/)).toBeInTheDocument()
    // The ending payment shows the amber "Cancelar pronto" badge
    expect(screen.getByText('Cancelar pronto')).toBeInTheDocument()
    expect(screen.getByText(/^Baja:/)).toBeInTheDocument()
  })

  it('shows the price-change indicator when the amount went up', async () => {
    getRecurring.mockResolvedValue([
      { ...ENDING_PAYMENT, endDate: null, previousAmount: 10, changePct: 20 },
    ])
    render(<RecurringPage />)

    expect(await screen.findByText('Netflix')).toBeInTheDocument()
    expect(screen.getByText(/▲ \+20%/)).toBeInTheDocument()
  })

  it('creates a weekly payment sending dayOfWeek and a placeholder dayOfMonth', async () => {
    getRecurring.mockResolvedValue([])
    createRecurring.mockResolvedValue({ ...WEEKLY_PAYMENT })
    const user = userEvent.setup()
    render(<RecurringPage />)

    await screen.findByText('Aún no hay pagos recurrentes')
    await user.click(screen.getByRole('button', { name: /Nuevo pago/i }))

    // Switch to weekly: the weekday selector must appear
    await user.selectOptions(screen.getByLabelText('Frecuencia'), 'WEEKLY')
    expect(screen.getByLabelText('Día de la semana')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Nombre'), 'Limpieza')
    await user.type(screen.getByLabelText('Importe'), '10')
    await user.selectOptions(screen.getByLabelText('Día de la semana'), '3')

    await user.click(screen.getByRole('button', { name: /Crear pago/i }))

    await waitFor(() => expect(createRecurring).toHaveBeenCalledTimes(1))
    expect(createRecurring).toHaveBeenCalledWith(
      expect.objectContaining({
        frequency: 'WEEKLY',
        dayOfWeek: 3,
        dayOfMonth: 1,
        month: null,
        endDate: null,
      }),
    )
  })
})
