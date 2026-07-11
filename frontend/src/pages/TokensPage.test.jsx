import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as api from '../api/tokens'
import TokensPage from './TokensPage'

vi.mock('../api/tokens')

beforeEach(() => {
  vi.resetAllMocks()
  api.listTokens.mockResolvedValue([])
})

describe('TokensPage', () => {
  it('muestra el valor en claro una sola vez al generar', async () => {
    api.createToken.mockResolvedValue({ id: 1, name: 'iPhone', value: 'senda_pat_ABC123' })
    render(
      <MemoryRouter>
        <TokensPage />
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText(/nombre del token/i), 'iPhone')
    await userEvent.click(screen.getByRole('button', { name: /generar/i }))

    await waitFor(() =>
      expect(screen.getByText('senda_pat_ABC123')).toBeInTheDocument(),
    )
    expect(api.createToken).toHaveBeenCalledWith('iPhone')
  })

  it('lista los tokens existentes con opción de revocar', async () => {
    api.listTokens.mockResolvedValue([
      { id: 7, name: 'iPhone pareja', createdAt: '2026-07-01', lastUsedAt: null, revokedAt: null },
    ])
    render(
      <MemoryRouter>
        <TokensPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('iPhone pareja')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /revocar/i })).toBeInTheDocument()
  })
})
