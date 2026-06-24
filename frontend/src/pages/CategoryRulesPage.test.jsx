import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CategoryRulesPage from './CategoryRulesPage'
import { listRules, createRule } from '../api/categoryRules'
import { listCategories } from '../api/categories'

vi.mock('../api/categoryRules', () => ({
  listRules: vi.fn(),
  createRule: vi.fn(),
  updateRule: vi.fn(),
  removeRule: vi.fn(),
}))
vi.mock('../api/categories', () => ({ listCategories: vi.fn() }))

const CATEGORIES = [{ id: 1, name: 'Comida', type: 'EXPENSE', color: '#ef4444', active: true }]

beforeEach(() => {
  vi.clearAllMocks()
  listCategories.mockResolvedValue(CATEGORIES)
})

describe('CategoryRulesPage', () => {
  it('lists existing rules', async () => {
    listRules.mockResolvedValue([
      { id: 7, matchText: 'MERCADONA', categoryId: 1, categoryName: 'Comida', categoryColor: '#ef4444' },
    ])
    render(<CategoryRulesPage />)
    expect(await screen.findByText('«MERCADONA»')).toBeInTheDocument()
    expect(screen.getByText('Comida')).toBeInTheDocument()
  })

  it('creates a rule', async () => {
    listRules.mockResolvedValue([])
    createRule.mockResolvedValue({ id: 1 })
    const user = userEvent.setup()
    render(<CategoryRulesPage />)

    await screen.findByText('Aún no hay reglas')
    await user.click(screen.getByRole('button', { name: /Nueva regla/i }))

    await user.type(screen.getByLabelText('Si la descripción contiene'), 'AMAZON')
    await user.click(screen.getByRole('button', { name: 'Crear regla' }))

    await waitFor(() =>
      expect(createRule).toHaveBeenCalledWith({ matchText: 'AMAZON', categoryId: 1 }),
    )
  })
})
