import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
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
  it('la barra lateral mide lo que la ventana, no lo que la página', () => {
    const { container } = renderLayout()
    expect(container.querySelector('aside')).toHaveClass('sticky', 'top-0', 'h-screen')
  })

  it('ordena el menú con Pareja al final', async () => {
    listSpaces.mockResolvedValue([{ id: 1, name: 'Pareja', myStatus: 'ACTIVE' }])
    renderLayout()
    const nav = screen.getAllByRole('navigation', { name: 'Principal' })[0]
    await within(nav).findByRole('link', { name: 'Pareja' })
    const labels = within(nav).getAllByRole('link').map((l) => l.textContent)
    expect(labels).toEqual(['Inicio', 'Activos', 'Movimientos', 'Planificación', 'Pareja'])
    expect(within(nav).getByRole('link', { name: 'Planificación' })).toHaveAttribute('href', '/categorias')
  })

  it('Importar y las pantallas con pestaña ya no están en el menú', () => {
    renderLayout()
    for (const label of [/Importar/i, /Recurrentes/i, /Cuentas/i, /Inversiones/i, /Deudas/i, /Reparto/i, /Categorías/i, /Deseos/i]) {
      expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument()
    }
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
    expect(screen.getAllByText('Planificación').length).toBeGreaterThan(0)

    await userEvent.click(screen.getByRole('button', { name: /colapsar menú/i }))

    expect(localStorage.getItem('senda_sidebar_collapsed')).toBe('true')
  })

  it('el toggle se puede volver a expandir y anuncia su estado', async () => {
    localStorage.setItem('senda_sidebar_collapsed', 'true')
    renderLayout()
    const toggle = screen.getByRole('button', { name: /expandir menú/i })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle.parentElement).toHaveClass('flex-col')

    await userEvent.click(toggle)

    expect(screen.getByRole('button', { name: /colapsar menú/i })).toHaveAttribute('aria-expanded', 'true')
    expect(localStorage.getItem('senda_sidebar_collapsed')).toBe('false')
  })

  it('arranca colapsado si localStorage lo indica', () => {
    localStorage.setItem('senda_sidebar_collapsed', 'true')
    renderLayout()
    // Collapsed: section headers are not rendered
    expect(screen.getAllByRole('link', { name: 'Planificación' })).toHaveLength(1)
  })

  it('el drawer móvil se abre con el botón de menú', async () => {
    renderLayout()
    await userEvent.click(screen.getByRole('button', { name: /abrir menú/i }))
    expect(screen.getByRole('button', { name: /cerrar menú/i })).toBeInTheDocument()
  })
})
