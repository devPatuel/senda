import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SpacePage from './SpacePage'
import {
  listSpaces,
  createSpace,
  addMember,
  acceptSpace,
  declineSpace,
  leaveSpace,
  listMembers,
} from '../api/spaces'

vi.mock('../api/spaces', () => ({
  listSpaces: vi.fn(),
  createSpace: vi.fn(),
  addMember: vi.fn(),
  acceptSpace: vi.fn(),
  declineSpace: vi.fn(),
  leaveSpace: vi.fn(),
  listMembers: vi.fn(),
}))

// Stub the reused resource pages so the hub test does not drag their API calls.
vi.mock('./AccountsPage', () => ({
  default: ({ spaceId }) => <div>Cuentas stub {spaceId}</div>,
}))
vi.mock('./CategoriesPage', () => ({
  default: ({ spaceId }) => <div>Categorías stub {spaceId}</div>,
}))
vi.mock('./TransactionsPage', () => ({
  default: ({ spaceId }) => <div>Movimientos stub {spaceId}</div>,
}))

const ACTIVE_SPACE = { id: 7, name: 'Nuestra pareja', myStatus: 'ACTIVE', createdBy: 1, createdAt: '2026-07-01' }
const PENDING_SPACE = { id: 9, name: 'Invitación', myStatus: 'PENDING', createdBy: 2, createdAt: '2026-07-02' }
const MEMBERS = [
  { userId: 1, email: 'yo@mail.com', name: 'Yo', status: 'ACTIVE' },
  { userId: 2, email: 'pareja@mail.com', name: 'Pareja', status: 'ACTIVE' },
]

describe('SpacePage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listSpaces.mockResolvedValue([])
    listMembers.mockResolvedValue(MEMBERS)
  })

  it('shows the create-space form and creates a space on submit', async () => {
    createSpace.mockResolvedValue(ACTIVE_SPACE)
    const user = userEvent.setup()
    render(<SpacePage />)

    const nameInput = await screen.findByLabelText('Nombre del espacio')
    expect(nameInput).toHaveValue('Pareja')
    await user.click(screen.getByRole('button', { name: 'Crear espacio' }))

    await waitFor(() => expect(createSpace).toHaveBeenCalledWith('Pareja'))
  })

  it('shows a pending invitation banner and accepts it', async () => {
    listSpaces.mockResolvedValue([PENDING_SPACE])
    acceptSpace.mockResolvedValue(null)
    const user = userEvent.setup()
    render(<SpacePage />)

    await screen.findByRole('button', { name: 'Aceptar' })
    expect(screen.getByRole('button', { name: 'Rechazar' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Aceptar' }))

    await waitFor(() => expect(acceptSpace).toHaveBeenCalledWith(9))
  })

  it('declines a pending invitation', async () => {
    listSpaces.mockResolvedValue([PENDING_SPACE])
    declineSpace.mockResolvedValue(null)
    const user = userEvent.setup()
    render(<SpacePage />)

    await user.click(await screen.findByRole('button', { name: 'Rechazar' }))

    await waitFor(() => expect(declineSpace).toHaveBeenCalledWith(9))
  })

  it('lists members and invites by email in an active space', async () => {
    listSpaces.mockResolvedValue([ACTIVE_SPACE])
    addMember.mockResolvedValue({ userId: 3, email: 'nuevo@mail.com', name: '', status: 'PENDING' })
    const user = userEvent.setup()
    render(<SpacePage />)

    await screen.findByText('pareja@mail.com')
    await user.type(screen.getByLabelText('Invitar por email'), 'nuevo@mail.com')
    await user.click(screen.getByRole('button', { name: 'Invitar' }))

    await waitFor(() => expect(addMember).toHaveBeenCalledWith(7, 'nuevo@mail.com'))
  })

  it('switches tabs to mount the reused resource pages with spaceId', async () => {
    listSpaces.mockResolvedValue([ACTIVE_SPACE])
    const user = userEvent.setup()
    render(<SpacePage />)

    // Cuentas is the default tab.
    expect(await screen.findByText('Cuentas stub 7')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Movimientos' }))
    expect(await screen.findByText('Movimientos stub 7')).toBeInTheDocument()
    expect(screen.queryByText('Cuentas stub 7')).not.toBeInTheDocument()
  })

  it('leaves the space after confirming the dialog', async () => {
    listSpaces.mockResolvedValue([ACTIVE_SPACE])
    leaveSpace.mockResolvedValue(null)
    const user = userEvent.setup()
    render(<SpacePage />)

    await screen.findByText('pareja@mail.com')
    await user.click(screen.getByRole('button', { name: 'Salir del espacio' }))
    // Confirm in the dialog.
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Salir' }))

    await waitFor(() => expect(leaveSpace).toHaveBeenCalledWith(7))
  })
})
