import { CommunityChip } from '@/features/communities/CommunityChip'
import type { Post } from '@/features/posts/types'
import { cn } from '@/shared/lib/cn'

export interface PostCardProps {
  post: Post
  /** Hide the community label, for a list that already sits inside one community. */
  hideCommunity?: boolean
  className?: string
}

/** One post in a feed: votes, community, title, who and where from, replies. Presentational only; fixed palette. */
export function PostCard({ post, hideCommunity = false, className }: PostCardProps) {
  return (
    <article className={cn('grid grid-cols-[32px_minmax(0,1fr)] items-start gap-3 text-left font-text text-p-ink', className)}>
      <div className="grid justify-items-center gap-0.5 pt-0.5 font-mono text-[13px] leading-4 font-medium text-p-accent" aria-label={`${post.votes} votes`}>
        <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
          <path d="M2.5 7.5 6 4l3.5 3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <span>{post.votes}</span>
      </div>
      <div className="grid min-w-0 justify-items-start gap-1.5">
        {!hideCommunity && <CommunityChip community={{ slug: post.community }} />}
        <p className="text-[16px] leading-[22px] font-medium tracking-normal text-p-ink">{post.title}</p>
        <p className="text-[13px] leading-[18px] text-p-muted">
          u/{post.author} · {post.office} · {post.age} · {post.replies} replies
        </p>
      </div>
    </article>
  )
}
