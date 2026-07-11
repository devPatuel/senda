import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import Layout from './Layout'

// Layout calls useAuth(); mock it to avoid mounting the real provider.
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({ user: { name: 'Jordi' }, logout: vi.fn() }),
}))

function renderLayout() {
  return render(
    <MemoryRouter>
      <Layout />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  localStorage.clear()
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

  it('recupera las rutas antes huérfanas (/patrimonio y /reglas)', () => {
    renderLayout()
    expect(screen.getAllByRole('link', { name: /Patrimonio/i })[0]).toHaveAttribute('href', '/patrimonio')
    expect(screen.getAllByRole('link', { name: /Reglas/i })[0]).toHaveAttribute('href', '/reglas')
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
