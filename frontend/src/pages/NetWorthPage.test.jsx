import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import NetWorthPage from './NetWorthPage'
import { getNetWorth } from '../api/networth'
import { formatCurrency } from '../lib/format'

vi.mock('../api/networth', () => ({
  getNetWorth: vi.fn(),
}))

// Testing Library normalizes whitespace in the DOM, so the non-breaking
// space Intl puts before "€" must be normalized in the expected string too.
function visibleCurrency(value) {
  return formatCurrency(value).replace(/[\u00a0\u202f]/g, ' ')
}

// In the vitest jsdom environment Intl does not add a thousands separator,
// so formatCurrency(63470) -> "63470,00 €" (no dot separating thousands).
const FULL_DATA = {
  liquid: 2500,
  investments: 61200,
  investmentsHoldings: 60000,
  investmentsNfts: 1200,
  debtsInFavor: 120,
  debtsAgainst: 350,
  coupleShare: 0,
  net: 63470,
}

const NEGATIVE_DATA = {
  liquid: 0,
  investments: 0,
  investmentsHoldings: 0,
  investmentsNfts: 0,
  debtsInFavor: 0,
  debtsAgainst: 500,
  coupleShare: 0,
  net: -500,
}

describe('NetWorthPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the net worth and all breakdown values', async () => {
    getNetWorth.mockResolvedValue(FULL_DATA)
    render(<NetWorthPage />)

    // Hero net worth card
    expect(await screen.findByText(visibleCurrency(63470))).toBeInTheDocument()

    // Individual blocks — use getAllByText because the value can appear in
    // both the block card and the breakdown legend at the bottom.
    expect(screen.getAllByText(visibleCurrency(2500)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(61200)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(60000)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(1200)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(120)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(350)).length).toBeGreaterThan(0)
  })

  it('shows the net worth in red when it is negative', async () => {
    getNetWorth.mockResolvedValue(NEGATIVE_DATA)
    render(<NetWorthPage />)

    const netEl = await screen.findByText(visibleCurrency(-500))
    // The hero paragraph carries the red class when net < 0
    expect(netEl.className).toMatch(/text-red/)
  })

  it('shows the "Pareja (50%)" tile when coupleShare is greater than 0', async () => {
    getNetWorth.mockResolvedValue({ ...FULL_DATA, coupleShare: 200, net: 63670 })
    render(<NetWorthPage />)

    // The label appears both in the block card and the breakdown legend
    expect((await screen.findAllByText('Pareja (50%)')).length).toBeGreaterThan(0)
    expect(screen.getAllByText(visibleCurrency(200)).length).toBeGreaterThan(0)
  })

  it('hides the "Pareja (50%)" tile when coupleShare is 0', async () => {
    getNetWorth.mockResolvedValue(FULL_DATA)
    render(<NetWorthPage />)

    // Wait for the page to render, then assert the couple tile is absent
    expect(await screen.findByText(visibleCurrency(63470))).toBeInTheDocument()
    expect(screen.queryByText('Pareja (50%)')).not.toBeInTheDocument()
  })

  it('shows an error state and a retry button when the request fails', async () => {
    getNetWorth.mockRejectedValue(new Error('No se ha podido cargar el patrimonio'))
    const user = userEvent.setup()
    render(<NetWorthPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se ha podido cargar el patrimonio',
    )

    // Simulate retry: second call succeeds
    getNetWorth.mockResolvedValue(FULL_DATA)
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText(visibleCurrency(63470))).toBeInTheDocument()
    expect(getNetWorth).toHaveBeenCalledTimes(2)
  })
})
