import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AdminPage from './AdminPage'
import { listUsers, resetUserPassword } from '../api/admin'

vi.mock('../api/admin', () => ({ listUsers: vi.fn(), resetUserPassword: vi.fn() }))
vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { id: 1, name: 'Admin' } }) }))

const USERS = [
  { id: 1, name: 'Admin', email: 'admin@example.com', createdAt: '2026-01-01T10:00:00Z', admin: true },
  { id: 2, name: 'Persona', email: 'persona@example.com', createdAt: '2026-02-01T10:00:00Z', admin: false },
]

describe('AdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listUsers.mockResolvedValue(USERS)
    resetUserPassword.mockResolvedValue(null)
  })

  it('lists accounts and offers the reset only for other people', async () => {
    render(<AdminPage />)

    expect(await screen.findByText('persona@example.com')).toBeInTheDocument()
    expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Cambiar contraseña' })).toHaveLength(1)
  })

  it('rejects a short password without calling the API', async () => {
    const user = userEvent.setup()
    render(<AdminPage />)

    await user.click(await screen.findByRole('button', { name: 'Cambiar contraseña' }))
    await user.type(screen.getByLabelText('Nueva contraseña'), 'corta')
    await user.click(screen.getAllByRole('button', { name: 'Cambiar contraseña' }).at(-1))

    expect(await screen.findByText('Debe tener al menos 8 caracteres')).toBeInTheDocument()
    expect(resetUserPassword).not.toHaveBeenCalled()
  })

  it('resets the password of another user', async () => {
    const user = userEvent.setup()
    render(<AdminPage />)

    await user.click(await screen.findByRole('button', { name: 'Cambiar contraseña' }))
    await user.type(screen.getByLabelText('Nueva contraseña'), 'nuevaclave123')
    await user.click(screen.getAllByRole('button', { name: 'Cambiar contraseña' }).at(-1))

    await waitFor(() => expect(resetUserPassword).toHaveBeenCalledWith(2, 'nuevaclave123'))
    expect(await screen.findByText('Contraseña de Persona cambiada.')).toBeInTheDocument()
  })

  it('explains a forbidden response', async () => {
    listUsers.mockRejectedValue(Object.assign(new Error('Forbidden'), { status: 403 }))
    render(<AdminPage />)

    expect(await screen.findByText('Esta pantalla es solo para administradores')).toBeInTheDocument()
  })
})
