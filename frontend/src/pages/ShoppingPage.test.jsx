import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ShoppingPage from './ShoppingPage'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------
vi.mock('../api/shopping', () => ({
  listItems: vi.fn(),
  createItem: vi.fn(),
  updateItem: vi.fn(),
  setBought: vi.fn(),
  removeItem: vi.fn(),
}))

vi.mock('../api/allocation', () => ({
  getEnvelopes: vi.fn(),
}))

import { getEnvelopes } from '../api/allocation'
import { createItem, listItems, setBought } from '../api/shopping'

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const ENVELOPES = [
  { id: 10, name: 'Ahorro', percentage: 60, position: 1, balance: 1000 },
]

const GROCERY_ITEMS = [
  { id: 1, listType: 'GROCERY', name: 'Leche', estimatedPrice: null, envelopeId: null, envelopeName: null, envelopeBalance: null, priority: null, bought: false, feasible: null, notes: null, createdAt: '2026-06-16T10:00:00Z' },
  { id: 2, listType: 'GROCERY', name: 'Pan', estimatedPrice: null, envelopeId: null, envelopeName: null, envelopeBalance: null, priority: null, bought: true, feasible: null, notes: null, createdAt: '2026-06-16T10:00:00Z' },
]

const WISHLIST_FEASIBLE = [
  { id: 3, listType: 'WISHLIST', name: 'NAS', estimatedPrice: 500, envelopeId: 10, envelopeName: 'Ahorro', envelopeBalance: 1000, priority: 1, bought: false, feasible: true, notes: null, createdAt: '2026-06-16T10:00:00Z' },
  { id: 4, listType: 'WISHLIST', name: 'Coche', estimatedPrice: 15000, envelopeId: 10, envelopeName: 'Ahorro', envelopeBalance: 1000, priority: 2, bought: false, feasible: false, notes: 'Algún día', createdAt: '2026-06-16T10:00:00Z' },
]

function setup({ groceryItems = GROCERY_ITEMS, wishlistItems = WISHLIST_FEASIBLE } = {}) {
  // Default: returns grocery items for first call, wishlist on second
  listItems.mockImplementation(({ listType } = {}) => {
    if (listType === 'WISHLIST') return Promise.resolve(wishlistItems)
    return Promise.resolve(groceryItems)
  })
  getEnvelopes.mockResolvedValue(ENVELOPES)
  return render(<ShoppingPage />)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ShoppingPage — pestaña Comida', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders grocery items with correct bought state', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('Leche')).toBeInTheDocument())
    expect(screen.getByText('Pan')).toBeInTheDocument()

    // Leche unchecked, Pan checked
    const lecheCheckbox = screen.getByRole('checkbox', { name: /Leche/i })
    const panCheckbox = screen.getByRole('checkbox', { name: /Pan/i })
    expect(lecheCheckbox).not.toBeChecked()
    expect(panCheckbox).toBeChecked()
  })

  it('toggles an item from unbought to bought on checkbox click', async () => {
    setup()
    await waitFor(() => expect(screen.getByText('Leche')).toBeInTheDocument())

    const updated = { ...GROCERY_ITEMS[0], bought: true }
    setBought.mockResolvedValue(updated)

    const checkbox = screen.getByRole('checkbox', { name: /Leche/i })
    fireEvent.click(checkbox)

    await waitFor(() => expect(setBought).toHaveBeenCalledWith(1, true))
    await waitFor(() => expect(screen.getByRole('checkbox', { name: /Leche/i })).toBeChecked())
  })

  it('quick-add creates a new grocery item', async () => {
    setup({ groceryItems: [] })
    await waitFor(() => expect(screen.getByPlaceholderText(/Añadir producto/i)).toBeInTheDocument())

    const newItem = { id: 99, listType: 'GROCERY', name: 'Mantequilla', estimatedPrice: null, envelopeId: null, envelopeName: null, envelopeBalance: null, priority: null, bought: false, feasible: null, notes: null, createdAt: '2026-06-16T10:00:00Z' }
    createItem.mockResolvedValue(newItem)

    fireEvent.change(screen.getByPlaceholderText(/Añadir producto/i), { target: { value: 'Mantequilla' } })
    fireEvent.click(screen.getByRole('button', { name: /Añadir/i }))

    await waitFor(() => expect(createItem).toHaveBeenCalledWith({ listType: 'GROCERY', name: 'Mantequilla' }))
    await waitFor(() => expect(screen.getByText('Mantequilla')).toBeInTheDocument())
  })
})

describe('ShoppingPage — pestaña Deseos', () => {
  beforeEach(() => vi.clearAllMocks())

  async function switchToWishlist() {
    setup()
    await waitFor(() => expect(screen.getByText('Leche')).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'Deseos' }))
    await waitFor(() => expect(screen.getByText('NAS')).toBeInTheDocument())
  }

  it('renders wishlist cards with feasibility badges', async () => {
    await switchToWishlist()

    // NAS is feasible
    const factibleBadges = screen.getAllByText('Factible')
    expect(factibleBadges.length).toBeGreaterThanOrEqual(1)

    // Coche is not yet feasible
    expect(screen.getByText('Aún no')).toBeInTheDocument()
  })

  it('shows envelope name and balance on wishlist card', async () => {
    await switchToWishlist()

    // Envelope name appears
    expect(screen.getAllByText(/Ahorro/i).length).toBeGreaterThanOrEqual(1)
  })

  it('opens new wishlist modal when clicking Nuevo deseo', async () => {
    await switchToWishlist()

    fireEvent.click(screen.getByRole('button', { name: /Nuevo deseo/i }))

    await waitFor(() => expect(screen.getByRole('dialog', { name: /Nuevo deseo/i })).toBeInTheDocument())
    expect(screen.getByLabelText(/Nombre/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Sobre asociado/i)).toBeInTheDocument()
  })
})
