import type { Instant } from './common'

/* auth-service, /api/users/**. Login, register and tokens are left out on purpose: the auth flow is
   being rebuilt (Authorization Server + gateway BFF) and gets its own contract. */

/** `GET /api/users/me`, `PATCH /api/users/me`: `UserProfileResponse.java`. Only ever about yourself. */
export interface UserProfileResponse {
  id: number
  username: string
  email: string
  firstName: string
  lastName: string
}

/** `PATCH /api/users/me`: at least one field. `currentPassword` is required when the email changes. */
export interface UpdateUserRequest {
  firstName?: string
  lastName?: string
  email?: string
  currentPassword?: string
}

/** `PUT /api/users/me/password` (agreed in the endpoints doc, not built). Logs you out everywhere. */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** `DELETE /api/users/me`: `DeleteUserRequest.java`. */
export interface DeleteUserRequest {
  password: string
}

/**
 * `GET /api/users/{username}`. Contract gap: the endpoint is agreed but has no response shape, and
 * `UserProfileResponse` would expose the email. Proposed: a public shape without it.
 */
export interface PublicProfileResponse {
  username: string
  firstName: string
  lastName: string
  /** null when there is no profile picture. */
  avatarUrl: string | null
  createdAt: Instant
}

/** `POST /api/users/{username}/ban` (ADMIN, agreed, not built). */
export interface BanUserRequest {
  reason: string
}
