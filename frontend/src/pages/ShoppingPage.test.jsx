import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ShoppingPage from './ShoppingPage'

vi.mock('../api/shoppingList', () => ({
  getShoppingList: vi.fn(),
  addToList: vi.fn(),
  updateListItem: vi.fn(),
  removeListItem: vi.fn(),
  clearChecked: vi.fn(),
}))
vi.mock('../api/products', () => ({
  listProducts: vi.fn(),
}))

import { getShoppingList, updateListItem } from '../api/shoppingList'
import { listProducts } from '../api/products'

function renderPage() {
  return render(
    <MemoryRouter>
      <ShoppingPage />
    </MemoryRouter>,
  )
}

describe('ShoppingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getShoppingList.mockResolvedValue({
      items: [{
        id: 1, productId: 9, productName: 'Leche', quantity: 2, checked: false,
        unitPrice: 1.10, supermarket: 'Mercadona', lineTotal: 2.20,
      }],
      estimatedTotal: 2.20,
    })
    listProducts.mockResolvedValue([{ id: 9, name: 'Leche' }, { id: 10, name: 'Pan' }])
  })

  it('renders the catalog-backed list with supermarket and estimated total', async () => {
    renderPage()
    expect(await screen.findByText('Leche')).toBeInTheDocument()
    expect(screen.getByText(/Mercadona/)).toBeInTheDocument()
    expect(screen.getByText(/Total estimado:/)).toBeInTheDocument()
  })

  it('marks an item as bought', async () => {
    updateListItem.mockResolvedValue({})
    renderPage()
    await screen.findByText('Leche')
    fireEvent.click(screen.getByRole('checkbox', { name: /marcar leche como comprado/i }))
    await waitFor(() => expect(updateListItem).toHaveBeenCalledWith(1, { checked: true }))
  })
})
