import { Section, SectionHead } from '@/pages/welcome/ui/Section'
import { PostCard } from '@/features/posts/PostCard'
import { ReplyCard } from '@/features/posts/ReplyCard'
import { cn } from '@/shared/lib/cn'
import { grid12 } from '@/pages/welcome/ui/layout/screen'
import type { Post, Reply } from '@/features/posts/types'

export interface JoinStep {
  id: string
  title: string
  body: string
}

export interface HowToJoinProps {
  title: string
  steps: readonly JoinStep[]
  /** The thread shown beside the steps: a post and its replies from other offices. */
  post: Post
  replies: readonly Reply[]
}

/** Three ruled steps with large numerals on the left; on the right, a render of a thread in the app's own cards. */
export function HowToJoin({ title, steps, post, replies }: HowToJoinProps) {
  return (
    <Section id="join" screens={1} ground="surface">
      <SectionHead title={title} />
      <div className={cn('items-center gap-y-8', grid12)}>
        <ol className="col-span-full min-w-0 border-b border-line-strong lg:col-span-5">
          {steps.map((s, i) => (
            <li key={s.id} className="grid grid-cols-[72px_minmax(0,1fr)] gap-4 border-t border-line-strong py-7">
              <span className="font-display text-[40px] leading-none tracking-[-0.03em] text-p-accent" aria-hidden="true">
                {String(i + 1).padStart(2, '0')}
              </span>
              <div>
                <h3 className="text-[20px] leading-[1.3] font-medium text-ink">{s.title}</h3>
                <p className="mt-1.5 text-[15px] leading-normal text-muted">{s.body}</p>
              </div>
            </li>
          ))}
        </ol>
        <div
          className="col-span-full grid min-w-0 gap-5 rounded-lg bg-[#fff] p-7 shadow-[0_1px_2px_rgb(20_24_51/0.05),0_18px_40px_-18px_rgb(43_63_160/0.28)] lg:col-span-6 lg:col-start-7"
          data-ui=""
        >
          <PostCard post={post} />
          <div className="grid gap-[18px] border-t border-line pt-5">
            {replies.map(r => (
              <ReplyCard key={r.id} reply={r} />
            ))}
          </div>
        </div>
      </div>
    </Section>
  )
}
