import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import LoginPage from './LoginPage'

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the login form', () => {
    renderLogin()

    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Contraseña')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('shows client-side validation errors without calling the API', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Introduce tu email')).toBeInTheDocument()
    expect(screen.getByText('Introduce tu contraseña')).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })

  it('shows the API error message on wrong credentials', async () => {
    const user = userEvent.setup()
    fetch.mockResolvedValue(
      jsonResponse(
        { status: 401, error: 'Unauthorized', message: 'Email o contraseña incorrectos' },
        401,
      ),
    )
    renderLogin()

    await user.type(screen.getByLabelText('Email'), 'jordi@test.com')
    await user.type(screen.getByLabelText('Contraseña'), 'wrongpass')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Email o contraseña incorrectos',
    )
  })

  it('marks the fields so password managers recognise a login form', () => {
    renderLogin()

    // "username" (not "email") is what Proton Pass, 1Password and the browser look for
    expect(screen.getByLabelText('Email')).toHaveAttribute('autocomplete', 'username')
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('autocomplete', 'current-password')
  })

  it('lets the user reveal and hide the password', async () => {
    const user = userEvent.setup()
    renderLogin()
    const input = screen.getByLabelText('Contraseña')
    expect(input).toHaveAttribute('type', 'password')

    await user.type(input, 'secreto1')
    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveValue('secreto1')

    await user.click(screen.getByRole('button', { name: 'Ocultar contraseña' }))
    expect(input).toHaveAttribute('type', 'password')
  })

  it('does not submit the form when the reveal button is pressed', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))

    expect(fetch).not.toHaveBeenCalled()
    expect(screen.queryByText('Introduce tu email')).not.toBeInTheDocument()
  })
})
