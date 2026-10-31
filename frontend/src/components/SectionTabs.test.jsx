import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import SectionTabs from './SectionTabs'

const TABS = [
  { to: '/cuentas', label: 'Cuentas' },
  { to: '/deudas', label: 'Deudas' },
]

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<SectionTabs label="Activos" tabs={TABS} />}>
          <Route path="/cuentas" element={<p>Pantalla de cuentas</p>} />
          <Route path="/deudas" element={<p>Pantalla de deudas</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('SectionTabs', () => {
  it('renders the current page and marks its tab', () => {
    renderAt('/deudas')
    expect(screen.getByText('Pantalla de deudas')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Deudas' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Cuentas' })).not.toHaveAttribute('aria-current')
  })
})
