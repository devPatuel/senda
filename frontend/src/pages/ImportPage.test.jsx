import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ImportPage from './ImportPage'
import { listCategories } from '../api/categories'
import { previewImport, commitImport } from '../api/imports'
import { listSpaces } from '../api/spaces'

vi.mock('../api/categories', () => ({ listCategories: vi.fn() }))
vi.mock('../api/imports', () => ({ previewImport: vi.fn(), commitImport: vi.fn() }))
vi.mock('../api/spaces', () => ({ listSpaces: vi.fn() }))

const CATEGORIES = [
  { id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', active: true },
  { id: 2, name: 'Nómina', type: 'INCOME', color: '#10b981', active: true },
]

function renderPage() {
  return render(
    <MemoryRouter>
      <ImportPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  listCategories.mockResolvedValue(CATEGORIES)
  listSpaces.mockResolvedValue([])
})

describe('ImportPage', () => {
  it('parses a CSV, previews with rule suggestions and imports', async () => {
    previewImport.mockResolvedValue([
      {
        date: '2026-06-01',
        description: 'Compra MERCADONA',
        amount: 20.5,
        type: 'EXPENSE',
        suggestedCategoryId: 1,
        suggestedCategoryName: 'Comida',
        duplicate: false,
      },
    ])
    commitImport.mockResolvedValue({ imported: 1, skipped: 0 })
    const user = userEvent.setup()
    renderPage()

    const csv = 'fecha;concepto;importe\n2026-06-01;Compra MERCADONA;-20,50'
    const file = new File([csv], 'extracto.csv', { type: 'text/csv' })
    await user.upload(screen.getByLabelText('Archivo CSV'), file)

    // Mapping step appears
    expect(await screen.findByText('Asigna las columnas')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Previsualizar' }))

    // Preview step: the row and its suggested category
    expect(await screen.findByText('Compra MERCADONA')).toBeInTheDocument()
    await waitFor(() =>
      expect(previewImport).toHaveBeenCalledWith(
        [{ date: '2026-06-01', description: 'Compra MERCADONA', amount: -20.5 }],
        null,
      ),
    )

    await user.click(screen.getByRole('button', { name: /Importar 1 movimiento/ }))

    await waitFor(() =>
      expect(commitImport).toHaveBeenCalledWith(
        [{ date: '2026-06-01', description: 'Compra MERCADONA', amount: 20.5, type: 'EXPENSE', categoryId: 1 }],
        null,
      ),
    )
    expect(await screen.findByText(/Importación completada/)).toBeInTheDocument()
  })

  it('unchecks duplicates by default so they are not re-imported', async () => {
    previewImport.mockResolvedValue([
      {
        date: '2026-06-01',
        description: 'Pago repetido',
        amount: 5,
        type: 'EXPENSE',
        suggestedCategoryId: 1,
        suggestedCategoryName: 'Comida',
        duplicate: true,
      },
    ])
    const user = userEvent.setup()
    renderPage()

    const file = new File(['fecha;concepto;importe\n2026-06-01;Pago repetido;-5,00'], 'e.csv', {
      type: 'text/csv',
    })
    await user.upload(screen.getByLabelText('Archivo CSV'), file)
    await user.click(await screen.findByRole('button', { name: 'Previsualizar' }))

    expect(await screen.findByText('Duplicado')).toBeInTheDocument()
    // Nothing importable -> the import button is disabled (0 rows)
    expect(screen.getByRole('button', { name: /Importar 0 movimiento/ })).toBeDisabled()
  })

  it('imports a shared statement into the couple space', async () => {
    listSpaces.mockResolvedValue([{ id: 7, name: 'Pareja', myStatus: 'ACTIVE' }])
    const SPACE_CATEGORIES = [
      { id: 9, name: 'Supermercados', type: 'EXPENSE', color: '#10b981', active: true },
    ]
    listCategories.mockImplementation(({ spaceId } = {}) =>
      Promise.resolve(spaceId ? SPACE_CATEGORIES : CATEGORIES),
    )
    previewImport.mockResolvedValue([
      {
        date: '2026-09-03',
        description: 'Mercadona',
        amount: 73.15,
        type: 'EXPENSE',
        suggestedCategoryId: null,
        suggestedCategoryName: null,
        duplicate: false,
      },
    ])
    commitImport.mockResolvedValue({ imported: 1, skipped: 0 })
    const user = userEvent.setup()
    renderPage()

    await user.selectOptions(await screen.findByLabelText('Destino'), '7')
    // The space's own categories replace the personal ones
    await waitFor(() => expect(listCategories).toHaveBeenCalledWith({ spaceId: 7 }))

    const csv = 'fecha;concepto;importe\n2026-09-03;Mercadona;-73,15'
    await user.upload(screen.getByLabelText('Archivo CSV'), new File([csv], 'extracto.csv', { type: 'text/csv' }))
    await screen.findByText('Asigna las columnas')
    await user.click(screen.getByRole('button', { name: 'Previsualizar' }))

    await waitFor(() =>
      expect(previewImport).toHaveBeenCalledWith(
        [{ date: '2026-09-03', description: 'Mercadona', amount: -73.15 }],
        7,
      ),
    )

    await screen.findByText('Mercadona')
    await user.selectOptions(screen.getByLabelText('Categoría'), '9')
    await user.click(screen.getByRole('button', { name: /Importar 1 movimiento/ }))

    await waitFor(() =>
      expect(commitImport).toHaveBeenCalledWith(
        [{ date: '2026-09-03', description: 'Mercadona', amount: 73.15, type: 'EXPENSE', categoryId: 9 }],
        7,
      ),
    )
  })
})
