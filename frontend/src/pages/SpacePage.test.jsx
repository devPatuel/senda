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
vi.mock('../components/SpaceSummary', () => ({
  default: ({ spaceId }) => <div>Resumen stub {spaceId}</div>,
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

    await user.click(await screen.findByRole('button', { name: 'Ajustes' }))
    await screen.findByText('pareja@mail.com')
    await user.type(screen.getByLabelText('Invitar por email'), 'nuevo@mail.com')
    await user.click(screen.getByRole('button', { name: 'Invitar' }))

    await waitFor(() => expect(addMember).toHaveBeenCalledWith(7, 'nuevo@mail.com'))
  })

  it('switches tabs to mount the reused resource pages with spaceId', async () => {
    listSpaces.mockResolvedValue([ACTIVE_SPACE])
    const user = userEvent.setup()
    render(<SpacePage />)

    // Resumen is the default tab: the month's figures, not a form.
    expect(await screen.findByText('Resumen stub 7')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Movimientos' }))
    expect(await screen.findByText('Movimientos stub 7')).toBeInTheDocument()
    expect(screen.queryByText('Resumen stub 7')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cuentas' }))
    expect(await screen.findByText('Cuentas stub 7')).toBeInTheDocument()
  })

  it('leaves the space after confirming the dialog', async () => {
    listSpaces.mockResolvedValue([ACTIVE_SPACE])
    leaveSpace.mockResolvedValue(null)
    const user = userEvent.setup()
    render(<SpacePage />)

    await user.click(await screen.findByRole('button', { name: 'Ajustes' }))
    await user.click(screen.getByRole('button', { name: 'Salir del espacio' }))
    // Confirm in the dialog.
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Salir' }))

    await waitFor(() => expect(leaveSpace).toHaveBeenCalledWith(7))
  })

  it('keeps members and invites out of the way until asked for', async () => {
    listSpaces.mockResolvedValue([ACTIVE_SPACE])
    const user = userEvent.setup()
    render(<SpacePage />)

    // Landing on the space shows its figures, not an invite form
    expect(await screen.findByText('Resumen stub 7')).toBeInTheDocument()
    expect(screen.queryByLabelText('Invitar por email')).not.toBeInTheDocument()
    expect(screen.queryByText('pareja@mail.com')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Ajustes' }))
    expect(await screen.findByLabelText('Invitar por email')).toBeInTheDocument()
  })
})
