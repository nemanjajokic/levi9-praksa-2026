import type { MouseEvent } from 'react'

export interface LinkItem {
  label: string
  href: string
}

/** A link a click can also handle in the app (a router push, a sign-in call). */
export interface ActionItem extends LinkItem {
  onClick?: (event: MouseEvent<HTMLAnchorElement>) => void
}
