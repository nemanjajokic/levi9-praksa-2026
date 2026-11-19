import { ApiError } from '../errors'
import { db, session, type UserRow } from './db'

/*
  The checks the services make before they act, thrown synchronously from inside an endpoint
  function (its async wrapper turns them into a rejected promise). The real services answer the
  same way: 401 without a login, 403 for the wrong role or a non-member, 400 for a bad body.
*/

/** The logged-in user, or a 401. */
export function requireUser(): UserRow {
  const user = session.userId === null ? undefined : db.users.find(u => u.id === session.userId)
  if (!user || user.isDeleted || user.isBanned) throw new ApiError({ status: 401, title: 'Unauthorized', detail: 'Log in first' })
  return user
}

/** The logged-in user if there is one; reads that change with the caller (myVote, joined) use it. */
export function optionalUser(): UserRow | undefined {
  return session.userId === null ? undefined : db.users.find(u => u.id === session.userId && !u.isDeleted)
}

/** An ADMIN, or a 401 / 403. */
export function requireAdmin(): UserRow {
  const user = requireUser()
  if (user.role !== 'ADMIN') throw new ApiError({ status: 403, title: 'Forbidden', detail: 'Admins only' })
  return user
}

/** A 400 with one field error, the shape a failed @Valid body gets. */
export function invalid(field: string, message: string): ApiError {
  return new ApiError({ status: 400, title: 'Bad Request', detail: 'Request validation failed', errors: [{ field, message }] })
}

export function notBlank(field: string, value: string | undefined | null, max: number): string {
  const text = value?.trim() ?? ''
  if (!text) throw invalid(field, 'must not be blank')
  if (text.length > max) throw invalid(field, `size must be between 1 and ${max}`)
  return text
}

/** A mock URL for an uploaded file: a blob URL in the browser, a placeholder in tests. */
export function fileUrl(file: File): string {
  return typeof URL.createObjectURL === 'function' ? URL.createObjectURL(file) : `mock://upload/${file.name}`
}
