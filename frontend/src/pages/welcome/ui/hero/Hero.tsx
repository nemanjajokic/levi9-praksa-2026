import { useId, useRef, type ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { HeroContext } from '@/pages/welcome/ui/hero/hero-context'
import { phaseClass, useHeroEnter } from '@/pages/welcome/ui/hero/useHeroEnter'

export interface HeroProps {
  /** The one entrance on load; `false` only for a hero that must stand. */
  enter?: boolean
  children: ReactNode
}

/**
 * The hero section: exactly the first screen, labelled by its h1 (`<HeroTitle>` takes the id). Runs
 * the entrance and gives its phase to the title. The welcome page's split hero builds on it
 * (`sections/Hero.tsx`).
 */
export function Hero({ enter = true, children }: HeroProps) {
  const ref = useRef<HTMLElement>(null)
  const titleId = useId()
  const phase = useHeroEnter(ref, enter)
  return (
    <HeroContext.Provider value={{ phase, titleId }}>
      <section
        ref={ref}
        className={cn(
          'hero relative isolate h-svh min-h-0 pt-[calc(var(--nav)+var(--photo,0px)+var(--hgap))] pb-[calc(var(--hpadb)+var(--bar))]',
          'max-sm:grid max-sm:h-auto max-sm:min-h-svh max-sm:grid-cols-[minmax(0,1fr)] max-sm:grid-rows-[1fr] max-sm:auto-rows-auto',
          phaseClass[phase],
        )}
        data-enter={enter ? '' : 'off'}
        aria-labelledby={titleId}
      >
        {children}
      </section>
    </HeroContext.Provider>
  )
}
