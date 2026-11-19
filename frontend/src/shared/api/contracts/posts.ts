import type { Instant, PageParams, VoteValue } from './common'

/* post-service, /api/posts/** and /api/comments/**. Removed posts and comments never come back from a
   read (404 for one, left out of lists). Comments are flat: no replies to comments. */

/** `PostResponse.java` */
export interface PostResponse {
  id: number
  title: string
  content: string
  authorId: number
  /** Copied when the post was written; usernames never change. */
  authorUsername: string
  communityId: number
  /**
   * Contract gap: the community's name, for "l/frontend" on a card in the main feed. The client
   * cannot look a community up by id. Proposed: copied at creation like `authorUsername`.
   */
  communityName: string
  /** Upvotes minus downvotes. */
  score: number
  commentCount: number
  createdAt: Instant
  updatedAt: Instant
  /** Contract gap: the caller's own vote, null when none or logged out. */
  myVote: VoteValue | null
  /** Contract gap: up to 3 image URLs, in upload order. */
  imageUrls: string[]
}

/** `CommentResponse.java` */
export interface CommentResponse {
  id: number
  postId: number
  authorId: number
  authorUsername: string
  content: string
  score: number
  createdAt: Instant
  /** Contract gap: comments are editable, so the "edited" marker needs this. */
  updatedAt: Instant
  /** Contract gap: the caller's own vote, null when none or logged out. */
  myVote: VoteValue | null
}

/** `GET /api/posts?communityId=&q=&page=&size=`: latest first. Without filters, the main page. */
export interface PostListParams extends PageParams {
  communityId?: number
  /** Searches titles (contains, any case), up to 300 characters. */
  q?: string
}

/** `GET /api/posts/top?period=`: the top 10 by score. WEEK: only posts created in the last 7 days. */
export type TopPeriod = 'ALL' | 'WEEK'

/** The JSON part of `POST /api/posts` (multipart: this plus up to 3 images). Members only. */
export interface CreatePostRequest {
  title: string
  content: string
  communityId: number
}

/** `PATCH /api/posts/{id}`: the author only. */
export interface UpdatePostRequest {
  title?: string
  content?: string
}

/** `POST /api/posts/{id}/comments` and `PATCH /api/comments/{id}` */
export interface CommentRequest {
  content: string
}
