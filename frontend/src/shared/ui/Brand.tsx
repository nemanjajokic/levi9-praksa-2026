import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { AppLink } from '@/shared/ui/Link'

export interface BrandProps {
  name: string
  href: string
  /** The mark before the name, e.g. the logo image. */
  mark: ReactNode
  className?: string
}

/** The site's name and mark, as the nav and the footer show it. */
export function Brand({ name, href, mark, className }: BrandProps) {
  return (
    <AppLink
      className={cn(
        'inline-flex min-h-11 flex-none items-center gap-1.5 font-display text-[clamp(16px,4.6vw,20px)] leading-none font-medium tracking-[-0.02em] whitespace-nowrap',
        'sm:min-h-0 sm:gap-2 sm:text-[22px] sm:whitespace-normal',
        className,
      )}
      href={href}
    >
      {mark}
      {name}
    </AppLink>
  )
}
