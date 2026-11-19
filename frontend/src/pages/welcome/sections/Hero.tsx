import { Hero as HeroShell } from '@/pages/welcome/ui/hero/Hero'
import { HeroTitle } from '@/pages/welcome/ui/hero/HeroTitle'
import { ButtonRow } from '@/shared/ui/Button'
import type { ActionItem } from '@/shared/ui/types'
import { lazy, Suspense } from 'react'
import type { GlobePlace } from '@/features/offices/OfficeGlobe'
import { PostCard } from '@/features/posts/PostCard'
import { cn } from '@/shared/lib/cn'
import { grid12 } from '@/pages/welcome/ui/layout/screen'
import type { Post } from '@/features/posts/types'

export interface HeroProps {
  kicker: string
  title: string
  lead: string
  /** The offices labelled on the globe. */
  places: readonly GlobePlace[]
  focus: { lat: number; lng: number }
  globeLabel: string
  /** The sample post rendered over the globe panel's corner. */
  post: Post
  register: ActionItem
  login: ActionItem
}

/** three.js and the globe load after the words, in their own chunk. */
const OfficeGlobe = lazy(() => import('@/features/offices/OfficeGlobe').then(m => ({ default: m.OfficeGlobe })))

/** The chosen split hero: the words on five columns, the 3D office globe on six. */
export function Hero({ kicker, title, lead, places, focus, globeLabel, post, register, login }: HeroProps) {
  return (
    <HeroShell>
      <div className="wrap relative z-[1] flex h-full flex-col gap-y-12 max-sm:h-auto">
        <div className={cn('hero-text h-full min-h-0 grid-rows-[minmax(0,1fr)] content-stretch items-stretch gap-y-card-gap', grid12)}>
          <div className="col-span-full min-w-0 self-start lg:col-span-5">
            <p className="flex items-center gap-2 text-small text-muted">
              <span className="size-2 flex-none rounded-full bg-p-accent" aria-hidden="true" />
              {kicker}
            </p>
            <HeroTitle className="mt-4">{title}</HeroTitle>
            <p className="mt-6 max-w-[42ch] text-[clamp(17px,.286vw+15.89px,20px)] leading-normal text-ink">{lead}</p>
            <ButtonRow primary={register} secondary={login} arrow size="hero" className="mt-8"
              primaryClassName="shadow-[0_14px_30px_-12px_rgb(43_63_160/0.7)] hover:shadow-[0_20px_38px_-14px_rgb(43_63_160/0.8)]" />
          </div>
          <div className="hero__media relative col-span-full h-full min-h-0 min-w-0 max-lg:mt-8 max-lg:h-auto max-lg:min-h-[240px] lg:col-span-6 lg:col-start-7">
            <Suspense fallback={<div className="og" role="img" aria-label={globeLabel} />}>
              <OfficeGlobe places={places} focus={focus} label={globeLabel} />
            </Suspense>
            <div className="absolute bottom-6 left-6 z-[2] grid w-[min(300px,calc(100%-48px))] gap-1 rounded-md border border-line bg-bg p-4 text-ink max-md:hidden">
              <PostCard post={post} />
            </div>
          </div>
        </div>
      </div>
    </HeroShell>
  )
}
