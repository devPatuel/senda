import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AccountsPage from './AccountsPage'
import { listAccounts, createAccount, updateAccount, removeAccount } from '../api/accounts'

vi.mock('../api/accounts', () => ({
  listAccounts: vi.fn(),
  createAccount: vi.fn(),
  updateAccount: vi.fn(),
  removeAccount: vi.fn(),
}))

const BANCO = { id: 1, name: 'Cuenta corriente', type: 'BANK', balance: 1000, currency: 'EUR', archived: false }
const EFECTIVO = { id: 2, name: 'Caja', type: 'CASH', balance: 50.5, currency: 'EUR', archived: false }

describe('AccountsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listAccounts.mockResolvedValue([BANCO, EFECTIVO])
  })

  it('shows the accounts and the total balance of non-archived accounts', async () => {
    render(<AccountsPage />)

    await screen.findByText('Cuenta corriente')
    expect(screen.getByText('Caja')).toBeInTheDocument()
    // Total 1050,50 € rendered in the summary card
    expect(screen.getByText('Saldo total').parentElement).toHaveTextContent('1050,50')
  })

  it('archives an account through the update endpoint', async () => {
    updateAccount.mockResolvedValue({ ...BANCO, archived: true })
    const user = userEvent.setup()
    render(<AccountsPage />)

    await screen.findByText('Cuenta corriente')
    await user.click(screen.getByRole('button', { name: 'Archivar Cuenta corriente' }))

    await waitFor(() =>
      expect(updateAccount).toHaveBeenCalledWith(1, expect.objectContaining({ archived: true })),
    )
  })

  it('keeps the list visible and shows a banner when delete fails', async () => {
    removeAccount.mockRejectedValue(new Error('No se ha podido eliminar la cuenta'))
    const user = userEvent.setup()
    render(<AccountsPage />)

    await screen.findByText('Cuenta corriente')
    await user.click(screen.getByRole('button', { name: 'Eliminar Cuenta corriente' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se ha podido eliminar la cuenta')
    expect(screen.getByText('Cuenta corriente')).toBeInTheDocument()
  })

  it('creates a new account', async () => {
    createAccount.mockResolvedValue({ id: 3, name: 'Ahorro', type: 'BANK', balance: 200, currency: 'EUR', archived: false })
    const user = userEvent.setup()
    render(<AccountsPage />)

    await screen.findByText('Cuenta corriente')
    await user.click(screen.getByRole('button', { name: /Nueva cuenta/ }))
    await user.type(screen.getByLabelText('Nombre'), 'Ahorro')
    await user.clear(screen.getByLabelText('Saldo'))
    await user.type(screen.getByLabelText('Saldo'), '200')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    await waitFor(() =>
      expect(createAccount).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Ahorro', type: 'BANK', balance: 200, currency: 'EUR' }),
      ),
    )
  })
})
