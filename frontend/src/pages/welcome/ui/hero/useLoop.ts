import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'
import { useInView } from '@/shared/hooks/useInView'
import { usePhone, useReducedMotion } from '@/shared/hooks/useMediaQuery'

/** What a loop's script gets: a wait that ends early (and throws) when the loop is stopped. */
export interface LoopApi {
  wait: (ms: number) => Promise<void>
  signal: AbortSignal
}

export interface LoopOptions {
  /** Rounds before it holds its finished picture again; a hover or focus plays it again. */
  cycles?: number
  /** Pause between rounds, ms. */
  rest?: number
  /** Called whenever the loop stops: show the finished picture. */
  still: () => void
  /** `false` keeps it still whatever the page (a loop the plan does not deal). */
  enabled?: boolean
}

export interface LoopControl {
  /** Whether it may move at all here (not a phone, not reduced motion). */
  live: boolean
  paused: boolean
  setPaused: (paused: boolean) => void
  /** Play it again from the start (a pointer entering its card). */
  restart: () => void
}

class Stopped extends Error {}

/**
 * The design system's card loop (ui.loop) as a hook: the finished picture first, then `cycles` rounds of
 * `run`, then the finished picture again. It rests off screen, never moves on a phone
 * or under reduced motion, and stops for the pause button.
 * `run` changes React state through the api's waits; it is read on every round, so it may close over
 * the latest props.
 */
export function useLoop(ref: RefObject<Element | null>, run: (api: LoopApi) => Promise<void>, { cycles = 3, rest = 1200, still, enabled = true }: LoopOptions): LoopControl {
  const reduced = useReducedMotion()
  const phone = usePhone()
  const onScreen = useInView(ref, { rootMargin: '48px' })
  const [paused, setPaused] = useState(false)
  const [round, setRound] = useState(0)
  const live = enabled && !reduced && !phone

  const runRef = useRef(run)
  const stillRef = useRef(still)
  useEffect(() => {
    runRef.current = run
    stillRef.current = still
  })

  useEffect(() => {
    if (!live || paused || !onScreen) {
      stillRef.current()
      return
    }
    const abort = new AbortController()
    const { signal } = abort
    const wait = (ms: number) =>
      new Promise<void>((resolve, reject) => {
        if (signal.aborted) return reject(new Stopped())
        const t = window.setTimeout(resolve, ms)
        signal.addEventListener('abort', () => {
          clearTimeout(t)
          reject(new Stopped())
        }, { once: true })
      })
    ;(async () => {
      for (let n = 0; n < cycles; n++) {
        await runRef.current({ wait, signal })
        if (n < cycles - 1) await wait(rest)
      }
      stillRef.current()
    })().catch(error => {
      if (!(error instanceof Stopped)) console.warn('loop:', error)
    })
    return () => abort.abort()
  }, [live, paused, onScreen, cycles, rest, round])

  const restart = useCallback(() => setRound(r => r + 1), [])
  return { live, paused, setPaused, restart }
}
