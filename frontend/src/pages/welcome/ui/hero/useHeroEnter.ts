import { useEffect, useState, type RefObject } from 'react'
import { useReducedMotion } from '@/shared/hooks/useMediaQuery'
import { usePage } from '@/pages/welcome/ui/layout/page-context'
import { fontsReady } from '@/shared/lib/env'
import { useIsoLayoutEffect } from '@/shared/hooks/useIsoLayoutEffect'

/**
 * waiting: the words are held by hero-enter.css (html.m-enter) until the fonts are in.
 * entering: the h1 rises line by line out of word masks, then what follows fades up.
 * entered: done; the h1 is plain text again.
 * still: it stands as written (reduced motion, no head line, scrolled past).
 */
export type EnterPhase = 'waiting' | 'entering' | 'entered' | 'still'

/** The class hero-enter.css reads for each phase. */
export const phaseClass: Record<EnterPhase, string> = {
  waiting: '',
  entering: 'is-entering',
  entered: 'is-entered',
  still: 'is-still is-entered',
}

const EASE = 'cubic-bezier(.22, 1, .36, 1)'
/** The hero's picture: it settles in place (hero-enter.css) and never rises with the words. */
const MEDIA = '[data-enter-media], .hero__media'
const TEXT = 'h1 ~ *, :has(> h1) ~ *, :has(~ h1)'
const LEAF = 'p, dl, ul, ol, a, button, img, picture, video, figure, blockquote, h2, h3'

/** What rises after the h1, as whole blocks in reading order:
    a box of layout is opened, a button row ([data-enter-row], ButtonRow) gives each button its own step,
    a picture never moves here. */
function enterSteps(hero: HTMLElement, h1: HTMLElement): HTMLElement[] {
  const all = [...hero.querySelectorAll<HTMLElement>(MEDIA)]
  const media = all.filter(m => !all.some(o => o !== m && o.contains(m)))
  const out: HTMLElement[] = []
  const take = (el: HTMLElement) => {
    if (el === h1 || el.contains(h1) || out.includes(el) || el.matches('img, picture, video, canvas')) return
    if (media.includes(el) || media.some(m => el.contains(m))) return
    if (el.matches('[data-enter-row]')) {
      ;[...el.children].forEach(c => take(c as HTMLElement))
      return
    }
    const own = [...el.childNodes].some(n => n.nodeType === Node.TEXT_NODE && n.textContent?.trim())
    if (own || !el.children.length || el.matches(LEAF)) out.push(el)
    else [...el.children].forEach(c => take(c as HTMLElement))
  }
  hero.querySelectorAll<HTMLElement>(TEXT).forEach(take)
  return out
    .filter(el => !el.closest('[aria-hidden="true"]') && !out.some(o => o !== el && o.contains(el)))
    .sort((a, b) => (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1))
}

/** The h1's word masks grouped into the lines the browser set them on. */
function maskLines(h1: HTMLElement): HTMLElement[][] {
  const lines: HTMLElement[][] = []
  let top: number | null = null
  h1.querySelectorAll<HTMLElement>('.m-w').forEach(w => {
    const t = w.getBoundingClientRect().top
    if (top === null || Math.abs(t - top) > 3) {
      lines.push([])
      top = t
    }
    lines.at(-1)?.push(w.firstElementChild as HTMLElement)
  })
  return lines
}

/**
 * The hero's one entrance on load: about 1.1 s, transform and opacity only. Returns
 * the phase; the hero puts `phaseClass[phase]` on its section and the title renders word masks
 * while it is `entering`. Needs `<PageShell>` (or the static head line) to set html.m-enter.
 */
export function useHeroEnter(ref: RefObject<HTMLElement | null>, enabled: boolean): EnterPhase {
  const { motion } = usePage()
  const reduced = useReducedMotion()
  // the hero stands as written: known while rendering, so it never waits a frame hidden
  const stands = !enabled || motion === false || reduced
  const [phase, setPhase] = useState<EnterPhase>('waiting')

  // after PageShell's layout effect has set html.m-enter: the fonts first (250ms at most), then enter
  useEffect(() => {
    if (stands) return
    const root = document.documentElement
    let cancelled = false
    let late = 0
    fontsReady(250).then(() => {
      if (cancelled) return
      const hero = ref.current
      // no head line, or the visitor is already below the hero (a reload half way down): it stands
      if (!hero || !root.classList.contains('m-enter') || hero.getBoundingClientRect().bottom < 0) setPhase('still')
      // the safety mark passed first: hero-enter.css fades the words in; the hero is done once that is through
      else if (root.classList.contains('m-enter-done')) late = window.setTimeout(() => setPhase('entered'), 1150)
      else setPhase('entering')
    })
    return () => {
      cancelled = true
      clearTimeout(late)
    }
  }, [ref, stands])

  // the masks are rendered in this commit: start every animation before the paint
  useIsoLayoutEffect(() => {
    if (phase !== 'entering') return
    const hero = ref.current
    const h1 = hero?.querySelector('h1')
    if (!hero || !h1) {
      setPhase('still')
      return
    }
    const masked = maskLines(h1)
    const lines = masked.length ? masked : [[h1]]
    const anims: Animation[] = []
    const lineStep = Math.min(80, 240 / Math.max(1, lines.length - 1))
    lines.forEach((words, k) => words.forEach(w => anims.push(w.animate(
      masked.length
        ? [{ transform: 'translateY(calc(100% + .2em))' }, { transform: 'none' }]
        : [{ transform: 'translateY(.35em)', opacity: 0 }, { transform: 'none', opacity: 1 }],
      { duration: 700, delay: 80 + k * lineStep, easing: EASE, fill: 'both' },
    ))))
    const after = 80 + (lines.length - 1) * lineStep + 220
    let k = 0
    enterSteps(hero, h1).forEach(el => {
      const above = !!(el.compareDocumentPosition(h1) & Node.DOCUMENT_POSITION_FOLLOWING)
      const timing: KeyframeAnimationOptions = { duration: 520, delay: above ? 40 : after + Math.min(k++, 6) * 60, easing: EASE, fill: 'both' }
      anims.push(
        el.animate([{ opacity: 0 }, { opacity: 1 }], timing),
        el.animate([{ transform: 'translateY(12px)' }, { transform: 'none' }], { ...timing, composite: 'add' }),
      )
    })
    let over = false
    const finish = () => {
      if (over) return
      over = true
      setPhase('entered')
    }
    // the pictures settle by CSS over 1.1 s; whatever stalls (a hidden tab), it stands after 2.4 s
    const settle = new Promise(resolve => setTimeout(resolve, 1150))
    Promise.all([settle, ...anims.map(a => a.finished)]).then(finish, finish)
    const safety = window.setTimeout(finish, 2400)
    return () => {
      over = true
      clearTimeout(safety)
      anims.forEach(a => a.cancel())
    }
  }, [phase, ref])

  return stands ? 'still' : phase
}
