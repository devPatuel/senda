import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProductsPage from './ProductsPage'

vi.mock('../api/products', () => ({
  listProducts: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  removeProduct: vi.fn(),
  addPrice: vi.fn(),
  listPrices: vi.fn(),
}))

import { addPrice, createProduct, listPrices, listProducts } from '../api/products'

const PRODUCTS = [
  {
    id: 1, name: 'Leche', unitType: 'WEIGHT', amount: 1, unit: 'L',
    createdAt: '2026-07-11T10:00:00Z',
    currentPrices: [
      { supermarket: 'Lidl', price: 1.1, recordedAt: '2026-07-11T10:00:00Z' },
      { supermarket: 'Mercadona', price: 1.25, recordedAt: '2026-07-10T10:00:00Z' },
    ],
  },
]

function flush() {
  return waitFor(() => expect(listProducts).toHaveBeenCalled())
}

describe('ProductsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listProducts.mockResolvedValue(PRODUCTS)
  })

  it('renders products with the cheapest current price first', async () => {
    render(<ProductsPage />)
    await flush()
    expect(await screen.findByText('Leche')).toBeInTheDocument()
    expect(screen.getByText('Lidl')).toBeInTheDocument()
    expect(screen.getByText('Mercadona')).toBeInTheDocument()
  })

  it('creates a product from the modal', async () => {
    createProduct.mockResolvedValue({
      id: 2, name: 'Pan', unitType: 'QUANTITY', amount: 1, unit: 'ud',
      createdAt: '2026-07-11T10:00:00Z', currentPrices: [],
    })
    render(<ProductsPage />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: /nuevo producto/i }))
    fireEvent.change(screen.getByLabelText(/nombre/i), { target: { value: 'Pan' } })
    fireEvent.change(screen.getByLabelText(/cantidad/i), { target: { value: '1' } })
    fireEvent.change(screen.getByRole('textbox', { name: /unidad \(/i }), { target: { value: 'ud' } })
    fireEvent.click(screen.getByRole('button', { name: /^guardar$/i }))

    await waitFor(() => expect(createProduct).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Pan', unit: 'ud' })))
  })

  it('registers a price for a product', async () => {
    addPrice.mockResolvedValue({
      id: 9, price: 1.05, supermarket: 'Aldi', recordedAt: '2026-07-11T10:00:00Z',
    })
    render(<ProductsPage />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: /registrar precio/i }))
    fireEvent.change(screen.getByLabelText(/supermercado/i), { target: { value: 'Aldi' } })
    fireEvent.change(screen.getByRole('spinbutton', { name: /precio/i }), { target: { value: '1.05' } })
    fireEvent.click(screen.getByRole('button', { name: /^guardar$/i }))

    await waitFor(() => expect(addPrice).toHaveBeenCalledWith(
      1, { supermarket: 'Aldi', price: 1.05 }))
  })

  it('opens the price history', async () => {
    listPrices.mockResolvedValue([
      { id: 9, price: 1.05, supermarket: 'Aldi', recordedAt: '2026-07-11T10:00:00Z' },
    ])
    render(<ProductsPage />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: /histórico/i }))
    await waitFor(() => expect(listPrices).toHaveBeenCalledWith(1))
    expect(await screen.findByText('Aldi')).toBeInTheDocument()
  })
})
