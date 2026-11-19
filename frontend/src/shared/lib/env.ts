/* Plain browser checks, safe to import on a server (every one answers false there). */

export const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

export function prefersReducedMotion(): boolean {
  return isBrowser && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Fonts first (a line set in the fallback face breaks elsewhere), but never wait longer than `ms`. */
export function fontsReady(ms: number): Promise<void> {
  const fonts = isBrowser ? document.fonts?.ready : undefined
  return Promise.race([
    fonts ? fonts.then(() => undefined) : Promise.resolve(),
    new Promise<void>(resolve => setTimeout(resolve, ms)),
  ])
}
