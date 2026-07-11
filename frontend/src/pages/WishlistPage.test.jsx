import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WishlistPage from './WishlistPage'

vi.mock('../api/wishlist', () => ({
  getWishlist: vi.fn(),
  createWishItem: vi.fn(),
  updateWishItem: vi.fn(),
  removeWishItem: vi.fn(),
}))

import { getWishlist } from '../api/wishlist'

describe('WishlistPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getWishlist.mockResolvedValue({
      items: [{ id: 1, name: 'NAS', price: 500, imageUrl: null, productUrl: 'https://x', comment: 'nota', priority: 1 }],
      total: 500,
    })
  })

  it('renders items and the total', async () => {
    render(<WishlistPage />)
    expect(await screen.findByText('NAS')).toBeInTheDocument()
    expect(screen.getByText(/Total:/)).toBeInTheDocument()
    // 500,00 appears twice: the total chip and the item's price
    expect(screen.getAllByText(/500,00/).length).toBeGreaterThanOrEqual(1)
  })

  it('opens the new-wish modal', async () => {
    render(<WishlistPage />)
    await screen.findByText('NAS')
    fireEvent.click(screen.getByRole('button', { name: /nuevo deseo/i }))
    await waitFor(() => expect(screen.getByRole('button', { name: /^guardar$/i })).toBeInTheDocument())
  })
})
