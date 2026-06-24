import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import InvestmentsPage from './InvestmentsPage'
import {
  listAssetClasses,
  listHoldings,
  listNfts,
  createHolding,
  addBuy,
  refreshPrices,
} from '../api/investments'

vi.mock('../api/investments', () => ({
  listAssetClasses: vi.fn(),
  listHoldings: vi.fn(),
  listNfts: vi.fn(),
  createHolding: vi.fn(),
  removeHolding: vi.fn(),
  addBuy: vi.fn(),
  setHoldingPrice: vi.fn(),
  refreshPrices: vi.fn(),
  createNft: vi.fn(),
  updateNft: vi.fn(),
  removeNft: vi.fn(),
}))

const CRIPTO = { id: 5, name: 'Cripto', pricingSource: 'CRYPTO', createdAt: '2026-06-10T12:00:00Z' }
const ORO = { id: 6, name: 'Oro', pricingSource: 'METAL', createdAt: '2026-06-10T12:00:00Z' }

const BTC = {
  id: 10,
  assetClassId: 5,
  assetClassName: 'Cripto',
  pricingSource: 'CRYPTO',
  symbol: 'BTC',
  name: 'Bitcoin',
  quantity: 2,
  avgCost: 10000,
  currentPrice: 15000,
  lastPricedAt: '2026-06-10T12:00:00Z',
  marketValue: 30000,
  pnl: 10000,
  cost: 20000,
}

const NFT = {
  id: 20,
  name: 'Punk',
  collection: 'Larva Labs',
  buyCryptoSymbol: 'ETH',
  buyCryptoAmount: 2,
  fiatValueAtPurchase: 4000,
  ourCurrentValue: 5000,
  utility: 'PFP',
  currentPurchaseValue: 6000,
  createdAt: '2026-06-10T12:00:00Z',
}

describe('InvestmentsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listAssetClasses.mockResolvedValue([CRIPTO, ORO])
    listHoldings.mockResolvedValue([BTC])
    listNfts.mockResolvedValue([NFT])
  })

  it('shows holdings grouped by asset class with market value and P&L', async () => {
    render(<InvestmentsPage />)

    await screen.findByText('Bitcoin', { exact: false })
    // Asset class headers
    expect(screen.getByRole('heading', { name: 'Cripto' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Oro' })).toBeInTheDocument()
    // Market value total card (2 * 15000 = 30000)
    expect(screen.getByText('Valor de mercado').parentElement).toHaveTextContent('30.000,00')
    // P&L is positive (+10.000,00)
    expect(screen.getByText(/\+10\.000,00/)).toBeInTheDocument()
  })

  it('refreshes prices via the refresh endpoint', async () => {
    refreshPrices.mockResolvedValue([{ ...BTC, currentPrice: 20000, marketValue: 40000, pnl: 20000 }])
    const user = userEvent.setup()
    render(<InvestmentsPage />)

    await screen.findByText('Bitcoin', { exact: false })
    await user.click(screen.getByRole('button', { name: 'Refrescar precios' }))

    await waitFor(() => expect(refreshPrices).toHaveBeenCalled())
    // Updated market value (40.000,00 €) appears both in the total card and the row
    expect((await screen.findAllByText('40.000,00 €')).length).toBeGreaterThan(0)
  })

  it('creates a new holding', async () => {
    createHolding.mockResolvedValue({ ...BTC, id: 11 })
    const user = userEvent.setup()
    render(<InvestmentsPage />)

    await screen.findByText('Bitcoin', { exact: false })
    await user.click(screen.getByRole('button', { name: 'Nueva posición' }))
    await user.type(screen.getByLabelText('Símbolo'), 'eth')
    await user.type(screen.getByLabelText('Nombre'), 'Ethereum')
    await user.type(screen.getByLabelText('Cantidad'), '1')
    await user.type(screen.getByLabelText('Coste medio'), '2000')
    await user.click(screen.getByRole('button', { name: 'Crear posición' }))

    await waitFor(() =>
      expect(createHolding).toHaveBeenCalledWith(
        expect.objectContaining({ assetClassId: 5, symbol: 'ETH', name: 'Ethereum', quantity: 1, avgCost: 2000 }),
      ),
    )
  })

  it('registers a buy on an existing holding', async () => {
    addBuy.mockResolvedValue({ ...BTC, quantity: 3, avgCost: 12000 })
    const user = userEvent.setup()
    render(<InvestmentsPage />)

    await screen.findByText('Bitcoin', { exact: false })
    await user.click(screen.getByRole('button', { name: 'Compra' }))
    await user.type(screen.getByLabelText('Cantidad'), '1')
    await user.type(screen.getByLabelText('Precio unitario'), '16000')
    await user.click(screen.getByRole('button', { name: 'Registrar compra' }))

    await waitFor(() =>
      expect(addBuy).toHaveBeenCalledWith(
        10,
        expect.objectContaining({ quantity: 1, unitPrice: 16000 }),
      ),
    )
  })

  it('shows the NFT currentPurchaseValue in the NFTs tab', async () => {
    const user = userEvent.setup()
    render(<InvestmentsPage />)

    await screen.findByText('Bitcoin', { exact: false })
    await user.click(screen.getByRole('button', { name: 'NFTs' }))

    expect(await screen.findByText('Punk', { exact: false })).toBeInTheDocument()
    // 2 ETH * 3000 = 6000 -> "Hoy esa cripto vale"
    expect(screen.getByText('Hoy esa cripto vale').parentElement).toHaveTextContent('6000,00')
  })
})
