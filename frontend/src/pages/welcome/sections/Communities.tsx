import { Section, SectionHead } from '@/pages/welcome/ui/Section'
import type { Community, Post } from '@/features/posts/types'

export interface CommunitiesProps {
  title: string
  lead: string
  communities: readonly Community[]
  /** Sample posts; each row shows its community's first one as what is hot there. */
  posts: readonly Post[]
  /** Where a row leads: joining means registering first. */
  joinHref: string
}

/**
 * The communities as a directory, one ruled row each, like an index page: the name set large, what it is
 * for, and the post that is hot there now. No boxes. A row is a link to register and join.
 */
export function Communities({ title, lead, communities, posts, joinHref }: CommunitiesProps) {
  return (
    <Section id="communities" screens={1}>
      <SectionHead title={title} lead={lead} />
      <ul className="border-b border-line-strong">
        {communities.map(c => {
          const hot = posts.find(p => p.community === c.slug)
          return (
            <li key={c.slug} className="border-t border-line-strong">
              <a
                className={
                  'group relative -mx-4 grid grid-cols-[minmax(0,1fr)_40px] items-center gap-x-6 gap-y-2 rounded-[12px] p-4 text-ink no-underline ' +
                  'transition-[background-color,color] duration-220 ease-[ease] motion-reduce:transition-none hover:bg-p-accent hover:text-[#fff] ' +
                  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink lg:grid-cols-[minmax(0,4fr)_minmax(0,3fr)_minmax(0,5fr)_40px]'
                }
                href={joinHref} aria-label={`Register to join l/${c.slug}`}>
                <span className="col-start-1 font-display text-[clamp(26px,2.7vw,40px)] leading-none font-[number:var(--display-weight)] tracking-[-0.03em] transition-[translate] duration-280 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none group-hover:translate-x-2 lg:col-start-auto">
                  <span className="text-p-accent transition-colors duration-220 ease-[ease] group-hover:text-[rgb(255_255_255/0.6)]">l/</span>
                  {c.slug}
                </span>
                <span className="hidden text-[15px] leading-[1.45] text-muted transition-colors duration-220 ease-[ease] group-hover:text-[rgb(255_255_255/0.78)] lg:inline">{c.blurb}</span>
                {hot && (
                  <span className="col-start-1 grid grid-cols-[auto_minmax(0,1fr)] items-baseline gap-x-3.5 gap-y-0.5 lg:col-start-auto">
                    <span className="row-span-2 text-[13px] font-semibold text-p-accent tabular-nums transition-colors duration-220 ease-[ease] group-hover:text-[rgb(255_255_255/0.78)]">▲ {hot.votes}</span>
                    <span className="text-[16px] leading-[1.35] font-medium">{hot.title}</span>
                    <span className="text-[13px] text-muted transition-colors duration-220 ease-[ease] group-hover:text-[rgb(255_255_255/0.78)]">
                      {hot.office} · {hot.replies} replies
                    </span>
                  </span>
                )}
                <span
                  className={
                    'col-start-2 row-span-2 row-start-1 grid size-10 place-items-center rounded-full text-p-accent ' +
                    'lg:col-start-auto lg:row-span-1 lg:row-start-auto lg:-translate-x-2 lg:opacity-0 lg:transition-[opacity,translate] lg:duration-[220ms,280ms] lg:ease-[ease,cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none ' +
                    'group-hover:translate-x-0 group-hover:bg-[#fff] group-hover:opacity-100'
                  }
                  aria-hidden="true"
                >
                  <svg className="size-5 fill-none stroke-current stroke-2 [stroke-linecap:round] [stroke-linejoin:round]" viewBox="0 0 20 20">
                    <path d="M5 10h10M11 5.5 15.5 10 11 14.5" />
                  </svg>
                </span>
              </a>
            </li>
          )
        })}
      </ul>
    </Section>
  )
}
