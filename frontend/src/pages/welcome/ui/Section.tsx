import type { CSSProperties, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { cssVars } from '@/shared/lib/style'
import type { Ground, Screens } from '@/pages/welcome/ui/types'
import { screenSection, screenWrap } from '@/pages/welcome/ui/layout/screen'

export interface SectionProps {
  id: string
  /** How many screens tall (1 or 2). */
  screens?: Screens
  /** The ground band: base (the page), surface (the tint) or brand (Levi9 blue). The colours flip with it (theme.css). */
  ground?: Ground
  className?: string
  wrapClassName?: string
  style?: CSSProperties
  children: ReactNode
}

const groundClass: Record<Ground, string | undefined> = {
  base: undefined,
  surface: 'ground-surface',
  inverse: 'ground-inverse',
  brand: 'ground-brand',
}

/** One screen-tall section of the page with its centred column. */
export function Section({ id, screens, ground = 'base', className, wrapClassName, style, children }: SectionProps) {
  return (
    <section id={id} className={cn(screenSection, groundClass[ground], className)} style={cssVars({ '--h': screens }, style)}>
      <div className={cn(screenWrap, 'flex flex-col', wrapClassName)}>{children}</div>
    </section>
  )
}

export interface SectionHeadProps {
  title: ReactNode
  lead?: ReactNode
  id?: string
  /** No gap under the head (when the parent spaces it). */
  flush?: boolean
  className?: string
}

/** A section's heading block: the h2 and an optional lead line. */
export function SectionHead({ title, lead, id, flush = false, className }: SectionHeadProps) {
  return (
    <div className={cn('grid max-w-[760px] gap-4', !flush && 'mb-head-gap', className)}>
      <h2 id={id}>{title}</h2>
      {lead && <p className="max-w-[60ch] text-[length:var(--t-lead)] leading-normal text-ink">{lead}</p>}
    </div>
  )
}
