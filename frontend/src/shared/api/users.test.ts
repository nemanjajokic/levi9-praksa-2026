import { beforeEach, describe, expect, it } from 'vitest'
import { ApiError } from './errors'
import { mockSignInAs, resetMockDb } from './mock/db'
import { setMockLatency } from './mock/server'
import { banUser, changePassword, deleteMe, getMe, getPublicProfile, updateMe } from './users'

async function errorOf(call: Promise<unknown>): Promise<ApiError> {
  const error = await call.then(() => undefined, (e: unknown) => e)
  expect(error).toBeInstanceOf(ApiError)
  return error as ApiError
}

beforeEach(() => {
  setMockLatency(0)
  resetMockDb()
})

describe('own profile', () => {
  it('starts logged in as demo_user and 401s when logged out', async () => {
    expect((await getMe()).username).toBe('demo_user')
    mockSignInAs(null)
    expect((await errorOf(getMe())).status).toBe(401)
  })

  it('needs at least one field, and the password to change the email', async () => {
    expect((await errorOf(updateMe({}))).status).toBe(400)
    expect((await updateMe({ firstName: ' Ana ' })).firstName).toBe('Ana')
    const noPassword = await errorOf(updateMe({ email: 'new@example.com' }))
    expect([noPassword.status, noPassword.errorType]).toEqual([401, 'INVALID_CREDENTIALS'])
    expect((await updateMe({ email: 'new@example.com', currentPassword: 'password' })).email).toBe('new@example.com')
  })

  it('rejects an email someone else has with 409', async () => {
    const taken = await errorOf(updateMe({ email: 'admin@example.com', currentPassword: 'password' }))
    expect([taken.status, taken.errorType]).toEqual([409, 'EMAIL_TAKEN'])
  })

  it('changes the password only with the current one', async () => {
    expect((await errorOf(changePassword({ currentPassword: 'wrong', newPassword: 'next' }))).status).toBe(401)
    await changePassword({ currentPassword: 'password', newPassword: 'next' })
    expect((await errorOf(deleteMe({ password: 'password' }))).status).toBe(401)
  })

  it('deletes a user account and logs it out, but never an admin one', async () => {
    await deleteMe({ password: 'password' })
    expect((await errorOf(getMe())).status).toBe(401)
    expect((await errorOf(getPublicProfile('demo_user'))).status).toBe(404)
    mockSignInAs('admin')
    expect((await errorOf(deleteMe({ password: 'password' }))).errorType).toBe('ACCOUNT_DELETION_FORBIDDEN')
  })
})

describe('other people', () => {
  it('shows a public profile without the email', async () => {
    const profile = await getPublicProfile('colleague_one')
    expect(profile.username).toBe('colleague_one')
    expect(profile).not.toHaveProperty('email')
  })

  it('lets only an admin ban, and a banned user is logged out', async () => {
    expect((await errorOf(banUser('colleague_one', { reason: 'Spam' }))).status).toBe(403)
    mockSignInAs('admin')
    await banUser('colleague_one', { reason: 'Spam' })
    mockSignInAs('colleague_one')
    expect((await errorOf(getMe())).status).toBe(401)
  })
})
