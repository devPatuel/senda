import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import InvestmentsPage from './InvestmentsPage'
import {
  listAssetClasses,
  listHoldings,
  listNfts,
  listLots,
  createHolding,
  addBuy,
  refreshPrices,
} from '../api/investments'

vi.mock('../api/investments', () => ({
  listAssetClasses: vi.fn(),
  listHoldings: vi.fn(),
  listNfts: vi.fn(),
  listLots: vi.fn(),
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
  pnlPct: 50,
  rewardsCost: 0,
}

const ETH_UNPRICED = {
  ...BTC,
  id: 11,
  symbol: 'ETH',
  name: 'Ethereum',
  quantity: 1,
  avgCost: 2000,
  currentPrice: null,
  lastPricedAt: null,
  marketValue: null,
  pnl: null,
  pnlPct: null,
  cost: 2000,
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

async function renderLoaded() {
  const user = userEvent.setup()
  render(<InvestmentsPage />)
  await screen.findAllByText('Bitcoin', { exact: false })
  return user
}

describe('InvestmentsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listAssetClasses.mockResolvedValue([CRIPTO, ORO])
    listHoldings.mockResolvedValue([BTC])
    listNfts.mockResolvedValue([NFT])
    listLots.mockResolvedValue([])
  })

  it('shows one tab per asset class between Total and NFTs, Total selected', async () => {
    await renderLoaded()

    const tabs = screen.getAllByRole('tab').map((tab) => tab.textContent)
    expect(tabs).toEqual(['Total', 'Cripto', 'Oro', 'NFTs'])
    expect(screen.getByRole('tab', { name: 'Total' })).toHaveAttribute('aria-selected', 'true')
  })

  it('sums holdings and NFTs in the Total summary', async () => {
    await renderLoaded()

    // 30000 BTC + 5000 NFT; invested 20000 + 4000; gain 11000
    expect(screen.getByText('Valor actual').parentElement).toHaveTextContent('35.000,00')
    expect(screen.getByText('Invertido').parentElement).toHaveTextContent('24.000,00')
    expect(screen.getByText('Ganancia / pérdida').parentElement).toHaveTextContent('+11.000,00')
  })

  it('weights each holding against holdings only, leaving NFTs out', async () => {
    listHoldings.mockResolvedValue([BTC, { ...BTC, id: 12, symbol: 'SOL', name: 'Solana', marketValue: 10000 }])
    await renderLoaded()

    // 30000 of 40000 in holdings = 75 % (with the 5000 NFT it would be 66,7 %)
    const row = screen.getByText('Bitcoin', { exact: false }).closest('li')
    expect(row).toHaveTextContent('75,0 %')
  })

  it('leads each row with symbol and quantity, details below', async () => {
    await renderLoaded()

    const title = screen.getByText((_, el) => el.tagName === 'P' && el.textContent === 'BTC · 2')
    expect(title).toHaveClass('font-semibold')
    // The name moves to the details line underneath
    expect(title.nextElementSibling).toHaveTextContent('Bitcoin')
  })

  it('warns about positions without a price', async () => {
    listHoldings.mockResolvedValue([BTC, ETH_UNPRICED])
    await renderLoaded()

    expect(screen.getByText('1 posición sin precio, no incluida en el valor')).toBeInTheDocument()
  })

  it('filters the summary to the selected asset class', async () => {
    const user = await renderLoaded()

    await user.click(screen.getByRole('tab', { name: 'Cripto' }))

    // NFTs no longer count: only BTC
    expect(screen.getByText('Valor actual').parentElement).toHaveTextContent('30.000,00')
    expect(screen.getByText('Ganancia / pérdida').parentElement).toHaveTextContent('+50 %')
  })

  it('shows the empty state for an asset class without positions', async () => {
    const user = await renderLoaded()

    await user.click(screen.getByRole('tab', { name: 'Oro' }))

    expect(screen.getByText('Sin posiciones en esta clase.')).toBeInTheDocument()
    expect(screen.queryByText('Valor actual')).not.toBeInTheDocument()
  })

  it('loads the lot history when it is expanded and labels rewards', async () => {
    listLots.mockResolvedValue([
      { id: 2, quantity: 0.01, unitPrice: 50000, date: '2026-09-28', kind: 'REWARD', createdAt: '2026-09-28T00:00:00Z' },
      { id: 1, quantity: 1, unitPrice: 10000, date: '2026-06-01', kind: 'BUY', createdAt: '2026-06-01T00:00:00Z' },
    ])
    const user = await renderLoaded()

    await user.click(screen.getByRole('button', { name: 'Historial' }))

    expect(listLots).toHaveBeenCalledWith(10)
    const table = await screen.findByRole('table')
    expect(within(table).getByRole('cell', { name: 'Recompensa' })).toBeInTheDocument()
    expect(within(table).getByRole('cell', { name: 'Compra' })).toBeInTheDocument()
  })

  it('says so when a holding has no lots', async () => {
    const user = await renderLoaded()

    await user.click(screen.getByRole('button', { name: 'Historial' }))

    expect(await screen.findByText('Sin compras registradas')).toBeInTheDocument()
  })

  it('refreshes prices via the refresh endpoint', async () => {
    refreshPrices.mockResolvedValue([{ ...BTC, currentPrice: 20000, marketValue: 40000, pnl: 20000, pnlPct: 100 }])
    const user = await renderLoaded()

    await user.click(screen.getByRole('button', { name: 'Refrescar precios' }))

    await waitFor(() => expect(refreshPrices).toHaveBeenCalled())
    expect((await screen.findAllByText('40.000,00 €')).length).toBeGreaterThan(0)
  })

  it('creates a new holding', async () => {
    createHolding.mockResolvedValue({ ...BTC, id: 12 })
    const user = await renderLoaded()

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

  it('registers a buy, and a reward when chosen', async () => {
    addBuy.mockResolvedValue({ ...BTC, quantity: 3, avgCost: 12000 })
    const user = await renderLoaded()

    await user.click(screen.getByRole('button', { name: 'Compra' }))
    await user.selectOptions(screen.getByLabelText('Tipo'), 'REWARD')
    await user.type(screen.getByLabelText('Cantidad'), '1')
    await user.type(screen.getByLabelText('Precio unitario'), '16000')
    await user.click(screen.getByRole('button', { name: 'Registrar compra' }))

    await waitFor(() =>
      expect(addBuy).toHaveBeenCalledWith(10, expect.objectContaining({ quantity: 1, unitPrice: 16000, kind: 'REWARD' })),
    )
  })

  it('shows the NFT currentPurchaseValue in the NFTs tab', async () => {
    const user = await renderLoaded()

    await user.click(screen.getByRole('tab', { name: 'NFTs' }))

    expect(await screen.findByText('Punk', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('Hoy esa cripto vale').parentElement).toHaveTextContent('6000,00')
  })
})
