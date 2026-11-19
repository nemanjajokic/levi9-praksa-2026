import type { VoteValue } from '../contracts/common'

/*
  The mock backend's tables, shaped like docs/data-model.md (rows, not responses: the endpoint
  functions turn rows into the contract types). One in-memory copy for the whole app; resetMockDb()
  rebuilds it, and tests call that before each case. Seeded people and threads are made up.
*/

export type Role = 'USER' | 'ADMIN'

export interface UserRow {
  id: number
  username: string
  email: string
  /** Mock only: the backend keeps a BCrypt hash. Every seeded account uses "password". */
  password: string
  firstName: string
  lastName: string
  role: Role
  avatarUrl: string | null
  isBanned: boolean
  banReason: string | null
  isDeleted: boolean
  createdAt: string
}

export interface CommunityRow {
  id: number
  name: string
  description: string | null
  creatorId: number
  isActive: boolean
  createdAt: string
}

export interface MemberRow {
  communityId: number
  userId: number
  joinedAt: string
}

export interface PostRow {
  id: number
  title: string
  content: string
  authorId: number
  authorUsername: string
  communityId: number
  /** Cached sum of all votes, updated by the vote delta like the backend does. */
  score: number
  commentCount: number
  imageUrls: string[]
  createdAt: string
  updatedAt: string
  removedAt: string | null
  removedBy: number | null
}

export interface CommentRow {
  id: number
  postId: number
  authorId: number
  authorUsername: string
  content: string
  score: number
  createdAt: string
  updatedAt: string
  removedAt: string | null
  removedBy: number | null
}

export interface MockDb {
  users: UserRow[]
  communities: CommunityRow[]
  members: MemberRow[]
  posts: PostRow[]
  comments: CommentRow[]
  /** The caller's votes: `${postId}:${userId}`. Seeded scores stand for other people's votes. */
  postVotes: Map<string, VoteValue>
  commentVotes: Map<string, VoteValue>
  nextId: { community: number; post: number; comment: number }
}

/** The key of one person's vote on one post or comment. */
export function voteKey(targetId: number, userId: number): string {
  return `${targetId}:${userId}`
}

/** Who the mock thinks is logged in; null is logged out. Starts as demo_user, a plain USER. */
export const session: { userId: number | null } = { userId: 1 }

export let db: MockDb = seed()

/** Puts the seed data back and logs demo_user in again. */
export function resetMockDb(): void {
  db = seed()
  session.userId = 1
}

/** Mock only: act as another seeded user ("admin" for the ADMIN screens), or null to log out. */
export function mockSignInAs(username: string | null): void {
  if (username === null) {
    session.userId = null
    return
  }
  const user = db.users.find(u => u.username === username)
  if (!user) throw new Error(`No mock user ${username}`)
  session.userId = user.id
}

function seed(): MockDb {
  const now = Date.now()
  const ago = (hours: number) => new Date(now - hours * 3_600_000).toISOString()

  const people: Array<[username: string, first: string, last: string]> = [
    ['demo_user', 'Demo', 'User'],
    ['admin', 'Site', 'Admin'],
    ['colleague_one', 'Ana', 'One'],
    ['colleague_two', 'Bram', 'Two'],
    ['colleague_three', 'Ioana', 'Three'],
    ['colleague_four', 'Oleh', 'Four'],
    ['colleague_five', 'Nikola', 'Five'],
    ['colleague_six', 'Sanne', 'Six'],
    ['colleague_seven', 'Radu', 'Seven'],
    ['colleague_eight', 'Iryna', 'Eight'],
  ]
  const users: UserRow[] = people.map(([username, firstName, lastName], i) => ({
    id: i + 1,
    username,
    email: `${username}@example.com`,
    password: 'password',
    firstName,
    lastName,
    role: username === 'admin' ? 'ADMIN' : 'USER',
    avatarUrl: null,
    isBanned: false,
    banReason: null,
    isDeleted: false,
    createdAt: ago(24 * (90 - i)),
  }))
  const byName = (username: string) => users.find(u => u.username === username)!

  const communitySeed: Array<[name: string, description: string, active?: false]> = [
    ['frontend', 'Components, tooling and the browser.'],
    ['backend', 'Services, databases and APIs.'],
    ['devops', 'Pipelines, cloud and on-call.'],
    ['qa', 'Test plans, automation and bugs.'],
    ['random', 'Anything that fits nowhere else.'],
    ['food', 'Lunch spots, recipes and coffee.'],
    ['sports', 'Matches, runs and office leagues.'],
    ['retro', 'Old hardware and older games. Shut down by an admin.', false],
  ]
  const communities: CommunityRow[] = communitySeed.map(([name, description, active], i) => ({
    id: i + 1,
    name,
    description,
    creatorId: byName('admin').id,
    isActive: active !== false,
    createdAt: ago(24 * (60 - i)),
  }))
  const community = (name: string) => communities.find(c => c.name === name)!

  // demo_user is in frontend, backend and random, so Join and Leave both show up somewhere
  const members: MemberRow[] = []
  users.forEach((user, u) => {
    communities.forEach((c, k) => {
      const joined = user.username === 'demo_user' ? ['frontend', 'backend', 'random'].includes(c.name) : (u + k) % 3 !== 0
      if (joined && c.isActive) members.push({ communityId: c.id, userId: user.id, joinedAt: ago(24 * (50 - k)) })
    })
  })

  const postSeed: Array<[community: string, author: string, hours: number, score: number, title: string, content: string]> = [
    ['frontend', 'colleague_one', 2, 42, 'Which test runner for a new React project?', 'Starting a fresh Vite + React app. Vitest, Jest or something else, and why?'],
    ['frontend', 'colleague_eight', 9, 36, 'CSS modules, Tailwind or something else?', 'We keep going back and forth. What did your team pick and would you pick it again?'],
    ['frontend', 'demo_user', 26, 21, 'How do you test accessibility?', 'Axe in CI catches some of it. What do you do for the rest: screen readers, manual passes?'],
    ['frontend', 'colleague_six', 6, 29, 'Share your favourite VS Code extensions', 'Mine: Error Lens, GitLens and the Tailwind one. Yours?'],
    ['backend', 'colleague_two', 5, 27, 'How do you version an internal API?', 'URL prefix, header or just never break it? Looking for what worked in practice.'],
    ['backend', 'colleague_five', 30, 18, 'Postgres or Mongo for the new service?', 'Mostly relational data with a few JSON blobs. Leaning Postgres with jsonb.'],
    ['devops', 'colleague_three', 28, 31, 'Pipeline caching tips that saved time', 'Caching the Maven repository cut our CI from 9 to 4 minutes. What else works?'],
    ['devops', 'colleague_four', 3, 12, 'Who else is on call this weekend?', 'Let us make a thread for handovers.'],
    ['qa', 'colleague_four', 27, 19, 'Flaky e2e tests: delete or fix?', 'We have 6 tests that fail one run in ten. Quarantine them or fix them now?'],
    ['qa', 'colleague_seven', 50, 24, 'Playwright or Cypress in 2026?', 'Starting e2e from zero on a new project.'],
    ['random', 'colleague_five', 75, 55, 'The first computer you ever used?', 'Mine had a turbo button that did nothing.'],
    ['random', 'colleague_three', 5, 47, 'Show us your desk setup', 'Photos welcome.'],
    ['food', 'colleague_six', 4, 23, 'Best lunch near the office?', 'New here. Where do people go?'],
    ['food', 'colleague_one', 52, 14, 'Coffee beans worth bringing to the office', 'Light roast people, speak up.'],
    ['sports', 'colleague_seven', 6, 16, 'Five-a-side match on Friday?', 'Need two more players.'],
    ['sports', 'colleague_three', 25, 11, 'Running club: Thursday after work', '5 km, easy pace, everyone welcome.'],
    // a week and a half old, so the WEEK top list leaves it out and the ALL one keeps it
    ['backend', 'colleague_two', 250, 64, 'What we learned moving to Java 25', 'Virtual threads, pattern matching in switch, and one upgrade surprise.'],
  ]
  const posts: PostRow[] = postSeed.map(([c, author, hours, score, title, content], i) => ({
    id: i + 1,
    title,
    content,
    authorId: byName(author).id,
    authorUsername: author,
    communityId: community(c).id,
    score,
    commentCount: 0,
    imageUrls: [],
    createdAt: ago(hours),
    updatedAt: ago(hours),
    removedAt: null,
    removedBy: null,
  }))
  // one removed post: lists leave it out, a direct read is a 404
  posts.push({
    ...posts[0]!, id: posts.length + 1, title: 'Removed by its author', content: 'Never shown.',
    removedAt: ago(1), removedBy: posts[0]!.authorId, score: 3,
  })

  const commentLines = [
    'Vitest. Same config as Vite, and the watch mode is fast.',
    'Does it play well with Testing Library?',
    'It does, we switched last sprint. Happy to share our setup.',
    'Jest still works fine for us, but we would not pick it again.',
    'Following, same question here.',
    'We tried both and settled on the first one.',
  ]
  const commenters = users.filter(u => u.role === 'USER')
  const comments: CommentRow[] = []
  posts.forEach((post, p) => {
    if (post.removedAt) return
    const count = p % 4 === 3 ? 0 : 2 + (p % 3)
    for (let k = 0; k < count; k++) {
      const author = commenters[(p + k + 2) % commenters.length]!
      const hoursAfter = (k + 1) * 0.4
      const createdAt = new Date(Date.parse(post.createdAt) + hoursAfter * 3_600_000).toISOString()
      comments.push({
        id: comments.length + 1,
        postId: post.id,
        authorId: author.id,
        authorUsername: author.username,
        content: commentLines[(p + k) % commentLines.length]!,
        score: (p * 7 + k * 3) % 12,
        createdAt,
        // one comment per few posts was edited later, so the "edited" marker shows somewhere
        updatedAt: k === 0 && p % 5 === 0 ? new Date(Date.parse(createdAt) + 600_000).toISOString() : createdAt,
        removedAt: null,
        removedBy: null,
      })
    }
    post.commentCount = count
  })

  // demo_user has already voted on a few things, so both arrows show a state somewhere
  const postVotes = new Map<string, VoteValue>([
    [voteKey(1, 1), 1],
    [voteKey(5, 1), 1],
    [voteKey(9, 1), -1],
  ])
  const commentVotes = new Map<string, VoteValue>([[voteKey(comments[0]!.id, 1), 1]])

  return {
    users,
    communities,
    members,
    posts,
    comments,
    postVotes,
    commentVotes,
    nextId: { community: communities.length + 1, post: posts.length + 1, comment: comments.length + 1 },
  }
}
