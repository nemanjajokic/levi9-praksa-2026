import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'
import { useDesktopNav } from '@/shared/hooks/useMediaQuery'

export interface NavSheet {
  /** The phone sheet is open. */
  open: boolean
  setOpen: (open: boolean) => void
  /** Which items' link lists are expanded in the sheet. */
  expanded: ReadonlySet<string>
  toggleItem: (key: string) => void
  /** Desktop: a menu panel has started to show, so the next word swaps it at once. */
  menuOpen: boolean
  /** Desktop: the pointer entered an item; `hasMenu` decides whether a panel is about to show. */
  enterItem: (hasMenu: boolean) => void
  leaveBar: () => void
}

/**
 * The nav's state: the phone sheet with its accordion, closed by
 * Escape (focus back on the toggle), by a tap on any link in it and by reaching the desktop width;
 * the body locked while it is open; and the desktop menu intent (150ms).
 */
export function useNavSheet(navRef: RefObject<HTMLElement | null>, toggleRef: RefObject<HTMLButtonElement | null>): NavSheet {
  const [open, setOpenState] = useState(false)
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set())
  const [menuOpen, setMenuOpen] = useState(false)
  const desktop = useDesktopNav()
  const [wasDesktop, setWasDesktop] = useState(desktop)
  const intent = useRef(0)

  // reaching the desktop width closes the sheet (state adjusted while rendering, not in an effect)
  if (desktop !== wasDesktop) {
    setWasDesktop(desktop)
    if (desktop) {
      setOpenState(false)
      setExpanded(new Set())
    }
  }

  const setOpen = useCallback((next: boolean) => {
    setOpenState(next)
    if (!next) setExpanded(new Set())
  }, [])

  const toggleItem = useCallback((key: string) => {
    setExpanded(prev => {
      const next = new Set(prev)
      if (!next.delete(key)) next.add(key)
      return next
    })
  }, [])

  // the body does not scroll under the open sheet
  useEffect(() => {
    document.body.classList.toggle('max-lg:overflow-hidden', open)
    return () => document.body.classList.remove('max-lg:overflow-hidden')
  }, [open])

  // Escape closes the sheet (focus back on the toggle) or leaves the bar
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      if (open) {
        setOpen(false)
        toggleRef.current?.focus()
      } else if (navRef.current?.contains(document.activeElement) && document.activeElement instanceof HTMLElement) {
        document.activeElement.blur()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, setOpen, navRef, toggleRef])

  // a tap on any link in the open sheet closes it, so the page it leads to is never under the sheet
  useEffect(() => {
    const nav = navRef.current
    if (!nav || !open) return
    const onClick = (e: MouseEvent) => {
      if (e.target instanceof Element && e.target.closest('a[href]')) setOpen(false)
    }
    nav.addEventListener('click', onClick)
    return () => nav.removeEventListener('click', onClick)
  }, [open, setOpen, navRef])

  const enterItem = useCallback((hasMenu: boolean) => {
    clearTimeout(intent.current)
    if (hasMenu) intent.current = window.setTimeout(() => setMenuOpen(true), 150)
    else setMenuOpen(false)
  }, [])
  const leaveBar = useCallback(() => {
    clearTimeout(intent.current)
    setMenuOpen(false)
  }, [])
  useEffect(() => () => clearTimeout(intent.current), [])

  return { open, setOpen, expanded, toggleItem, menuOpen, enterItem, leaveBar }
}
