import { cn } from '@/shared/lib/cn'
import { useHero } from '@/pages/welcome/ui/hero/hero-context'

export interface HeroTitleProps {
  /** The headline, as plain text. */
  children: string
  className?: string
}

/**
 * The hero's h1. During the entrance (useHeroEnter) each word sits in a mask and the lines rise one
 * after another; the masked words are hidden from screen readers, so the h1 carries the text as its
 * label meanwhile. After the entrance it is plain text again.
 */
export function HeroTitle({ children, className }: HeroTitleProps) {
  const { phase, titleId } = useHero()
  const masks = phase === 'entering'
  return (
    <h1
      id={titleId}
      className={cn(
        'text-[clamp(40px,3.048vw+28.11px,72px)] leading-[1.02] tracking-[var(--display-tracking-xl,var(--display-tracking))]',
        'text-balance',
        className,
      )}
      aria-label={masks ? children : undefined}
    >
      {masks
        ? children.split(/(\s+)/).filter(Boolean).map((part, i) =>
            /^\s+$/.test(part) ? ' ' : (
              <span key={i} className="m-w" aria-hidden="true">
                <span className="m-w__in">{part}</span>
              </span>
            ),
          )
        : children}
    </h1>
  )
}
