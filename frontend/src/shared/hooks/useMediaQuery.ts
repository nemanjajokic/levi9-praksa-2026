import { useCallback, useSyncExternalStore } from 'react'

/** A media query as state. `serverValue` is what a server render (and hydration) assumes. */
export function useMediaQuery(query: string, serverValue = false): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    [query],
  )
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
    () => serverValue,
  )
}

/** The visitor asked for less motion: every loop holds its finished picture, the hero stands. */
export const useReducedMotion = (): boolean => useMediaQuery('(prefers-reduced-motion: reduce)', true)

/** Under 640px: nothing loops, as the design system's CSS holds it. */
export const usePhone = (): boolean => useMediaQuery('(max-width: 639px)')

/** The desktop nav (hover menus); under it the phone sheet. */
export const useDesktopNav = (): boolean => useMediaQuery('(min-width: 1024px)', true)
