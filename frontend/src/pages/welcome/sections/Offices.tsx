import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Section, SectionHead } from '@/pages/welcome/ui/Section'
import { PhotoImg } from '@/pages/welcome/ui/Media'
import { useInView } from '@/shared/hooks/useInView'
import { useMediaQuery } from '@/shared/hooks/useMediaQuery'
import type { Office } from '@/pages/welcome/content'

export interface OfficesProps {
  offices: readonly Office[]
  title: string
  lead: string
}

/** Milliseconds each office stays up before the album turns on its own. */
const TURN_MS = 3000

/** Where a slide sits relative to the current one, wrapped round: 0 centre, -1 left, 1 right, 2 behind. */
function offsetOf(i: number, index: number, count: number): number {
  let d = (i - index + count) % count
  if (d > count / 2) d -= count
  return d
}

/**
 * The offices as a photo album: the current office large in the centre, its neighbours peeking at the
 * sides, the two arrows together at the foot of the centre photo. It turns on its own every few seconds
 * while on screen; hover or focus holds it, an arrow or a name restarts the clock, reduced motion keeps it by hand.
 */
export function Offices({ offices, title, lead }: OfficesProps) {
  const count = offices.length
  const rootRef = useRef<HTMLDivElement>(null)
  const [index, setIndex] = useState(0)
  const [held, setHeld] = useState(false)
  const [turn, setTurn] = useState(0) // bumps on every manual move, restarting the clock
  const inView = useInView(rootRef)
  const reduced = useMediaQuery('(prefers-reduced-motion: reduce)')
  const running = count > 1 && inView && !held && !reduced

  useEffect(() => {
    if (!running) return
    const timer = setTimeout(() => setIndex(i => (i + 1) % count), TURN_MS)
    return () => clearTimeout(timer)
  }, [running, index, count, turn])

  const go = useCallback(
    (next: number) => {
      setIndex(((next % count) + count) % count)
      setTurn(t => t + 1)
    },
    [count],
  )

  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'ArrowRight') go(index + 1)
    else if (e.key === 'ArrowLeft') go(index - 1)
    else return
    e.preventDefault()
  }

  const current = offices[index]

  return (
    <Section id="offices" screens={1} className="overflow-x-clip">
      <SectionHead title={title} lead={lead} className="mx-auto text-center [&_p]:mx-auto" />
      <div
        ref={rootRef}
        className="relative"
        role="region"
        aria-roledescription="carousel"
        aria-label="Levi9 offices"
        onKeyDown={onKey}
        onPointerEnter={() => setHeld(true)}
        onPointerLeave={() => setHeld(false)}
        onFocus={() => setHeld(true)}
        onBlur={() => setHeld(false)}
      >
        <div className="relative h-[clamp(240px,42vh,340px)] md:h-[clamp(300px,54vh,500px)]">
          {offices.map((o, i) => {
            const d = offsetOf(i, index, count)
            const pos = d === 0 ? 'centre' : d === -1 ? 'left' : d === 1 ? 'right' : 'back'
            return (
              <div
                key={o.id}
                className={
                  'group/slide absolute top-0 left-1/2 h-full w-[84%] -translate-x-1/2 overflow-hidden rounded-lg bg-surface will-change-[translate,scale] md:w-[min(62%,800px)] ' +
                  '[transition:translate_620ms_cubic-bezier(0.22,1,0.36,1),scale_620ms_cubic-bezier(0.22,1,0.36,1),opacity_620ms_ease,filter_620ms_ease] motion-reduce:transition-none ' +
                  'data-[pos=centre]:z-[3] data-[pos=centre]:scale-100 data-[pos=centre]:opacity-100 ' +
                  'data-[pos=left]:z-[2] data-[pos=left]:-translate-x-[150%] data-[pos=left]:scale-[0.8] data-[pos=left]:cursor-pointer data-[pos=left]:opacity-55 data-[pos=left]:saturate-[0.7] data-[pos=left]:hover:opacity-80 md:data-[pos=left]:-translate-x-[122%] ' +
                  'data-[pos=right]:z-[2] data-[pos=right]:translate-x-[50%] data-[pos=right]:scale-[0.8] data-[pos=right]:cursor-pointer data-[pos=right]:opacity-55 data-[pos=right]:saturate-[0.7] data-[pos=right]:hover:opacity-80 md:data-[pos=right]:translate-x-[22%] ' +
                  'data-[pos=back]:pointer-events-none data-[pos=back]:z-[1] data-[pos=back]:scale-[0.6] data-[pos=back]:opacity-0'
                }
                data-pos={pos}
                aria-hidden={d !== 0}
                onClick={d === -1 || d === 1 ? () => go(i) : undefined}
              >
                <PhotoImg photo={o.photo} className="block size-full object-cover" />
                <span
                  className={
                    'absolute inset-x-0 bottom-0 bg-[linear-gradient(to_top,rgb(10_16_48/0.55),transparent)] px-7 pt-16 pb-6 font-display text-[clamp(24px,2.6vw,38px)] leading-none font-[number:var(--display-weight)] tracking-[-0.02em] text-[#fff] ' +
                    'opacity-0 [transition:opacity_400ms_ease] motion-reduce:transition-none group-data-[pos=centre]/slide:opacity-100 group-data-[pos=centre]/slide:[transition-delay:200ms]'
                  }
                >{o.label}</span>
              </div>
            )
          })}
          <div className="absolute top-[calc(100%+16px)] left-1/2 z-[4] flex -translate-x-1/2 gap-2.5 md:top-auto md:bottom-5">
            <button type="button" className={
                'grid size-11 cursor-pointer appearance-none place-items-center rounded-full border-0 bg-p-surface p-0 text-p-accent transition-[scale,background-color] duration-160 ease-[ease] ' +
                'hover:scale-[1.06] hover:bg-[#fff] active:scale-[0.96] focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-[#fff] ' +
                'md:size-12 md:bg-[rgb(255_255_255/0.92)] md:shadow-[0_6px_20px_-8px_rgb(10_16_48/0.45)]'
              } aria-label="Previous office" onClick={() => go(index - 1)}>
              <svg className="size-5 fill-none stroke-current stroke-2 [stroke-linecap:round] [stroke-linejoin:round]" viewBox="0 0 20 20" aria-hidden="true">
                <path d="M12.5 4.5 7 10l5.5 5.5" />
              </svg>
            </button>
            <button type="button" className={
                'grid size-11 cursor-pointer appearance-none place-items-center rounded-full border-0 bg-p-surface p-0 text-p-accent transition-[scale,background-color] duration-160 ease-[ease] ' +
                'hover:scale-[1.06] hover:bg-[#fff] active:scale-[0.96] focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-[#fff] ' +
                'md:size-12 md:bg-[rgb(255_255_255/0.92)] md:shadow-[0_6px_20px_-8px_rgb(10_16_48/0.45)]'
              } aria-label="Next office" onClick={() => go(index + 1)}>
              <svg className="size-5 fill-none stroke-current stroke-2 [stroke-linecap:round] [stroke-linejoin:round]" viewBox="0 0 20 20" aria-hidden="true">
                <path d="M7.5 4.5 13 10l-5.5 5.5" />
              </svg>
            </button>
          </div>
        </div>
        <div className="mt-[84px] flex flex-wrap justify-center gap-x-[18px] gap-y-1 md:mt-7 md:gap-x-7" role="group" aria-label="Pick an office">
          {offices.map((o, i) => (
            <button key={o.id} type="button" className={
                'group/tab min-h-11 cursor-pointer appearance-none border-0 bg-transparent px-0 pt-2.5 pb-0 font-text text-[14px] leading-[1.2] font-medium text-muted transition-colors duration-160 ease-[ease] md:text-[16px] ' +
                'hover:text-ink aria-pressed:text-ink focus-visible:rounded-[4px] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ink'
              }
              aria-pressed={i === index} onClick={() => go(i)}>
              {o.label}
              <span className="mt-2 block h-0.5 overflow-hidden rounded-[2px] bg-line group-aria-pressed/tab:bg-[color-mix(in_oklab,var(--p-accent)_18%,transparent)]" aria-hidden="true">
                {i === index && running && <span key={`${index}-${turn}`} className="block h-full origin-left animate-album-run bg-p-accent" />}
              </span>
            </button>
          ))}
        </div>
        <p className="sr-only" aria-live="polite">
          {current?.label}, office {index + 1} of {count}
        </p>
      </div>
    </Section>
  )
}
