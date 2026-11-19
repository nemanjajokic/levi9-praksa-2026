import { createContext, useContext } from 'react'

export interface PageContextValue {
  /** The motion layer: off means the hero stands and nothing moves by itself. */
  motion: boolean
}

export const PageContext = createContext<PageContextValue>({ motion: true })

/** The page's motion switch. Outside a `<PageShell>` motion is on. */
export function usePage(): PageContextValue {
  return useContext(PageContext)
}
