import type { PageResponse } from './contracts/common'
import {
  COMMUNITY_NAME_PATTERN,
  type CommunityDetailResponse,
  type CommunityListParams,
  type CommunityResponse,
  type CreateCommunityRequest,
  type UpdateCommunityRequest,
} from './contracts/communities'
import { ApiError } from './errors'
import { db, type CommunityRow } from './mock/db'
import { invalid, optionalUser, requireAdmin, requireUser } from './mock/guards'
import { containsIgnoreCase, handle, newestFirst, page } from './mock/server'

/*
  community-service, /api/communities/**. Mocked: every function answers from the in-memory data
  with the service's rules and status codes. Replace each body with a request when the backend is up;
  the signatures stay.
*/

const memberCount = (c: CommunityRow) => db.members.filter(m => m.communityId === c.id).length

/** An active community by name (any case), or the service's 404. */
function activeCommunity(name: string): CommunityRow {
  const found = db.communities.find(c => c.name.toLowerCase() === name.toLowerCase() && c.isActive)
  if (!found) throw notFound(name)
  return found
}

/** Any community, shut down or not, for the ADMIN endpoints. */
function anyCommunity(name: string): CommunityRow {
  const found = db.communities.find(c => c.name.toLowerCase() === name.toLowerCase())
  if (!found) throw notFound(name)
  return found
}

function notFound(name: string): ApiError {
  return new ApiError({ status: 404, title: 'Not Found', detail: `Community not found: ${name}`, errorType: 'COMMUNITY_NOT_FOUND' })
}

function toDetail(c: CommunityRow): CommunityDetailResponse {
  const me = optionalUser()
  return {
    id: c.id,
    name: c.name,
    description: c.description,
    memberCount: memberCount(c),
    createdAt: c.createdAt,
    joined: !!me && db.members.some(m => m.communityId === c.id && m.userId === me.id),
  }
}

/** Activity in the last 24 h for `hot`: new posts and comments there (the real one counts upvotes too). */
function activity(c: CommunityRow): number {
  const since = new Date(Date.now() - 24 * 3_600_000).toISOString()
  const posts = db.posts.filter(p => p.communityId === c.id && !p.removedAt)
  const ids = new Set(posts.map(p => p.id))
  return posts.filter(p => p.createdAt >= since).length + db.comments.filter(m => ids.has(m.postId) && !m.removedAt && m.createdAt >= since).length
}

/** `GET /api/communities?sort=&name=&member=&status=&page=&size=`: active communities, newest first by default. */
export function listCommunities(params: CommunityListParams = {}): Promise<PageResponse<CommunityResponse>> {
  return handle(() => {
    const { sort = 'new', name, member, status } = params
    if (name !== undefined && name.length > 50) throw invalid('name', 'size must be between 0 and 50')
    if (status === 'inactive') requireAdmin()
    let rows = db.communities.filter(c => c.isActive === (status !== 'inactive'))
    if (name) rows = rows.filter(c => containsIgnoreCase(c.name, name))
    if (member === 'me') {
      const me = requireUser()
      rows = rows.filter(c => db.members.some(m => m.communityId === c.id && m.userId === me.id))
    }
    const sorted = [...rows].sort(
      sort === 'top' ? (a, b) => memberCount(b) - memberCount(a)
      : sort === 'hot' ? (a, b) => activity(b) - activity(a)
      : newestFirst,
    )
    return page(sorted.map(c => ({ name: c.name, memberCount: memberCount(c) })), params)
  })
}

/** `GET /api/communities/{name}`: 404 if it does not exist or is shut down. */
export function getCommunity(name: string): Promise<CommunityDetailResponse> {
  return handle(() => toDetail(activeCommunity(name)))
}

/** `PUT /api/communities/{name}/members/me`: join. Joining twice is harmless. */
export function joinCommunity(name: string): Promise<void> {
  return handle(() => {
    const me = requireUser()
    const c = activeCommunity(name)
    if (!db.members.some(m => m.communityId === c.id && m.userId === me.id)) {
      db.members.push({ communityId: c.id, userId: me.id, joinedAt: new Date().toISOString() })
    }
  })
}

/** `DELETE /api/communities/{name}/members/me`: leave. Leaving when not a member is harmless. */
export function leaveCommunity(name: string): Promise<void> {
  return handle(() => {
    const me = requireUser()
    const c = activeCommunity(name)
    db.members = db.members.filter(m => !(m.communityId === c.id && m.userId === me.id))
  })
}

/** `POST /api/communities` (ADMIN): 201 with the new community; 409 if the name is taken, in any case. */
export function createCommunity(request: CreateCommunityRequest): Promise<CommunityDetailResponse> {
  return handle(() => {
    const admin = requireAdmin()
    const name = request.name?.trim() ?? ''
    if (!name) throw invalid('name', 'must not be blank')
    if (!COMMUNITY_NAME_PATTERN.test(name)) throw invalid('name', 'must be a plain community name')
    if ((request.description?.length ?? 0) > 2000) throw invalid('description', 'size must be between 0 and 2000')
    if (db.communities.some(c => c.name.toLowerCase() === name.toLowerCase())) {
      throw new ApiError({ status: 409, title: 'Conflict', detail: `Community name is already taken: ${name}`, errorType: 'COMMUNITY_NAME_TAKEN' })
    }
    const row: CommunityRow = {
      id: db.nextId.community++,
      name,
      description: request.description?.trim() || null,
      creatorId: admin.id,
      isActive: true,
      createdAt: new Date().toISOString(),
    }
    db.communities.push(row)
    return toDetail(row)
  })
}

/** `PATCH /api/communities/{name}` (ADMIN): the description only; the name never changes. */
export function updateCommunityDescription(name: string, request: UpdateCommunityRequest): Promise<void> {
  return handle(() => {
    requireAdmin()
    const c = activeCommunity(name)
    if (request.description === null || request.description === undefined) throw invalid('description', 'must not be null')
    if (request.description.length > 2000) throw invalid('description', 'size must be between 0 and 2000')
    c.description = request.description
  })
}

/** `DELETE /api/communities/{name}` (ADMIN): shut down; nothing is deleted. */
export function deactivateCommunity(name: string): Promise<void> {
  return handle(() => {
    requireAdmin()
    anyCommunity(name).isActive = false
  })
}

/** `POST /api/communities/{name}/reactivate` (ADMIN): undo a shutdown. */
export function reactivateCommunity(name: string): Promise<void> {
  return handle(() => {
    requireAdmin()
    anyCommunity(name).isActive = true
  })
}
