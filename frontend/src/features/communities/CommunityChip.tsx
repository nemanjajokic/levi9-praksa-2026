import { cn } from '@/shared/lib/cn'
import type { Community } from '@/features/posts/types'

export interface CommunityChipProps {
  community: Pick<Community, 'slug'>
  className?: string
}

/** The "l/slug" label of a community, the same wherever a post or a list names it. Fixed palette, so it reads the same on any ground. */
export function CommunityChip({ community, className }: CommunityChipProps) {
  return (
    <span
      className={cn(
        'inline-flex w-fit items-baseline rounded-full bg-[color-mix(in_oklab,var(--p-accent)_10%,var(--p-bg))] px-2.5 py-0.5',
        'font-mono text-[13px] leading-5 font-medium tracking-normal text-p-accent',
        className,
      )}
    >
      l/{community.slug}
    </span>
  )
}
