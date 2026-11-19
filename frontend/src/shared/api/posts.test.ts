import { beforeEach, describe, expect, it } from 'vitest'
import { ApiError } from './errors'
import { mockSignInAs, resetMockDb } from './mock/db'
import { setMockLatency } from './mock/server'
import {
  createComment,
  createPost,
  getPost,
  getTopPosts,
  listComments,
  listPosts,
  removeComment,
  removePost,
  unvotePost,
  updatePost,
  voteComment,
  votePost,
} from './posts'
import { getCommunity } from './communities'

/** The status of the ApiError a call rejects with. */
async function statusOf(call: Promise<unknown>): Promise<number> {
  const error = await call.then(() => undefined, (e: unknown) => e)
  expect(error).toBeInstanceOf(ApiError)
  return (error as ApiError).status
}

beforeEach(() => {
  setMockLatency(0)
  resetMockDb()
})

describe('reading posts', () => {
  it('lists the main page newest first and leaves removed posts out', async () => {
    const page = await listPosts()
    const dates = page.content.map(p => p.createdAt)
    expect(dates).toEqual([...dates].sort().reverse())
    expect(page.content.some(p => p.title === 'Removed by its author')).toBe(false)
    expect(page.totalElements).toBe(17)
  })

  it('pages like Spring and rejects a size over 100', async () => {
    const second = await listPosts({ page: 1, size: 5 })
    expect(second).toMatchObject({ page: 1, size: 5, totalPages: 4 })
    expect(second.content).toHaveLength(5)
    expect(await statusOf(listPosts({ size: 101 }))).toBe(400)
  })

  it('filters by community and searches titles in any case', async () => {
    const frontend = await getCommunity('frontend')
    const inFrontend = await listPosts({ communityId: frontend.id })
    expect(inFrontend.content.every(p => p.communityName === 'frontend')).toBe(true)
    const found = await listPosts({ q: 'TAILWIND' })
    expect(found.content.map(p => p.title)).toEqual(['CSS modules, Tailwind or something else?'])
  })

  it('gives the caller its own vote, and none when logged out', async () => {
    expect((await getPost(1)).myVote).toBe(1)
    mockSignInAs(null)
    expect((await getPost(1)).myVote).toBeNull()
  })

  it('returns 404 for a removed post', async () => {
    expect(await statusOf(getPost(18))).toBe(404)
  })

  it('keeps the top 10, and WEEK only counts the last 7 days', async () => {
    const all = await getTopPosts('ALL')
    expect(all).toHaveLength(10)
    expect(all[0]!.title).toBe('What we learned moving to Java 25')
    const week = await getTopPosts('WEEK')
    expect(week.some(p => p.title === 'What we learned moving to Java 25')).toBe(false)
  })
})

describe('writing posts', () => {
  it('lets a member post, and refuses a non-member with 403', async () => {
    const backend = await getCommunity('backend')
    const post = await createPost({ title: '  New one  ', content: 'Body', communityId: backend.id })
    expect(post).toMatchObject({ title: 'New one', authorUsername: 'demo_user', score: 0, commentCount: 0, myVote: null })
    const devops = await getCommunity('devops')
    expect(await statusOf(createPost({ title: 'x', content: 'y', communityId: devops.id }))).toBe(403)
  })

  it('needs a login, a title and at most 3 images', async () => {
    const backend = await getCommunity('backend')
    const image = new File(['x'], 'a.png')
    expect(await statusOf(createPost({ title: ' ', content: 'y', communityId: backend.id }))).toBe(400)
    expect(await statusOf(createPost({ title: 'x', content: 'y', communityId: backend.id }, [image, image, image, image]))).toBe(400)
    mockSignInAs(null)
    expect(await statusOf(createPost({ title: 'x', content: 'y', communityId: backend.id }))).toBe(401)
  })

  it('lets only the author edit, and the author or an admin remove', async () => {
    expect(await statusOf(updatePost(1, { title: 'Mine now' }))).toBe(403)
    const edited = await updatePost(3, { title: 'Edited' })
    expect(edited.title).toBe('Edited')
    expect(edited.updatedAt >= edited.createdAt).toBe(true)
    expect(await statusOf(removePost(1))).toBe(403)
    mockSignInAs('admin')
    await removePost(1)
    expect(await statusOf(getPost(1))).toBe(404)
  })
})

describe('votes', () => {
  it('moves the score by new minus old', async () => {
    const before = (await getPost(1)).score // demo_user already voted +1 here
    await votePost(1, -1)
    expect((await getPost(1)).score).toBe(before - 2)
    await unvotePost(1)
    const after = await getPost(1)
    expect(after.score).toBe(before - 1)
    expect(after.myVote).toBeNull()
  })

  it('refuses a vote on your own post or comment with 422', async () => {
    expect(await statusOf(votePost(3, 1))).toBe(422)
    const mine = await createComment(1, { content: 'Mine' })
    expect(await statusOf(voteComment(mine.id, 1))).toBe(422)
  })
})

describe('comments', () => {
  it('lists newest first and 404s when the post is gone', async () => {
    const page = await listComments(1)
    const dates = page.content.map(c => c.createdAt)
    expect(dates).toEqual([...dates].sort().reverse())
    expect(await statusOf(listComments(18))).toBe(404)
  })

  it('keeps the post comment count in step', async () => {
    const before = (await getPost(1)).commentCount
    const comment = await createComment(1, { content: 'Hello' })
    expect((await getPost(1)).commentCount).toBe(before + 1)
    await removeComment(comment.id)
    expect((await getPost(1)).commentCount).toBe(before)
  })

  it('needs membership in the post community', async () => {
    expect(await statusOf(createComment(7, { content: 'devops is not joined' }))).toBe(403)
  })
})

it('hands back copies, so editing a response never edits the mock data', async () => {
  const post = await getPost(1)
  post.title = 'changed on the client'
  expect((await getPost(1)).title).not.toBe('changed on the client')
})
