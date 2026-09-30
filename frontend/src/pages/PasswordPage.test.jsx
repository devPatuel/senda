import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import PasswordPage from './PasswordPage'

const changePassword = vi.fn()
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({ changePassword }),
}))

async function fill(current, next, repeat) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Contraseña actual'), current)
  await user.type(screen.getByLabelText('Contraseña nueva'), next)
  await user.type(screen.getByLabelText('Repite la contraseña nueva'), repeat)
  await user.click(screen.getByRole('button', { name: 'Cambiar contraseña' }))
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PasswordPage', () => {
  it('changes the password and confirms that other sessions are closed', async () => {
    changePassword.mockResolvedValue(undefined)
    render(<PasswordPage />)

    await fill('password123', 'una-mejor-clave', 'una-mejor-clave')

    expect(changePassword).toHaveBeenCalledWith('password123', 'una-mejor-clave')
    expect(await screen.findByText(/Contraseña cambiada/)).toBeInTheDocument()
  })

  it('does not send a new password that was mistyped', async () => {
    render(<PasswordPage />)

    await fill('password123', 'una-mejor-clave', 'una-mejor-clabe')

    expect(changePassword).not.toHaveBeenCalled()
    expect(screen.getByText('Las dos contraseñas no coinciden')).toBeInTheDocument()
  })

  it('says so when the current password is wrong', async () => {
    changePassword.mockRejectedValue(Object.assign(new Error('Current password is incorrect'), { status: 422 }))
    render(<PasswordPage />)

    await fill('no-es-esta', 'una-mejor-clave', 'una-mejor-clave')

    expect(await screen.findByText('La contraseña actual no es correcta')).toBeInTheDocument()
  })
})
