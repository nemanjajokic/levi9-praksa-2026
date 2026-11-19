import { useMemo, type ReactNode } from 'react'
import type { Radius } from '@/pages/welcome/ui/types'
import { PageContext, type PageContextValue } from '@/pages/welcome/ui/layout/page-context'
import { useIsoLayoutEffect } from '@/shared/hooks/useIsoLayoutEffect'

export interface PageShellProps {
  /** The radius scale (`<html data-radius>`, theme.css). */
  radius?: Radius
  /** `false` switches the motion layer off (`<html data-motion="off">`): the hero stands and nothing moves by itself. */
  motion?: boolean
  lang?: string
  title: string
  description: string
  children: ReactNode
}

type Attrs = Record<string, string | null>

/** Sets attributes on an element for as long as the caller is mounted, then puts the old ones back. */
function useElementAttributes(get: () => Element, attrs: Attrs): void {
  const key = JSON.stringify(attrs)
  useIsoLayoutEffect(() => {
    const el = get()
    const before: Attrs = {}
    for (const [name, value] of Object.entries(attrs)) {
      before[name] = el.getAttribute(name)
      if (value === null) el.removeAttribute(name)
      else el.setAttribute(name, value)
    }
    return () => {
      for (const [name, value] of Object.entries(before)) {
        if (value === null) el.removeAttribute(name)
        else el.setAttribute(name, value)
      }
    }
    // attrs is compared by value through key
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
}

/** A `<meta name>` in the head for as long as the page is mounted. */
function useMeta(name: string, content: string | null): void {
  useIsoLayoutEffect(() => {
    if (content === null) return
    let meta = document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)
    const made = !meta
    const before = meta?.content ?? null
    if (!meta) {
      meta = document.createElement('meta')
      meta.name = name
      document.head.append(meta)
    }
    meta.content = content
    return () => {
      if (made) meta.remove()
      else if (before !== null) meta.content = before
    }
  }, [name, content])
}

/**
 * The root of the welcome page: sets `<html lang data-radius data-motion>`, the description meta and
 * the title, and arms the hero's entrance before the first paint. Children read the motion switch
 * through `usePage()`.
 */
export function PageShell({ radius = 'soft', motion = true, lang = 'en', title, description, children }: PageShellProps) {
  useElementAttributes(() => document.documentElement, {
    lang,
    'data-radius': radius,
    'data-motion': motion ? '' : 'off',
  })
  useMeta('description', description)
  useIsoLayoutEffect(() => {
    const before = document.title
    document.title = title
    return () => {
      document.title = before
    }
  }, [title])

  // The hero's entrance (hero-enter.css): the words wait under html.m-enter from the first paint, and a
  // 700ms safety mark lets CSS show them if the entrance never starts. A layout effect runs before
  // the browser paints, so it does what the static page's head line does.
  useIsoLayoutEffect(() => {
    const root = document.documentElement
    if (!motion) {
      root.classList.add('m-enter-done')
      return () => root.classList.remove('m-enter-done')
    }
    root.classList.add('m-enter')
    const mark = window.setTimeout(() => root.classList.add('m-enter-done'), 700)
    return () => {
      clearTimeout(mark)
      root.classList.remove('m-enter', 'm-enter-done')
    }
  }, [motion])

  const value = useMemo<PageContextValue>(() => ({ motion }), [motion])
  return <PageContext.Provider value={value}>{children}</PageContext.Provider>
}
