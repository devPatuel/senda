import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AllocationPage from './AllocationPage'
import { getEnvelopes, savePlan, distribute } from '../api/allocation'

vi.mock('../api/allocation', () => ({
  getEnvelopes: vi.fn(),
  savePlan: vi.fn(),
  distribute: vi.fn(),
}))

const AHORRO = { id: 1, name: 'Ahorro', color: '#10b981', percentage: 60 }
const OCIO = { id: 2, name: 'Ocio', color: '#f59e0b', percentage: 40 }

describe('AllocationPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    getEnvelopes.mockResolvedValue([AHORRO, OCIO])
  })

  it('renders each expense category with its current plan percentage', async () => {
    render(<AllocationPage />)

    // Both category names must appear in the plan editor
    expect(await screen.findByText('Ahorro')).toBeInTheDocument()
    expect(screen.getByText('Ocio')).toBeInTheDocument()

    // The plan editor pre-fills each category's target percentage
    expect(screen.getByDisplayValue('60')).toBeInTheDocument()
    expect(screen.getByDisplayValue('40')).toBeInTheDocument()
  })

  it('shows total-indicator in green when percentages sum to 100', async () => {
    render(<AllocationPage />)

    await screen.findByText('Ahorro')

    // The live indicator should show 100.00 % and be green (emerald classes)
    const indicator = await screen.findByText('100.00 %')
    expect(indicator.closest('[aria-live]')).toHaveTextContent('Total asignado')
    // Green variant has emerald text
    expect(indicator.closest('[aria-live]')).toHaveClass('text-emerald-700')
  })

  it('shows total-indicator in red when percentages do not sum to 100', async () => {
    // Start with a single envelope at 60 % (another at 40 hidden by removal)
    getEnvelopes.mockResolvedValue([{ ...AHORRO, percentage: 60 }])
    render(<AllocationPage />)

    await screen.findByText('Ahorro')

    const indicator = await screen.findByText('60.00 %')
    expect(indicator.closest('[aria-live]')).toHaveClass('text-red-700')
  })

  it('calls distribute and shows result when simulating a cobro', async () => {
    distribute.mockResolvedValue({
      amount: 1000,
      lines: [
        { envelopeId: 1, envelopeName: 'Ahorro', percentage: 60, allocated: 600, balance: 600 },
        { envelopeId: 2, envelopeName: 'Ocio', percentage: 40, allocated: 400, balance: 400 },
      ],
    })

    const user = userEvent.setup()
    render(<AllocationPage />)

    await screen.findByText('Ahorro')

    // Fill the amount input (label "Importe a repartir")
    await user.type(screen.getByLabelText('Importe a repartir'), '1000')
    await user.click(screen.getByRole('button', { name: /Calcular/i }))

    await waitFor(() =>
      expect(distribute).toHaveBeenCalledWith(1000, false),
    )

    // Distribution lines appear
    expect(await screen.findByText('+600,00 €')).toBeInTheDocument()
    expect(screen.getByText('+400,00 €')).toBeInTheDocument()
  })
})
