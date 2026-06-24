import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import DebtsPage from './DebtsPage'
import {
  listDebts,
  createDebt,
  removeDebt,
  addPayment,
  listPayments,
  removePayment,
} from '../api/debts'

vi.mock('../api/debts', () => ({
  listDebts: vi.fn(),
  getDebt: vi.fn(),
  createDebt: vi.fn(),
  updateDebt: vi.fn(),
  removeDebt: vi.fn(),
  listPayments: vi.fn(),
  addPayment: vi.fn(),
  removePayment: vi.fn(),
}))

// Use distinct counterparty names to avoid collisions with UI labels like "Me deben" / "Debo"
const THEY_OWE_DEBT = {
  id: 1,
  direction: 'THEY_OWE_ME',
  counterparty: 'Rodrigo',
  concept: 'Viaje de fin de año',
  originalAmount: 200,
  paidAmount: 50,
  pendingAmount: 150,
  settled: false,
  date: '2026-06-01',
  createdAt: '2026-06-01T10:00:00Z',
}

const I_OWE_DEBT = {
  id: 2,
  direction: 'I_OWE',
  counterparty: 'Marta',
  concept: 'Préstamo mes de mayo',
  originalAmount: 100,
  paidAmount: 100,
  pendingAmount: 0,
  settled: true,
  date: '2026-05-01',
  createdAt: '2026-05-01T10:00:00Z',
}

const PAYMENT = {
  id: 10,
  debtId: 1,
  amount: 50,
  date: '2026-06-10',
  note: 'Bizum',
  createdAt: '2026-06-10T12:00:00Z',
}

describe('DebtsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listDebts.mockResolvedValue([THEY_OWE_DEBT, I_OWE_DEBT])
  })

  it('renders debts split into two sections and shows pending totals', async () => {
    render(<DebtsPage />)

    // Counterparty names appear in their respective sections
    await screen.findByText('Rodrigo')
    expect(screen.getByText('Marta')).toBeInTheDocument()

    // Section headings
    expect(screen.getByRole('heading', { name: /Me deben/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Debo/ })).toBeInTheDocument()

    // Settled badge on Marta's debt
    expect(screen.getByText('Saldada')).toBeInTheDocument()
  })

  it('opens the payment modal and calls addPayment on submit', async () => {
    addPayment.mockResolvedValue({
      id: 11, debtId: 1, amount: 30, date: '2026-06-16', note: null, createdAt: '2026-06-16T10:00:00Z',
    })
    // Reload after payment
    listDebts.mockResolvedValueOnce([THEY_OWE_DEBT, I_OWE_DEBT])
      .mockResolvedValue([{ ...THEY_OWE_DEBT, paidAmount: 80, pendingAmount: 120 }, I_OWE_DEBT])

    const user = userEvent.setup()
    render(<DebtsPage />)

    await screen.findByText('Rodrigo')
    await user.click(screen.getByRole('button', { name: /Abonar/ }))

    // Modal appears
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    // Fill amount
    await user.clear(screen.getByLabelText('Importe abonado'))
    await user.type(screen.getByLabelText('Importe abonado'), '30')
    await user.click(screen.getByRole('button', { name: 'Registrar abono' }))

    await waitFor(() =>
      expect(addPayment).toHaveBeenCalledWith(1, expect.objectContaining({ amount: 30 })),
    )
  })

  it('shows translated error when payment exceeds pending amount', async () => {
    addPayment.mockRejectedValue(
      Object.assign(new Error('Payment exceeds the pending amount (pending: 150.00, requested: 200.00)'), {}),
    )
    const user = userEvent.setup()
    render(<DebtsPage />)

    await screen.findByText('Rodrigo')
    await user.click(screen.getByRole('button', { name: /Abonar/ }))
    await user.clear(screen.getByLabelText('Importe abonado'))
    await user.type(screen.getByLabelText('Importe abonado'), '200')
    await user.click(screen.getByRole('button', { name: 'Registrar abono' }))

    // Spanish translation of the error
    expect(await screen.findByRole('alert')).toHaveTextContent('El abono supera el importe pendiente')
  })

  it('creates a new debt and reloads the list', async () => {
    const newDebt = {
      id: 3,
      direction: 'THEY_OWE_ME',
      counterparty: 'Sofía',
      concept: 'Cena cumpleaños',
      originalAmount: 40,
      paidAmount: 0,
      pendingAmount: 40,
      settled: false,
      date: '2026-06-16',
      createdAt: '2026-06-16T10:00:00Z',
    }
    createDebt.mockResolvedValue(newDebt)
    listDebts
      .mockResolvedValueOnce([THEY_OWE_DEBT, I_OWE_DEBT])
      .mockResolvedValue([THEY_OWE_DEBT, I_OWE_DEBT, newDebt])

    const user = userEvent.setup()
    render(<DebtsPage />)

    await screen.findByText('Rodrigo')
    await user.click(screen.getByRole('button', { name: /Nueva deuda/ }))

    await user.type(screen.getByLabelText('Persona / entidad'), 'Sofía')
    await user.type(screen.getByLabelText('Concepto'), 'Cena cumpleaños')
    await user.type(screen.getByLabelText('Importe original'), '40')
    await user.click(screen.getByRole('button', { name: 'Crear deuda' }))

    await waitFor(() =>
      expect(createDebt).toHaveBeenCalledWith(
        expect.objectContaining({ counterparty: 'Sofía', originalAmount: 40 }),
      ),
    )
  })

  it('opens payment list and deletes a payment', async () => {
    listPayments.mockResolvedValue([PAYMENT])
    removePayment.mockResolvedValue(null)
    listDebts.mockResolvedValue([THEY_OWE_DEBT, I_OWE_DEBT])

    const user = userEvent.setup()
    render(<DebtsPage />)

    await screen.findByText('Rodrigo')
    // Click "Ver abonos"
    await user.click(screen.getByRole('button', { name: /Ver abonos de Rodrigo/ }))

    // Payment appears (rendered inline as "<amount> · <note>")
    await screen.findByText(/Bizum/)

    // Delete it
    await user.click(screen.getByRole('button', { name: /Eliminar abono de/ }))
    // Confirm dialog
    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    await waitFor(() =>
      expect(removePayment).toHaveBeenCalledWith(1, 10),
    )
  })
})
