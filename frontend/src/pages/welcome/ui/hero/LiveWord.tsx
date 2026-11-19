import { cn } from '@/shared/lib/cn'
import { useEffect, useRef, useState } from 'react'
import { useLoop } from '@/pages/welcome/ui/hero/useLoop'

export interface LiveWordProps {
  /** The first word is the one at rest (and the one read aloud); the rest take turns. */
  words: readonly [string, string, ...string[]]
}

/**
 * One live word in a heading (the JoinAnywhere h2): it changes every 2 s for two rounds,
 * rests on the first word, and plays a round again when a mouse enters the headline. The slot is as
 * wide as its widest word, so no other word moves.
 */
export function LiveWord({ words }: LiveWordProps) {
  const ref = useRef<HTMLSpanElement>(null)
  const [at, setAt] = useState(0)
  const [out, setOut] = useState<number | null>(null)
  const atRef = useRef(0)

  const loop = useLoop(
    ref,
    async ({ wait }) => {
      for (let n = 1; n <= words.length; n++) {
        await wait(2000)
        const cur = atRef.current
        const next = (cur + 1) % words.length
        atRef.current = next
        setOut(cur)
        setAt(next)
        await wait(620)
        setOut(null)
      }
    },
    {
      cycles: 2,
      rest: 900,
      still: () => {
        atRef.current = 0
        setAt(0)
        setOut(null)
      },
    },
  )

  const { live, restart } = loop
  useEffect(() => {
    const h1 = ref.current?.closest('h1')
    if (!h1 || !live) return
    const onEnter = (e: PointerEvent) => {
      if (e.pointerType !== 'touch') restart()
    }
    h1.addEventListener('pointerenter', onEnter)
    return () => h1.removeEventListener('pointerenter', onEnter)
  }, [live, restart])

  return (
    <span ref={ref} className="ly-word relative -mb-[.1em] inline-grid overflow-clip align-bottom [overflow-clip-margin:.14em]">
      {words.map((word, k) => (
        <span key={word} aria-hidden="true" className={cn(
            'col-start-1 row-start-1 justify-self-start whitespace-nowrap pb-[.1em] transition-[translate,opacity] duration-600 ease-ui motion-reduce:transition-none',
            'bg-[linear-gradient(var(--accent),var(--accent))] bg-no-repeat [background-position:0_calc(100%-.05em)] [background-size:100%_max(3px,.05em)]',
            k === at ? undefined : k === out ? '-translate-y-[105%] opacity-0' : 'translate-y-[105%] opacity-0',
          )}>
          {word}
        </span>
      ))}
    </span>
  )
}
