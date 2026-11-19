import type { Instant, PageParams } from './common'

/* community-service, /api/communities/**. A community's name is its id in URLs: stored without the
   "l/" prefix (the UI adds it), never changes, and matches [A-Za-z0-9_-]{1,50}. */

export const COMMUNITY_NAME_PATTERN = /^[A-Za-z0-9_-]{1,50}$/

/** One community in a list: `CommunityResponse.java`. Deliberately small; the detail has the rest. */
export interface CommunityResponse {
  name: string
  memberCount: number
}

/** `GET /api/communities/{name}`: `CommunityDetailResponse.java`. Its `id` is what lists posts. */
export interface CommunityDetailResponse {
  id: number
  name: string
  description: string | null
  memberCount: number
  createdAt: Instant
  /**
   * Contract gap: whether the caller is a member (Join or Leave, and whether they may post).
   * Proposed: present when logged in, false when logged out.
   */
  joined: boolean
}

/** `new`: newest first. `top`: most members. `hot` (activity in the last 24 h) is agreed but not built. */
export type CommunitySort = 'new' | 'top' | 'hot'

/** `GET /api/communities?sort=&name=&page=&size=` */
export interface CommunityListParams extends PageParams {
  sort?: CommunitySort
  /** Filters by name (contains, any case). */
  name?: string
  /**
   * Contract gap: "my communities" (the sidebar). Proposed: `member=me` limits the list to the
   * caller's memberships.
   */
  member?: 'me'
  /** ADMIN only (agreed, not built): the shut-down communities instead of the active ones. */
  status?: 'inactive'
}

/** `POST /api/communities` (ADMIN): `CreateCommunityRequest.java`. */
export interface CreateCommunityRequest {
  name: string
  /** Up to 2000 characters; may be empty. */
  description?: string | null
}

/** `PATCH /api/communities/{name}` (ADMIN): `UpdateCommunityRequest.java`. Only the description changes. */
export interface UpdateCommunityRequest {
  description: string
}
