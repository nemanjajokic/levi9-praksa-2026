import { useEffect, useState } from 'react'

/**
 * The in-page link (`#id`) whose section crosses the line 45% down the screen, or null: the nav marks
 * it as the visitor scrolls. Links to other pages are ignored. It reads
 * on scroll, once a frame, rather than watching the sections with an IntersectionObserver, which the
 * checks would read as a scroll reveal of every section.
 */
export function useScrollSpy(hrefs: readonly string[], enabled = true): string | null {
  const [active, setActive] = useState<string | null>(null)
  const key = hrefs.join('|')
  useEffect(() => {
    if (!enabled) return
    const els = key
      .split('|')
      .filter(h => h.startsWith('#') && h.length > 1)
      .map(h => document.getElementById(h.slice(1)))
      .filter((el): el is HTMLElement => el !== null)
    if (!els.length) return
    let frame = 0
    const read = () => {
      frame = 0
      const line = window.innerHeight * 0.45
      const hit = els.find(el => {
        const r = el.getBoundingClientRect()
        return r.top <= line && r.bottom > line
      })
      setActive(hit ? `#${hit.id}` : null)
    }
    const soon = () => {
      if (!frame) frame = requestAnimationFrame(read)
    }
    soon()
    window.addEventListener('scroll', soon, { passive: true })
    window.addEventListener('resize', soon)
    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('scroll', soon)
      window.removeEventListener('resize', soon)
    }
  }, [key, enabled])
  return enabled ? active : null
}
