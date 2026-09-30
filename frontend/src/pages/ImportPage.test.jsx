import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ImportPage from './ImportPage'
import { listCategories } from '../api/categories'
import { previewImport, commitImport } from '../api/imports'
import { listSpaces } from '../api/spaces'
import { readXlsx } from '../lib/xlsx'

vi.mock('../lib/xlsx', () => ({ readXlsx: vi.fn() }))
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
  it('parses a CSV, previews the rows and imports them', async () => {
    previewImport.mockResolvedValue([
      {
        date: '2026-06-01',
        description: 'Compra MERCADONA',
        amount: 20.5,
        type: 'EXPENSE',
        duplicate: false,
      },
    ])
    commitImport.mockResolvedValue({ imported: 1, skipped: 0 })
    const user = userEvent.setup()
    renderPage()

    const csv = 'fecha;concepto;importe\n2026-06-01;Compra MERCADONA;-20,50'
    const file = new File([csv], 'extracto.csv', { type: 'text/csv' })
    await user.upload(screen.getByLabelText('Archivo CSV o Excel'), file)

    // Mapping step appears
    expect(await screen.findByText('Asigna las columnas')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Previsualizar' }))

    // Preview step: the row, with the category still to be picked
    expect(await screen.findByText('Compra MERCADONA')).toBeInTheDocument()
    await waitFor(() =>
      expect(previewImport).toHaveBeenCalledWith(
        [{ date: '2026-06-01', description: 'Compra MERCADONA', amount: -20.5 }],
        null,
      ),
    )

    await user.selectOptions(screen.getByLabelText('Categoría'), '1')
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
    await user.upload(screen.getByLabelText('Archivo CSV o Excel'), file)
    await user.click(await screen.findByRole('button', { name: 'Previsualizar' }))

    expect(await screen.findByText('Duplicado')).toBeInTheDocument()
    // Nothing importable -> the import button is disabled (0 rows)
    expect(screen.getByRole('button', { name: /Importar 0 movimiento/ })).toBeDisabled()
  })

  it('selects every row or none with a single control', async () => {
    previewImport.mockResolvedValue([
      { date: '2026-06-01', description: 'Compra', amount: 5, type: 'EXPENSE', duplicate: false },
      { date: '2026-06-02', description: 'Pago repetido', amount: 7, type: 'EXPENSE', duplicate: true },
    ])
    const user = userEvent.setup()
    renderPage()

    const csv = 'fecha;concepto;importe\n2026-06-01;Compra;-5,00\n2026-06-02;Pago repetido;-7,00'
    await user.upload(screen.getByLabelText('Archivo CSV o Excel'), new File([csv], 'e.csv'))
    await user.click(await screen.findByRole('button', { name: 'Previsualizar' }))

    const selectAll = await screen.findByLabelText('Seleccionar todo')
    const rowChecks = () => screen.getAllByLabelText('Incluir movimiento')
    // The duplicate starts unchecked, so the selection is partial
    expect(selectAll).not.toBeChecked()
    expect(screen.getByText('1 de 2 seleccionados')).toBeInTheDocument()

    await user.click(selectAll)
    expect(rowChecks().every((c) => c.checked)).toBe(true)
    expect(screen.getByText('2 de 2 seleccionados')).toBeInTheDocument()

    await user.click(selectAll)
    expect(rowChecks().some((c) => c.checked)).toBe(false)
    expect(screen.getByText('0 de 2 seleccionados')).toBeInTheDocument()
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
    await user.upload(screen.getByLabelText('Archivo CSV o Excel'), new File([csv], 'extracto.csv', { type: 'text/csv' }))
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

  it('reads an Excel statement, skipping the account details above the table', async () => {
    readXlsx.mockResolvedValue([
      ['', '', 'CUENTA ONLINE', 'FECHA'],
      ['Movimientos'],
      ['Fecha operación', 'Fecha valor', 'Concepto', 'Importe', 'Saldo'],
      ['03/09/2026', '03/09/2026', 'Mercadona', '\u221273,15', '1.000,00'],
    ])
    previewImport.mockResolvedValue([
      { date: '2026-09-03', description: 'Mercadona', amount: 73.15, type: 'EXPENSE', duplicate: false },
    ])
    const user = userEvent.setup()
    renderPage()

    const file = new File(['binary'], 'export_excel.xlsx')
    await user.upload(screen.getByLabelText('Archivo CSV o Excel'), file)
    await screen.findByText('Asigna las columnas')
    expect(readXlsx).toHaveBeenCalledWith(file)
    await user.click(screen.getByRole('button', { name: 'Previsualizar' }))

    await waitFor(() =>
      expect(previewImport).toHaveBeenCalledWith(
        [{ date: '2026-09-03', description: 'Mercadona', amount: -73.15 }],
        null,
      ),
    )
  })

  it('explains that a file it cannot read is not imported', async () => {
    readXlsx.mockRejectedValue(new Error('not a spreadsheet'))
    const user = userEvent.setup()
    renderPage()

    await user.upload(screen.getByLabelText('Archivo CSV o Excel'), new File(['x'], 'roto.xlsx'))

    expect(await screen.findByText(/No se ha podido leer el archivo/)).toBeInTheDocument()
    expect(screen.queryByText('Asigna las columnas')).not.toBeInTheDocument()
  })
})
