import { useEffect, useState, type RefObject } from 'react'

export interface InViewOptions {
  rootMargin?: string
  threshold?: number
  /** Stop watching after the first time it is in view. */
  once?: boolean
}

/** Whether the element is on screen (an IntersectionObserver as state). True where there is none. */
export function useInView(ref: RefObject<Element | null>, { rootMargin = '0px', threshold = 0, once = false }: InViewOptions = {}): boolean {
  const [inView, setInView] = useState(() => typeof IntersectionObserver !== 'function')
  useEffect(() => {
    const el = ref.current
    if (!el || typeof IntersectionObserver !== 'function') return
    const io = new IntersectionObserver(
      ([entry]) => {
        if (!entry) return
        setInView(entry.isIntersecting)
        if (entry.isIntersecting && once) io.disconnect()
      },
      { rootMargin, threshold },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [ref, rootMargin, threshold, once])
  return inView
}
