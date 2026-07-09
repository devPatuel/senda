import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import TransactionsPage from './TransactionsPage'
import { listCategories } from '../api/categories'
import {
  listTransactions,
  createTransaction,
  updateTransaction,
  removeTransaction,
} from '../api/transactions'
import { todayISO } from '../lib/format'

vi.mock('../api/categories', () => ({
  listCategories: vi.fn(),
}))

vi.mock('../api/transactions', () => ({
  listTransactions: vi.fn(),
  createTransaction: vi.fn(),
  updateTransaction: vi.fn(),
  removeTransaction: vi.fn(),
}))

const CATEGORIES = [
  { id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', active: true },
  { id: 2, name: 'Transporte', type: 'EXPENSE', color: '#3b82f6', active: true },
  { id: 3, name: 'Nómina', type: 'INCOME', color: '#10b981', active: true },
]

const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

const EXISTING_TRANSACTION = {
  id: 7,
  categoryId: 1,
  categoryName: 'Comida',
  categoryColor: '#ef4444',
  type: 'EXPENSE',
  amount: 20,
  date: '2026-06-10',
  description: 'Cena',
  createdAt: '2026-06-10T12:00:00Z',
}

const PAGE_WITH_ONE = {
  content: [EXISTING_TRANSACTION],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

function renderPage() {
  return render(
    <MemoryRouter>
      <TransactionsPage />
    </MemoryRouter>,
  )
}

function categoryOptions() {
  const select = screen.getByLabelText('Categoría')
  return within(select)
    .getAllByRole('option')
    .map((option) => option.textContent)
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listCategories.mockResolvedValue(CATEGORIES)
    listTransactions.mockResolvedValue(EMPTY_PAGE)
  })

  it('filters the category select by the selected type in the form', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Nuevo movimiento' }))

    // Default type is expense
    expect(categoryOptions()).toEqual([
      'Selecciona una categoría',
      'Comida',
      'Transporte',
    ])

    await user.click(screen.getByRole('button', { name: 'Ingreso' }))
    expect(categoryOptions()).toEqual(['Selecciona una categoría', 'Nómina'])
  })

  it('validates the form and does not call the API with invalid data', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Nuevo movimiento' }))
    await user.click(screen.getByRole('button', { name: 'Crear movimiento' }))

    expect(await screen.findByText('Elige una categoría')).toBeInTheDocument()
    expect(screen.getByText('Introduce un importe mayor que cero')).toBeInTheDocument()
    expect(createTransaction).not.toHaveBeenCalled()
  })

  it('sends the correct payload when creating a transaction', async () => {
    createTransaction.mockResolvedValue({ id: 99 })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Nuevo movimiento' }))
    await user.selectOptions(screen.getByLabelText('Categoría'), '1')
    await user.type(screen.getByLabelText('Importe (€)'), '12.5')
    await user.type(screen.getByLabelText('Descripción (opcional)'), 'Pan')
    await user.click(screen.getByRole('button', { name: 'Crear movimiento' }))

    await waitFor(() =>
      expect(createTransaction).toHaveBeenCalledWith({
        categoryId: 1,
        type: 'EXPENSE',
        amount: 12.5,
        date: todayISO(),
        description: 'Pan',
      }),
    )
  })

  it('sends the correct payload when editing a transaction', async () => {
    listTransactions.mockResolvedValue(PAGE_WITH_ONE)
    updateTransaction.mockResolvedValue(EXISTING_TRANSACTION)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Cena')
    await user.click(screen.getByRole('button', { name: 'Editar movimiento' }))

    const amountInput = screen.getByLabelText('Importe (€)')
    expect(amountInput).toHaveValue(20)

    await user.clear(amountInput)
    await user.type(amountInput, '25')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() =>
      expect(updateTransaction).toHaveBeenCalledWith(7, {
        categoryId: 1,
        type: 'EXPENSE',
        amount: 25,
        date: '2026-06-10',
        description: 'Cena',
      }),
    )
  })

  it('shows the inactive current category when editing and keeps it on submit', async () => {
    // The transaction's category was soft-deleted: it must stay visible and
    // selected so the user can edit other fields without changing it
    listCategories.mockResolvedValue([
      { id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', active: false },
      { id: 2, name: 'Transporte', type: 'EXPENSE', color: '#3b82f6', active: true },
    ])
    listTransactions.mockResolvedValue(PAGE_WITH_ONE)
    updateTransaction.mockResolvedValue(EXISTING_TRANSACTION)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Cena')
    await user.click(screen.getByRole('button', { name: 'Editar movimiento' }))

    const select = screen.getByLabelText('Categoría')
    expect(select).toHaveValue('1')
    expect(within(select).getByRole('option', { name: 'Comida (inactiva)' })).toBeDisabled()

    const amountInput = screen.getByLabelText('Importe (€)')
    await user.clear(amountInput)
    await user.type(amountInput, '30')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() =>
      expect(updateTransaction).toHaveBeenCalledWith(
        7,
        expect.objectContaining({ categoryId: 1, amount: 30 }),
      ),
    )
  })

  it('deletes a transaction only after confirmation', async () => {
    listTransactions.mockResolvedValue(PAGE_WITH_ONE)
    removeTransaction.mockResolvedValue(null)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Cena')
    await user.click(screen.getByRole('button', { name: 'Eliminar movimiento' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(removeTransaction).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    await waitFor(() => expect(removeTransaction).toHaveBeenCalledWith(7))
  })

  it('keeps the loaded list visible and shows a banner when the delete fails', async () => {
    listTransactions.mockResolvedValue(PAGE_WITH_ONE)
    removeTransaction.mockRejectedValue(new Error('No se ha podido eliminar el movimiento'))
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Cena')
    await user.click(screen.getByRole('button', { name: 'Eliminar movimiento' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se ha podido eliminar el movimiento',
    )
    // The list does not unmount and no full-screen retry replaces it
    expect(screen.getByText('Cena')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('does not dismiss the confirm dialog while the delete is in flight', async () => {
    listTransactions.mockResolvedValue(PAGE_WITH_ONE)
    let resolveDelete
    removeTransaction.mockReturnValue(
      new Promise((resolve) => {
        resolveDelete = resolve
      }),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Cena')
    await user.click(screen.getByRole('button', { name: 'Eliminar movimiento' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    // Escape, overlay click and the X must be inert while the request runs
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cerrar' })).toBeDisabled()

    resolveDelete(null)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('scopes listing, category loading and creation to a couple space when spaceId is set', async () => {
    createTransaction.mockResolvedValue({ id: 99 })
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TransactionsPage spaceId={7} />
      </MemoryRouter>,
    )

    await waitFor(() =>
      expect(listTransactions).toHaveBeenCalledWith(expect.objectContaining({ spaceId: 7 })),
    )
    expect(listCategories).toHaveBeenCalledWith(expect.objectContaining({ spaceId: 7 }))

    await user.click(await screen.findByRole('button', { name: 'Nuevo movimiento' }))
    await user.selectOptions(screen.getByLabelText('Categoría'), '1')
    await user.type(screen.getByLabelText('Importe (€)'), '12.5')
    await user.click(screen.getByRole('button', { name: 'Crear movimiento' }))

    await waitFor(() =>
      expect(createTransaction).toHaveBeenCalledWith(expect.objectContaining({ spaceId: 7 })),
    )
  })

  it('clamps to the last available page when the current page falls out of range', async () => {
    listTransactions.mockImplementation(({ page = 0 }) =>
      page === 0
        ? Promise.resolve({ ...PAGE_WITH_ONE, totalElements: 21, totalPages: 2 })
        : // Data changed elsewhere: the requested page no longer exists
          Promise.resolve({ content: [], page, size: 20, totalElements: 1, totalPages: 1 }),
    )
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Cena')
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))

    await waitFor(() =>
      expect(listTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 })),
    )
    expect(await screen.findByText('Cena')).toBeInTheDocument()
    expect(screen.queryByText('Aún no hay movimientos')).not.toBeInTheDocument()
  })
})
