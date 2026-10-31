import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import Layout from './Layout'
import { getMe } from '../api/admin'
import { listSpaces } from '../api/spaces'

// Layout calls useAuth(); mock it to avoid mounting the real provider.
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({ user: { name: 'Persona' }, logout: vi.fn() }),
}))
vi.mock('../api/admin', () => ({ getMe: vi.fn() }))
vi.mock('../api/spaces', () => ({ listSpaces: vi.fn() }))

function renderLayout() {
  return render(
    <MemoryRouter>
      <Layout />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  localStorage.clear()
  getMe.mockResolvedValue({ admin: false })
  listSpaces.mockResolvedValue([])
})

describe('Layout sidebar', () => {
  it('agrupa la navegación con cabeceras de sección', () => {
    renderLayout()
    // "General", "Planificación" y "Activos y pasivos" son cabeceras únicas.
    expect(screen.getByText('General')).toBeInTheDocument()
    expect(screen.getByText('Planificación')).toBeInTheDocument()
    expect(screen.getByText('Activos y pasivos')).toBeInTheDocument()
    // "Movimientos" aparece dos veces: como cabecera de sección y como enlace.
    expect(screen.getAllByText('Movimientos').length).toBeGreaterThanOrEqual(2)
  })

  it('muestra el logo en la marca que lleva a Inicio', () => {
    const { container } = renderLayout()
    const logo = container.querySelector('img[src="/logo.svg"]')
    expect(logo).toBeInTheDocument()
    expect(logo.closest('a')).toHaveAttribute('href', '/')
  })

  it('oculta Pareja a quien no tiene espacio ni invitación', async () => {
    renderLayout()
    await waitFor(() => expect(listSpaces).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: /Pareja/i })).not.toBeInTheDocument()
  })

  it('muestra Pareja con una invitación pendiente', async () => {
    listSpaces.mockResolvedValue([{ id: 1, name: 'Pareja', myStatus: 'PENDING' }])
    renderLayout()
    expect((await screen.findAllByRole('link', { name: /Pareja/i }))[0]).toHaveAttribute('href', '/pareja')
  })

  it('mantiene Pareja visible si no se pueden cargar los espacios', async () => {
    listSpaces.mockRejectedValue(new Error('caído'))
    renderLayout()
    expect((await screen.findAllByRole('link', { name: /Pareja/i })).length).toBeGreaterThan(0)
  })

  it('solo ofrece Administración a un administrador', async () => {
    renderLayout()
    await waitFor(() => expect(getMe).toHaveBeenCalled())
    expect(screen.queryByRole('link', { name: /Administración/i })).not.toBeInTheDocument()

    cleanup()
    getMe.mockResolvedValue({ admin: true })
    renderLayout()
    expect((await screen.findAllByRole('link', { name: /Administración/i }))[0]).toHaveAttribute('href', '/admin')
  })

  it('ya no ofrece Patrimonio: su detalle vive en Inicio', () => {
    renderLayout()
    expect(screen.queryByRole('link', { name: /Patrimonio/i })).not.toBeInTheDocument()
  })

  it('no ofrece los modulos retirados (Compra, Productos, Reglas, Habitos)', () => {
    renderLayout()
    for (const label of [/Compra/i, /Productos/i, /Reglas/i, /Hábitos/i]) {
      expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument()
    }
  })

  it('enlaza el cambio de contraseña en el pie', () => {
    renderLayout()
    expect(screen.getAllByRole('link', { name: /Contraseña/i })[0]).toHaveAttribute('href', '/contrasena')
  })

  it('enlaza la pantalla de Tokens en el pie', () => {
    renderLayout()
    expect(screen.getAllByRole('link', { name: /Tokens/i })[0]).toHaveAttribute('href', '/tokens')
  })

  it('NO muestra Alertas como ítem de menú', () => {
    renderLayout()
    expect(screen.queryByRole('link', { name: /Alertas/i })).not.toBeInTheDocument()
  })

  it('el toggle colapsa y persiste en localStorage', async () => {
    renderLayout()
    expect(screen.getByText('General')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /colapsar menú/i }))

    expect(localStorage.getItem('senda_sidebar_collapsed')).toBe('true')
  })

  it('arranca colapsado si localStorage lo indica', () => {
    localStorage.setItem('senda_sidebar_collapsed', 'true')
    renderLayout()
    // Collapsed: section headers are not rendered
    expect(screen.queryByText('General')).not.toBeInTheDocument()
  })

  it('el drawer móvil se abre con el botón de menú', async () => {
    renderLayout()
    await userEvent.click(screen.getByRole('button', { name: /abrir menú/i }))
    expect(screen.getByRole('button', { name: /cerrar menú/i })).toBeInTheDocument()
  })
})
