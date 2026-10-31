import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WishlistPage from './WishlistPage'

vi.mock('../api/wishlist', () => ({
  getWishlist: vi.fn(),
  createWishItem: vi.fn(),
  updateWishItem: vi.fn(),
  removeWishItem: vi.fn(),
  createWishList: vi.fn(),
  renameWishList: vi.fn(),
  removeWishList: vi.fn(),
}))

import { createWishItem, createWishList, getWishlist, removeWishList } from '../api/wishlist'

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

  describe('with lists', () => {
    beforeEach(() => {
      getWishlist.mockResolvedValue({
        items: [
          { id: 1, name: 'NAS', price: 500, listId: 7 },
          { id: 2, name: 'Viaje', price: 200, listId: null },
        ],
        total: 700,
        unlistedTotal: 200,
        lists: [{ id: 7, name: 'Oficina', itemCount: 1, total: 500 }],
      })
    })

    it('shows each list with its total and filters the wishes', async () => {
      render(<WishlistPage />)
      await screen.findByText('NAS')
      expect(screen.getByText('Viaje')).toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: /Oficina/ }))

      expect(screen.getByRole('button', { name: /Oficina/ })).toHaveTextContent('500,00')
      expect(screen.getByText('NAS')).toBeInTheDocument()
      expect(screen.queryByText('Viaje')).not.toBeInTheDocument()
      expect(screen.getByText(/Total:/)).toHaveTextContent('500,00')

      fireEvent.click(screen.getByRole('button', { name: /Sin lista/ }))
      expect(screen.getByText('Viaje')).toBeInTheDocument()
      expect(screen.queryByText('NAS')).not.toBeInTheDocument()
    })

    it('saves a new wish in the open list by default', async () => {
      createWishItem.mockResolvedValue({})
      render(<WishlistPage />)
      await screen.findByText('NAS')
      fireEvent.click(screen.getByRole('button', { name: /Oficina/ }))
      fireEvent.click(screen.getByRole('button', { name: /nuevo deseo/i }))
      fireEvent.change(await screen.findByLabelText('Nombre'), { target: { value: 'Mesa' } })
      fireEvent.click(screen.getByRole('button', { name: /^guardar$/i }))

      await waitFor(() => expect(createWishItem).toHaveBeenCalled())
      expect(createWishItem.mock.calls[0][0]).toMatchObject({ name: 'Mesa', listId: 7 })
    })

    it('creates a list and explains a duplicate name', async () => {
      createWishList.mockRejectedValue(Object.assign(new Error('x'), { status: 409 }))
      render(<WishlistPage />)
      await screen.findByText('NAS')
      fireEvent.click(screen.getByRole('button', { name: /nueva lista/i }))
      fireEvent.change(await screen.findByLabelText('Nombre de la lista'), { target: { value: 'Oficina' } })
      fireEvent.click(screen.getByRole('button', { name: /^guardar$/i }))

      expect(await screen.findByText('Ya tienes una lista con ese nombre')).toBeInTheDocument()
    })

    it('asks before deleting a list', async () => {
      removeWishList.mockResolvedValue(null)
      render(<WishlistPage />)
      await screen.findByText('NAS')
      fireEvent.click(screen.getByRole('button', { name: /Oficina/ }))
      fireEvent.click(screen.getByRole('button', { name: 'Eliminar lista' }))
      expect(removeWishList).not.toHaveBeenCalled()

      fireEvent.click(screen.getAllByRole('button', { name: 'Eliminar lista' }).at(-1))
      await waitFor(() => expect(removeWishList).toHaveBeenCalledWith(7))
    })
  })
})
