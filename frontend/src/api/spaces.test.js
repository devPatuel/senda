import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {  } from './http'
import {
  listSpaces,
  createSpace,
  addMember,
  acceptSpace,
  declineSpace,
  leaveSpace,
  listMembers,
} from './spaces'
import { listAccounts } from './accounts'
import { listCategories } from './categories'
import { listTransactions, getSummary } from './transactions'

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api/spaces', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('listSpaces does GET /spaces', async () => {
    fetch.mockResolvedValue(jsonResponse([]))

    await listSpaces()

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/spaces')
    expect(options.method).toBe('GET')
  })

  it('createSpace does POST /spaces with { name }', async () => {
    fetch.mockResolvedValue(jsonResponse({ id: 1 }, 201))

    await createSpace('Pareja')

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/spaces')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ name: 'Pareja' })
  })

  it('addMember does POST /spaces/7/members with { email }', async () => {
    fetch.mockResolvedValue(jsonResponse({ userId: 2 }, 201))

    await addMember(7, 'a@b.com')

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/spaces/7/members')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ email: 'a@b.com' })
  })

  it('acceptSpace does POST /spaces/7/accept with no body', async () => {
    fetch.mockResolvedValue(new Response(null, { status: 204 }))

    await acceptSpace(7)

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/spaces/7/accept')
    expect(options.method).toBe('POST')
    expect(options.body).toBeUndefined()
  })

  it('declineSpace does POST /spaces/7/decline with no body', async () => {
    fetch.mockResolvedValue(new Response(null, { status: 204 }))

    await declineSpace(7)

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/spaces/7/decline')
    expect(options.method).toBe('POST')
    expect(options.body).toBeUndefined()
  })

  it('leaveSpace does DELETE /spaces/7/members/me', async () => {
    fetch.mockResolvedValue(new Response(null, { status: 204 }))

    await leaveSpace(7)

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/spaces/7/members/me')
    expect(options.method).toBe('DELETE')
  })

  it('listMembers does GET /spaces/7/members', async () => {
    fetch.mockResolvedValue(jsonResponse([]))

    await listMembers(7)

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/spaces/7/members')
    expect(options.method).toBe('GET')
  })
})

describe('spaceId propagation', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('listAccounts({ spaceId: 7 }) includes ?spaceId=7', async () => {
    fetch.mockResolvedValue(jsonResponse([]))

    await listAccounts({ spaceId: 7 })

    const [url] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/accounts?spaceId=7')
  })

  it('listAccounts() without spaceId keeps the personal URL', async () => {
    fetch.mockResolvedValue(jsonResponse([]))

    await listAccounts()

    const [url] = fetch.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/accounts')
  })

  it('listCategories({ spaceId: 7 }) includes spaceId=7', async () => {
    fetch.mockResolvedValue(jsonResponse([]))

    await listCategories({ spaceId: 7 })

    const [url] = fetch.mock.calls[0]
    expect(url).toContain('spaceId=7')
  })

  it('listTransactions({ spaceId: 7 }) includes spaceId=7', async () => {
    fetch.mockResolvedValue(jsonResponse({ content: [] }))

    await listTransactions({ spaceId: 7 })

    const [url] = fetch.mock.calls[0]
    expect(url).toContain('spaceId=7')
  })

  it('listTransactions() without spaceId does not include spaceId', async () => {
    fetch.mockResolvedValue(jsonResponse({ content: [] }))

    await listTransactions()

    const [url] = fetch.mock.calls[0]
    expect(url).not.toContain('spaceId')
  })

  it('getSummary(2026, 6, 7) includes spaceId=7', async () => {
    fetch.mockResolvedValue(jsonResponse({}))

    await getSummary(2026, 6, 7)

    const [url] = fetch.mock.calls[0]
    expect(url).toContain('spaceId=7')
  })

  it('getSummary(2026, 6) without spaceId does not include spaceId', async () => {
    fetch.mockResolvedValue(jsonResponse({}))

    await getSummary(2026, 6)

    const [url] = fetch.mock.calls[0]
    expect(url).not.toContain('spaceId')
  })
})
