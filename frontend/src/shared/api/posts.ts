import type { PageParams, PageResponse, VoteValue } from './contracts/common'
import type {
  CommentRequest,
  CommentResponse,
  CreatePostRequest,
  PostListParams,
  PostResponse,
  TopPeriod,
  UpdatePostRequest,
} from './contracts/posts'
import { ApiError } from './errors'
import { db, voteKey, type CommentRow, type PostRow, type UserRow } from './mock/db'
import { fileUrl, invalid, notBlank, optionalUser, requireUser } from './mock/guards'
import { containsIgnoreCase, handle, newestFirst, page } from './mock/server'

/*
  post-service, /api/posts/** and /api/comments/**. Mocked: every function answers from the
  in-memory data with the service's rules (docs/data-model.md, the endpoints doc). Replace each body
  with a request when the backend is up; the signatures stay.
*/

// Not fixed by the backend yet (no create-post DTO exists): the search limit, and a generous body.
const TITLE_MAX = 300
const CONTENT_MAX = 40_000
const IMAGES_MAX = 3

function livePost(id: number): PostRow {
  const post = db.posts.find(p => p.id === id && !p.removedAt)
  if (!post) throw new ApiError({ status: 404, title: 'Not Found', detail: `Post not found: ${id}` })
  return post
}

function liveComment(id: number): CommentRow {
  const comment = db.comments.find(c => c.id === id && !c.removedAt)
  if (!comment) throw new ApiError({ status: 404, title: 'Not Found', detail: `Comment not found: ${id}` })
  return comment
}

/** Posting and commenting need membership in the community (post-service asks community-service). */
function requireMember(user: UserRow, communityId: number): void {
  const community = db.communities.find(c => c.id === communityId && c.isActive)
  if (!community) throw new ApiError({ status: 404, title: 'Not Found', detail: `Community not found: ${communityId}` })
  if (!db.members.some(m => m.communityId === communityId && m.userId === user.id)) {
    throw new ApiError({ status: 403, title: 'Forbidden', detail: 'Join the community first' })
  }
}

function requireAuthor(user: UserRow, authorId: number): void {
  if (user.id !== authorId) throw new ApiError({ status: 403, title: 'Forbidden', detail: 'Only the author can do this' })
}

function requireAuthorOrAdmin(user: UserRow, authorId: number): void {
  if (user.id !== authorId && user.role !== 'ADMIN') throw new ApiError({ status: 403, title: 'Forbidden', detail: 'Only the author or an admin can do this' })
}

function checkVote(value: VoteValue): void {
  if (value !== 1 && value !== -1) throw invalid('value', 'must be 1 or -1')
}

/** Casts or changes a vote and moves the cached score by new minus old, like the service. */
function castVote(votes: Map<string, VoteValue>, target: { id: number; score: number }, userId: number, value: VoteValue | null): void {
  const key = voteKey(target.id, userId)
  const old = votes.get(key) ?? 0
  if (value === null) votes.delete(key)
  else votes.set(key, value)
  target.score += (value ?? 0) - old
}

function toPost(p: PostRow): PostResponse {
  const me = optionalUser()
  return {
    id: p.id,
    title: p.title,
    content: p.content,
    authorId: p.authorId,
    authorUsername: p.authorUsername,
    communityId: p.communityId,
    communityName: db.communities.find(c => c.id === p.communityId)?.name ?? '',
    score: p.score,
    commentCount: p.commentCount,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
    myVote: me ? (db.postVotes.get(voteKey(p.id, me.id)) ?? null) : null,
    imageUrls: p.imageUrls,
  }
}

function toComment(c: CommentRow): CommentResponse {
  const me = optionalUser()
  return {
    id: c.id,
    postId: c.postId,
    authorId: c.authorId,
    authorUsername: c.authorUsername,
    content: c.content,
    score: c.score,
    createdAt: c.createdAt,
    updatedAt: c.updatedAt,
    myVote: me ? (db.commentVotes.get(voteKey(c.id, me.id)) ?? null) : null,
  }
}

const now = () => new Date().toISOString()

// ---------------------------------------------------------------- posts

/** `GET /api/posts?communityId=&q=&page=&size=`: latest first. No filter is the main page; `q` searches titles. */
export function listPosts(params: PostListParams = {}): Promise<PageResponse<PostResponse>> {
  return handle(() => {
    const { communityId, q } = params
    if (q !== undefined && q.length > TITLE_MAX) throw invalid('q', `size must be between 0 and ${TITLE_MAX}`)
    const rows = db.posts
      .filter(p => !p.removedAt)
      .filter(p => communityId === undefined || p.communityId === communityId)
      .filter(p => !q || containsIgnoreCase(p.title, q))
      .sort(newestFirst)
    return page(rows.map(toPost), params)
  })
}

/** `GET /api/posts/top?period=ALL|WEEK`: the 10 highest scores; WEEK only counts posts created in the last 7 days. */
export function getTopPosts(period: TopPeriod = 'ALL'): Promise<PostResponse[]> {
  return handle(() => {
    const since = new Date(Date.now() - 7 * 24 * 3_600_000).toISOString()
    return db.posts
      .filter(p => !p.removedAt && (period === 'ALL' || p.createdAt >= since))
      .sort((a, b) => b.score - a.score || newestFirst(a, b))
      .slice(0, 10)
      .map(toPost)
  })
}

/** `GET /api/posts/{id}`: 404 if it does not exist or was removed. */
export function getPost(id: number): Promise<PostResponse> {
  return handle(() => toPost(livePost(id)))
}

/** `POST /api/posts` (multipart: the JSON part and up to 3 images). Members only; 201 with the post. */
export function createPost(request: CreatePostRequest, images: readonly File[] = []): Promise<PostResponse> {
  return handle(() => {
    const me = requireUser()
    const title = notBlank('title', request.title, TITLE_MAX)
    const content = notBlank('content', request.content, CONTENT_MAX)
    if (images.length > IMAGES_MAX) throw invalid('images', `at most ${IMAGES_MAX} images`)
    requireMember(me, request.communityId)
    const at = now()
    const row: PostRow = {
      id: db.nextId.post++,
      title,
      content,
      authorId: me.id,
      authorUsername: me.username,
      communityId: request.communityId,
      score: 0,
      commentCount: 0,
      imageUrls: images.map(fileUrl),
      createdAt: at,
      updatedAt: at,
      removedAt: null,
      removedBy: null,
    }
    db.posts.push(row)
    return toPost(row)
  })
}

/** `PATCH /api/posts/{id}`: title and content, the author only. 404 if removed. */
export function updatePost(id: number, request: UpdatePostRequest): Promise<PostResponse> {
  return handle(() => {
    const me = requireUser()
    const post = livePost(id)
    requireAuthor(me, post.authorId)
    if (request.title === undefined && request.content === undefined) throw invalid('title', 'give a title or content')
    if (request.title !== undefined) post.title = notBlank('title', request.title, TITLE_MAX)
    if (request.content !== undefined) post.content = notBlank('content', request.content, CONTENT_MAX)
    post.updatedAt = now()
    return toPost(post)
  })
}

/** `DELETE /api/posts/{id}`: the author or an admin. Soft: the post disappears from every read. */
export function removePost(id: number): Promise<void> {
  return handle(() => {
    const me = requireUser()
    const post = livePost(id)
    requireAuthorOrAdmin(me, post.authorId)
    post.removedAt = now()
    post.removedBy = me.id
  })
}

/** `PUT /api/posts/{id}/vote`: cast or change your vote. Any logged-in user; 422 on your own post. */
export function votePost(id: number, value: VoteValue): Promise<void> {
  return handle(() => {
    const me = requireUser()
    checkVote(value)
    const post = livePost(id)
    if (post.authorId === me.id) throw new ApiError({ status: 422, title: 'Unprocessable Entity', detail: 'You cannot vote on your own post' })
    castVote(db.postVotes, post, me.id, value)
  })
}

/** `DELETE /api/posts/{id}/vote`: take your vote back. Harmless when there is none. */
export function unvotePost(id: number): Promise<void> {
  return handle(() => {
    const me = requireUser()
    castVote(db.postVotes, livePost(id), me.id, null)
  })
}

// ---------------------------------------------------------------- comments

/** `GET /api/posts/{id}/comments?page=&size=`: newest first, flat. 404 if the post is gone. */
export function listComments(postId: number, params: PageParams = {}): Promise<PageResponse<CommentResponse>> {
  return handle(() => {
    livePost(postId)
    const rows = db.comments.filter(c => c.postId === postId && !c.removedAt).sort(newestFirst)
    return page(rows.map(toComment), params)
  })
}

/** `POST /api/posts/{id}/comments`: members of the post's community only; 201 with the comment. */
export function createComment(postId: number, request: CommentRequest): Promise<CommentResponse> {
  return handle(() => {
    const me = requireUser()
    const content = notBlank('content', request.content, CONTENT_MAX)
    const post = livePost(postId)
    requireMember(me, post.communityId)
    const at = now()
    const row: CommentRow = {
      id: db.nextId.comment++,
      postId,
      authorId: me.id,
      authorUsername: me.username,
      content,
      score: 0,
      createdAt: at,
      updatedAt: at,
      removedAt: null,
      removedBy: null,
    }
    db.comments.push(row)
    post.commentCount += 1
    return toComment(row)
  })
}

/** `PATCH /api/comments/{id}`: the author only. 404 if removed. */
export function updateComment(id: number, request: CommentRequest): Promise<CommentResponse> {
  return handle(() => {
    const me = requireUser()
    const comment = liveComment(id)
    requireAuthor(me, comment.authorId)
    comment.content = notBlank('content', request.content, CONTENT_MAX)
    comment.updatedAt = now()
    return toComment(comment)
  })
}

/** `DELETE /api/comments/{id}`: the author or an admin. Soft, and the post's comment count drops. */
export function removeComment(id: number): Promise<void> {
  return handle(() => {
    const me = requireUser()
    const comment = liveComment(id)
    requireAuthorOrAdmin(me, comment.authorId)
    comment.removedAt = now()
    comment.removedBy = me.id
    const post = db.posts.find(p => p.id === comment.postId)
    if (post) post.commentCount -= 1
  })
}

/** `PUT /api/comments/{id}/vote`: 422 on your own comment. */
export function voteComment(id: number, value: VoteValue): Promise<void> {
  return handle(() => {
    const me = requireUser()
    checkVote(value)
    const comment = liveComment(id)
    if (comment.authorId === me.id) throw new ApiError({ status: 422, title: 'Unprocessable Entity', detail: 'You cannot vote on your own comment' })
    castVote(db.commentVotes, comment, me.id, value)
  })
}

/** `DELETE /api/comments/{id}/vote`: take your vote back. */
export function unvoteComment(id: number): Promise<void> {
  return handle(() => {
    const me = requireUser()
    castVote(db.commentVotes, liveComment(id), me.id, null)
  })
}
