import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import RegisterPage from './RegisterPage'

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <AuthProvider>
        <RegisterPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function fillAndSubmit(user) {
  await user.type(screen.getByLabelText('Nombre'), 'Intruso')
  await user.type(screen.getByLabelText('Email'), 'intruso@test.com')
  await user.type(screen.getByLabelText('Contraseña'), 'password123')
  await user.type(screen.getByLabelText('Repite la contraseña'), 'password123')
  await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('explains in Spanish that sign-up is closed when the API answers 403', async () => {
    const user = userEvent.setup()
    fetch.mockResolvedValue(
      jsonResponse(
        { status: 403, error: 'Forbidden', message: 'Registration is closed' },
        403,
      ),
    )
    renderRegister()

    await fillAndSubmit(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El registro está cerrado en esta instalación de Senda',
    )
  })
})
