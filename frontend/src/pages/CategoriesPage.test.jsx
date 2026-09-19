import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CategoriesPage from './CategoriesPage'
import { listCategories, createCategory, updateCategory, removeCategory, getBudget, assignToCategory } from '../api/categories'

vi.mock('../api/categories', () => ({
  listCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  removeCategory: vi.fn(),
  getBudget: vi.fn(),
  assignToCategory: vi.fn(),
  setCategoryTarget: vi.fn(),
}))

const COMIDA = { id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', emoji: '🍔', active: true }
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

  it('renders the category emoji next to its name', async () => {
    render(<CategoriesPage />)
    await screen.findByText('Comida')
    expect(screen.getByText('🍔')).toBeInTheDocument()
  })

  it('sends the chosen emoji when creating a category', async () => {
    createCategory.mockResolvedValue({ id: 9, name: 'Viajes', type: 'EXPENSE', color: '#10b981', emoji: '✈️', active: true })
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await screen.findByText('Comida')
    await user.click(screen.getByRole('button', { name: /Nueva categoría/i }))

    await user.type(screen.getByLabelText('Nombre'), 'Viajes')
    await user.type(screen.getByRole('textbox', { name: /emoji/i }), '✈️')
    await user.click(screen.getByRole('button', { name: 'Crear categoría' }))

    await waitFor(() =>
      expect(createCategory).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Viajes', emoji: '✈️' }),
      ),
    )
  })

  it('shows the "fixed expense" checkbox only for expense categories', async () => {
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await screen.findByText('Comida')
    await user.click(screen.getByRole('button', { name: /Nueva categoría/i }))
    expect(screen.getByLabelText('Gasto fijo')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Ingreso' }))
    expect(screen.queryByLabelText('Gasto fijo')).not.toBeInTheDocument()
  })

  it('sends transfer=true for a category that only moves money between accounts', async () => {
    createCategory.mockResolvedValue({
      id: 10, name: 'Aportaciones', type: 'INCOME', color: '#10b981', transfer: true, active: true,
    })
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await screen.findByText('Comida')
    await user.click(screen.getByRole('button', { name: /Nueva categoría/i }))
    await user.click(screen.getByRole('button', { name: 'Ingreso' }))
    await user.type(screen.getByLabelText('Nombre'), 'Aportaciones')
    // Unlike "fixed", a transfer can be an income category too
    await user.click(screen.getByLabelText('Traspaso entre cuentas propias'))
    await user.click(screen.getByRole('button', { name: 'Crear categoría' }))

    await waitFor(() =>
      expect(createCategory).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Aportaciones', transfer: true }),
      ),
    )
  })

  it('sends fixed=true when the "fixed expense" checkbox is checked', async () => {
    createCategory.mockResolvedValue({ id: 9, name: 'Alquiler', type: 'EXPENSE', color: '#10b981', fixed: true, active: true })
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await screen.findByText('Comida')
    await user.click(screen.getByRole('button', { name: /Nueva categoría/i }))
    await user.type(screen.getByLabelText('Nombre'), 'Alquiler')
    await user.click(screen.getByLabelText('Gasto fijo'))
    await user.click(screen.getByRole('button', { name: 'Crear categoría' }))

    await waitFor(() =>
      expect(createCategory).toHaveBeenCalledWith(expect.objectContaining({ name: 'Alquiler', fixed: true })),
    )
  })

  it('preselects the "fixed expense" checkbox when editing an already-fixed category', async () => {
    const vivienda = { id: 3, name: 'Vivienda', type: 'EXPENSE', color: '#8b5cf6', fixed: true, active: true }
    listCategories.mockResolvedValue([COMIDA, NOMINA, vivienda])
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await screen.findByText('Vivienda')
    await user.click(screen.getByRole('button', { name: 'Editar Vivienda' }))
    expect(screen.getByLabelText('Gasto fijo')).toBeChecked()

    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() =>
      expect(updateCategory).toHaveBeenCalledWith(3, expect.objectContaining({ fixed: true })),
    )
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

  it('scopes the list and the shared budget to a couple space when spaceId is set', async () => {
    createCategory.mockResolvedValue({ id: 9, name: 'Ocio', type: 'EXPENSE', color: '#10b981', active: true })
    const user = userEvent.setup()
    render(<CategoriesPage spaceId={7} />)

    await screen.findByText('Comida')
    expect(listCategories).toHaveBeenCalledWith(expect.objectContaining({ spaceId: 7 }))
    // The shared couple budget/envelope section is now scoped to the space
    expect(getBudget).toHaveBeenCalledWith(7)
    expect(screen.getByText('Por asignar')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Nueva categoría/ }))
    await user.type(screen.getByLabelText('Nombre'), 'Ocio')
    await user.click(screen.getByRole('button', { name: 'Crear categoría' }))

    await waitFor(() =>
      expect(createCategory).toHaveBeenCalledWith(expect.objectContaining({ name: 'Ocio', spaceId: 7 })),
    )
  })

  it('assigns to a shared envelope carrying the spaceId', async () => {
    assignToCategory.mockResolvedValue({ ...BUDGET, totalAssigned: 250, toAssign: 750 })
    const user = userEvent.setup()
    render(<CategoriesPage spaceId={7} />)

    await screen.findByText('Comida')
    await user.click(screen.getByRole('button', { name: 'Asignar' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Importe a asignar'), '50')
    await user.click(within(dialog).getByRole('button', { name: 'Asignar' }))

    await waitFor(() => expect(assignToCategory).toHaveBeenCalledWith(1, 50, 7))
  })

  it('assigns without a spaceId in personal mode', async () => {
    assignToCategory.mockResolvedValue({ ...BUDGET, totalAssigned: 250, toAssign: 750 })
    const user = userEvent.setup()
    render(<CategoriesPage />)

    await screen.findByText('Comida')
    await user.click(screen.getByRole('button', { name: 'Asignar' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Importe a asignar'), '50')
    await user.click(within(dialog).getByRole('button', { name: 'Asignar' }))

    await waitFor(() => expect(assignToCategory).toHaveBeenCalledWith(1, 50, undefined))
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
