import type { ReactNode } from 'react'
import { Button } from '@/shared/ui/Button'
import { cn } from '@/shared/lib/cn'
import { AppLink } from '@/shared/ui/Link'
import type { ActionItem, LinkItem } from '@/shared/ui/types'
import { Brand, type BrandProps } from '@/shared/ui/Brand'
import { SectionHead } from '@/pages/welcome/ui/Section'
import { grid12, screenSection, screenWrap } from '@/pages/welcome/ui/layout/screen'

export interface FooterColumn {
  title: string
  links: readonly LinkItem[]
}

export interface ClosingProps {
  id?: string
  /** The closing ask: the h2, the line under it and the main action set full width. */
  title: string
  line: string
  primary: ActionItem
  brand: BrandProps
  lede: string
  /** Two columns of links beside the name. */
  columns: readonly [FooterColumn, FooterColumn]
  /** The legal line: the year and the owner ("© 2026 Levddit"). */
  legal: string
  socials?: ReactNode
}

/** A footer link: a 1px underline sweeps in from the left on hover or focus. */
const footerLink =
  'pb-0.5 bg-[linear-gradient(currentColor,currentColor),linear-gradient(var(--line-strong),var(--line-strong))] bg-no-repeat ' +
  '[background-position:right_0_bottom_0,0_100%] [background-size:0%_1px,0%_1px] transition-[background-size,color] duration-120 ease-ui ' +
  'hover:text-ink hover:duration-150 hover:[background-position:left_0_bottom_0,0_100%] hover:[background-size:100%_1px,100%_1px] ' +
  'focus-visible:[background-position:left_0_bottom_0,0_100%] focus-visible:[background-size:100%_1px,100%_1px] ' +
  'max-sm:inline-flex max-sm:min-h-11 max-sm:min-w-11 max-sm:items-center'

/**
 * The page's last screen on the inverse ground: row 1 the closing ask with its full-width button,
 * row 2 the footer (name and lede, two link columns, the legal line).
 */
export function Closing({ id = 'contact', title, line, primary, brand, lede, columns, legal, socials }: ClosingProps) {
  const headingId = `${id}-h`
  return (
    <footer id={id} className={cn(screenSection, 'ground-inverse')} aria-labelledby={headingId}>
      <div className={cn(screenWrap, 'grid grid-rows-[minmax(0,1fr)_auto] gap-y-16 max-sm:gap-y-12')}>
        <div className="flex min-h-0 flex-col gap-8">
          <SectionHead id={headingId} title={title} lead={line} flush className="[&_h2]:max-w-[20ch]" />
          <Button href={primary.href} onClick={primary.onClick} arrow size="xl">{primary.label}</Button>
        </div>
        <div className="grid gap-12 max-sm:gap-6">
          <div className={cn(grid12, 'gap-y-12 max-sm:gap-y-6')}>
            <div className="col-span-full min-w-0 lg:col-span-4 [&>*+*]:mt-4">
              <Brand {...brand} />
              <p className="max-w-[30ch] font-display text-[length:var(--t-lead)] leading-[1.25] font-[number:var(--display-weight)] tracking-[var(--display-tracking)]">{lede}</p>
            </div>
            {columns.map(col => (
              <div key={col.title} className="col-span-full min-w-0 lg:col-span-4">
                <h3 className="mb-4 font-text text-small leading-[1.4] font-medium tracking-normal text-muted">{col.title}</h3>
                <ul className="grid gap-3 text-small max-sm:gap-0">
                  {col.links.map(link => (
                    <li key={link.href + link.label}>
                      <AppLink className={footerLink} href={link.href}>{link.label}</AppLink>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="flex flex-wrap justify-between gap-x-8 gap-y-4 border-t border-line pt-6 text-label text-muted">
            <span>{legal}</span>
            <div className="flex gap-2">{socials}</div>
          </div>
        </div>
      </div>
    </footer>
  )
}
