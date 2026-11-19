import type { Reply } from '@/features/posts/types'
import { cn } from '@/shared/lib/cn'

export interface ReplyCardProps {
  reply: Reply
  className?: string
}

/** The initials avatar: the handle's first two letters on the brand tint. */
function initials(handle: string): string {
  return handle.replace(/[^a-z]/gi, '').slice(0, 2).toUpperCase()
}

/** One reply in a thread: avatar, who and where from, the text, votes. Presentational only; fixed palette. */
export function ReplyCard({ reply, className }: ReplyCardProps) {
  return (
    <article className={cn('grid grid-cols-[32px_minmax(0,1fr)] items-start gap-3 text-left font-text', reply.depth === 1 && 'ml-11', className)}>
      <span
        className="grid size-8 place-items-center rounded-full bg-[color-mix(in_oklab,var(--p-accent)_14%,var(--p-bg))] text-[12px] font-semibold tracking-[0.02em] text-p-accent"
        aria-hidden="true"
      >
        {initials(reply.author)}
      </span>
      <div className="grid min-w-0 gap-1">
        <p className="text-[13px] leading-[18px] text-p-muted">
          <strong className="font-medium text-p-ink">u/{reply.author}</strong> · {reply.office} · {reply.age}
        </p>
        <p className="text-[15px] leading-[22px] text-p-ink">{reply.body}</p>
        <p className="text-[12px] leading-4 font-medium text-p-accent">▲ {reply.votes}</p>
      </div>
    </article>
  )
}
