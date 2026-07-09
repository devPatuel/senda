import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CategoriesPage from './CategoriesPage'
import { listCategories, createCategory, removeCategory, getBudget } from '../api/categories'

vi.mock('../api/categories', () => ({
  listCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  removeCategory: vi.fn(),
  getBudget: vi.fn(),
  assignToCategory: vi.fn(),
  setCategoryTarget: vi.fn(),
}))

const COMIDA = { id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', active: true }
const NOMINA = { id: 2, name: 'Nómina', type: 'INCOME', color: '#10b981', active: true }

// Comida has a 300 target with a 150 balance (50% funded).
const BUDGET = {
  totalAccounts: 1000,
  totalAssigned: 200,
  toAssign: 800,
  categories: [
    {
      id: 1,
      name: 'Comida',
      color: '#ef4444',
      balance: 150,
      spentThisMonth: 40,
      targetPercentage: null,
      targetAmount: 300,
    },
  ],
}

async function deleteComida(user) {
  await screen.findByText('Comida')
  await user.click(screen.getByRole('button', { name: 'Eliminar Comida' }))
  await user.click(screen.getByRole('button', { name: 'Eliminar' }))
}

describe('CategoriesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listCategories.mockResolvedValue([COMIDA, NOMINA])
    getBudget.mockResolvedValue(BUDGET)
  })

  it('shows the funding-target progress bar for a category with a target', async () => {
    render(<CategoriesPage />)

    await screen.findByText('Comida')
    expect(screen.getByText('objetivo 300,00 €')).toBeInTheDocument()
    // 150 / 300 = 50%
    expect(screen.getByText('50%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Objetivo' })).toBeInTheDocument()
  })

  it('reports the deactivation when the category had transactions', async () => {
    let stored = [COMIDA, NOMINA]
    listCategories.mockImplementation((opts = {}) =>
      Promise.resolve(opts.includeInactive ? stored : stored.filter((c) => c.active)),
    )
    removeCategory.mockImplementation(() => {
      // The backend deactivates instead of deleting: it had transactions
      stored = [{ ...COMIDA, active: false }, NOMINA]
      return Promise.resolve(null)
    })
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await deleteComida(user)

    expect(await screen.findByRole('status')).toHaveTextContent(
      '«Comida» tenía movimientos, así que se ha desactivado en lugar de eliminarse.',
    )
    // Inactive categories are hidden by default
    await waitFor(() => expect(screen.queryByText('Comida')).not.toBeInTheDocument())
  })

  it('does not report a failed delete when only the follow-up re-fetch fails', async () => {
    let deleted = false
    listCategories.mockImplementation((opts = {}) => {
      if (opts.includeInactive && deleted) return Promise.reject(new Error('network down'))
      return Promise.resolve(deleted ? [NOMINA] : [COMIDA, NOMINA])
    })
    removeCategory.mockImplementation(() => {
      deleted = true
      return Promise.resolve(null)
    })
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await deleteComida(user)

    // The delete itself succeeded: confirm it instead of claiming a failure
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Categoría «Comida» eliminada o desactivada.',
    )
    expect(screen.queryByText(/No se ha podido eliminar/)).not.toBeInTheDocument()
    // The keyed reload (not a manual set) refreshes the list
    await waitFor(() => expect(screen.queryByText('Comida')).not.toBeInTheDocument())
  })

  it('scopes the list to a couple space and hides the budget when spaceId is set', async () => {
    createCategory.mockResolvedValue({ id: 9, name: 'Ocio', type: 'EXPENSE', color: '#10b981', active: true })
    const user = userEvent.setup()
    render(<CategoriesPage spaceId={7} />)

    await screen.findByText('Comida')
    expect(listCategories).toHaveBeenCalledWith(expect.objectContaining({ spaceId: 7 }))
    // The budget/envelope section is personal-only: it must not appear in space mode
    expect(getBudget).not.toHaveBeenCalled()
    expect(screen.queryByText('Por asignar')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Nueva categoría/ }))
    await user.type(screen.getByLabelText('Nombre'), 'Ocio')
    await user.click(screen.getByRole('button', { name: 'Crear categoría' }))

    await waitFor(() =>
      expect(createCategory).toHaveBeenCalledWith(expect.objectContaining({ name: 'Ocio', spaceId: 7 })),
    )
  })

  it('keeps the loaded list visible and shows a banner when the delete fails', async () => {
    removeCategory.mockRejectedValue(new Error('No se ha podido eliminar la categoría'))
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await deleteComida(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se ha podido eliminar la categoría',
    )
    // The list does not unmount and no full-screen retry replaces it
    expect(screen.getByText('Comida')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })
})
