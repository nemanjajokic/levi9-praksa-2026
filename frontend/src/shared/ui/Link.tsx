import { createContext, createElement, useContext, type AnchorHTMLAttributes, type ComponentType, type ReactNode } from 'react'

export type AppLinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }

/** A plain anchor: the default for every design system link. */
function PlainLink(props: AppLinkProps) {
  return <a {...props} />
}

const LinkContext = createContext<ComponentType<AppLinkProps>>(PlainLink)

export interface LinkProviderProps {
  /** The app's own link, so design system links route like the rest of the app. React Router:
      `({ href, ...rest }) => <Link to={href} {...rest} />`; Next.js: its Link takes href as is. */
  component: ComponentType<AppLinkProps>
  children: ReactNode
}

/** Renders every design system link (nav, buttons, footer) through the app's router link. */
export function LinkProvider({ component, children }: LinkProviderProps) {
  return <LinkContext.Provider value={component}>{children}</LinkContext.Provider>
}

/** The link component design system parts render: the app's router link inside a LinkProvider, else an anchor.
    In-page anchors (`#id`) always stay plain anchors. */
export function AppLink(props: AppLinkProps) {
  const link = useContext(LinkContext)
  if (props.href.startsWith('#')) return <a {...props} />
  return createElement(link, props)
}
