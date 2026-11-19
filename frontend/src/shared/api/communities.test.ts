import { beforeEach, describe, expect, it } from 'vitest'
import { ApiError } from './errors'
import { mockSignInAs, resetMockDb } from './mock/db'
import { setMockLatency } from './mock/server'
import {
  createCommunity,
  deactivateCommunity,
  getCommunity,
  joinCommunity,
  leaveCommunity,
  listCommunities,
  reactivateCommunity,
  updateCommunityDescription,
} from './communities'

async function errorOf(call: Promise<unknown>): Promise<ApiError> {
  const error = await call.then(() => undefined, (e: unknown) => e)
  expect(error).toBeInstanceOf(ApiError)
  return error as ApiError
}

beforeEach(() => {
  setMockLatency(0)
  resetMockDb()
})

describe('reading communities', () => {
  it('lists active communities only, with member counts', async () => {
    const page = await listCommunities({ size: 100 })
    expect(page.content.map(c => c.name)).not.toContain('retro')
    expect(page.totalElements).toBe(7)
    expect(page.content.every(c => c.memberCount > 0)).toBe(true)
  })

  it('sorts top by members and filters by name', async () => {
    const top = await listCommunities({ sort: 'top', size: 100 })
    const counts = top.content.map(c => c.memberCount)
    expect(counts).toEqual([...counts].sort((a, b) => b - a))
    const named = await listCommunities({ name: 'END' })
    expect(named.content.map(c => c.name).sort()).toEqual(['backend', 'frontend'])
  })

  it('lists my communities, and needs a login for that', async () => {
    const mine = await listCommunities({ member: 'me' })
    expect(mine.content.map(c => c.name).sort()).toEqual(['backend', 'frontend', 'random'])
    mockSignInAs(null)
    expect((await errorOf(listCommunities({ member: 'me' }))).status).toBe(401)
  })

  it('finds a community in any case, and 404s a shut-down one', async () => {
    expect((await getCommunity('FrontEnd')).name).toBe('frontend')
    const error = await errorOf(getCommunity('retro'))
    expect(error.status).toBe(404)
    expect(error.errorType).toBe('COMMUNITY_NOT_FOUND')
  })
})

describe('membership', () => {
  it('joins and leaves, both harmless when repeated', async () => {
    const before = await getCommunity('devops')
    expect(before.joined).toBe(false)
    await joinCommunity('devops')
    await joinCommunity('devops')
    const joined = await getCommunity('devops')
    expect(joined).toMatchObject({ joined: true, memberCount: before.memberCount + 1 })
    await leaveCommunity('devops')
    await leaveCommunity('devops')
    expect(await getCommunity('devops')).toMatchObject({ joined: false, memberCount: before.memberCount })
  })

  it('shows joined as false when logged out', async () => {
    mockSignInAs(null)
    expect((await getCommunity('frontend')).joined).toBe(false)
  })
})

describe('admin', () => {
  it('is refused for a plain user', async () => {
    expect((await errorOf(createCommunity({ name: 'mobile' }))).status).toBe(403)
  })

  it('creates, rejects a taken name in any case, and checks the name pattern', async () => {
    mockSignInAs('admin')
    const created = await createCommunity({ name: 'mobile', description: 'Apps.' })
    expect(created).toMatchObject({ name: 'mobile', memberCount: 0, joined: false })
    const taken = await errorOf(createCommunity({ name: 'FRONTEND' }))
    expect([taken.status, taken.errorType]).toEqual([409, 'COMMUNITY_NAME_TAKEN'])
    expect((await errorOf(createCommunity({ name: 'l/space name' }))).status).toBe(400)
  })

  it('shuts down, lists inactive, reactivates and edits the description', async () => {
    mockSignInAs('admin')
    await deactivateCommunity('food')
    const inactive = await listCommunities({ status: 'inactive' })
    expect(inactive.content.map(c => c.name).sort()).toEqual(['food', 'retro'])
    await reactivateCommunity('retro')
    await updateCommunityDescription('retro', { description: 'Back again.' })
    expect((await getCommunity('retro')).description).toBe('Back again.')
  })
})
