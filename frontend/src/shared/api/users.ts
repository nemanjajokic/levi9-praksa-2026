import type {
  BanUserRequest,
  ChangePasswordRequest,
  DeleteUserRequest,
  PublicProfileResponse,
  UpdateUserRequest,
  UserProfileResponse,
} from './contracts/users'
import { ApiError } from './errors'
import { db, session, type UserRow } from './mock/db'
import { fileUrl, invalid, notBlank, requireAdmin, requireUser } from './mock/guards'
import { handle } from './mock/server'

/*
  auth-service, /api/users/**. Login, register and logout are not here: the auth flow is being
  rebuilt and gets its own module. The mock starts logged in as demo_user (mock/db.ts).
*/

function toProfile(u: UserRow): UserProfileResponse {
  return { id: u.id, username: u.username, email: u.email, firstName: u.firstName, lastName: u.lastName }
}

function requirePassword(user: UserRow, password: string | undefined): void {
  if (!password || password !== user.password) {
    throw new ApiError({ status: 401, title: 'Unauthorized', detail: 'Invalid password', errorType: 'INVALID_CREDENTIALS' })
  }
}

function userByName(username: string): UserRow {
  const user = db.users.find(u => u.username.toLowerCase() === username.toLowerCase() && !u.isDeleted)
  if (!user) throw new ApiError({ status: 404, title: 'Not Found', detail: `User not found: ${username}`, errorType: 'USER_NOT_FOUND' })
  return user
}

/** `GET /api/users/me`: your own profile. */
export function getMe(): Promise<UserProfileResponse> {
  return handle(() => toProfile(requireUser()))
}

/** `PATCH /api/users/me`: at least one field. Changing the email needs `currentPassword`; 409 if the email is taken. */
export function updateMe(request: UpdateUserRequest): Promise<UserProfileResponse> {
  return handle(() => {
    const me = requireUser()
    const { firstName, lastName, email } = request
    if (firstName === undefined && lastName === undefined && email === undefined) {
      throw invalid('profileFieldPresent', 'Provide at least one of firstName, lastName, or email')
    }
    const first = firstName === undefined ? undefined : notBlank('firstName', firstName, 50)
    const last = lastName === undefined ? undefined : notBlank('lastName', lastName, 50)
    const mail = email === undefined ? undefined : notBlank('email', email, 254)
    if (mail !== undefined && !/^[^\s@]+@[^\s@]+$/.test(mail)) throw invalid('email', 'must be a well-formed email address')
    if (mail !== undefined && mail !== me.email) {
      requirePassword(me, request.currentPassword)
      if (db.users.some(u => u.id !== me.id && u.email.toLowerCase() === mail.toLowerCase())) {
        throw new ApiError({ status: 409, title: 'Conflict', detail: 'Email is already taken', errorType: 'EMAIL_TAKEN' })
      }
    }
    if (first !== undefined) me.firstName = first
    if (last !== undefined) me.lastName = last
    if (mail !== undefined) me.email = mail
    return toProfile(me)
  })
}

/** `PUT /api/users/me/password`: 401 if the current one is wrong. The real one also logs you out everywhere. */
export function changePassword(request: ChangePasswordRequest): Promise<void> {
  return handle(() => {
    const me = requireUser()
    requirePassword(me, request.currentPassword)
    me.password = notBlank('newPassword', request.newPassword, 128)
  })
}

/** `PUT /api/users/me/avatar` (multipart). */
export function uploadAvatar(file: File): Promise<void> {
  return handle(() => {
    requireUser().avatarUrl = fileUrl(file)
  })
}

/** `DELETE /api/users/me/avatar` */
export function removeAvatar(): Promise<void> {
  return handle(() => {
    requireUser().avatarUrl = null
  })
}

/** `DELETE /api/users/me`: needs your password (401 if wrong). Admin accounts cannot be deleted (403). Logs you out. */
export function deleteMe(request: DeleteUserRequest): Promise<void> {
  return handle(() => {
    const me = requireUser()
    if (me.role !== 'USER') {
      throw new ApiError({ status: 403, title: 'Forbidden', detail: 'This account cannot be deleted', errorType: 'ACCOUNT_DELETION_FORBIDDEN' })
    }
    requirePassword(me, request.password)
    me.isDeleted = true
    session.userId = null
  })
}

/** `GET /api/users/{username}`: anyone's public profile. 404 for an unknown or deleted account. */
export function getPublicProfile(username: string): Promise<PublicProfileResponse> {
  return handle(() => {
    const u = userByName(username)
    return { username: u.username, firstName: u.firstName, lastName: u.lastName, avatarUrl: u.avatarUrl, createdAt: u.createdAt }
  })
}

/** `POST /api/users/{username}/ban` (ADMIN): the reason is shown to them when they try to log in. */
export function banUser(username: string, request: BanUserRequest): Promise<void> {
  return handle(() => {
    requireAdmin()
    const reason = notBlank('reason', request.reason, 500)
    const user = userByName(username)
    user.isBanned = true
    user.banReason = reason
  })
}

/** `DELETE /api/users/{username}/ban` (ADMIN) */
export function unbanUser(username: string): Promise<void> {
  return handle(() => {
    requireAdmin()
    const user = userByName(username)
    user.isBanned = false
    user.banReason = null
  })
}
