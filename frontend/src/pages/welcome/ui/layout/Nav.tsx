import { useRef, type ReactNode } from 'react'
import { Button } from '@/shared/ui/Button'
import { cn } from '@/shared/lib/cn'
import { AppLink } from '@/shared/ui/Link'
import type { ActionItem, LinkItem } from '@/shared/ui/types'
import { Brand, type BrandProps } from '@/shared/ui/Brand'
import { useNavSheet } from '@/pages/welcome/ui/layout/useNavSheet'
import { useScrollSpy } from '@/pages/welcome/ui/layout/useScrollSpy'
import { grid12 } from '@/pages/welcome/ui/layout/screen'

export interface NavMenuCard extends LinkItem {
  line: string
  visual: ReactNode
}

export interface NavMenu {
  cards: readonly NavMenuCard[]
}

export interface NavItem extends LinkItem {
  menu?: NavMenu
  current?: boolean
}

export interface NavProps {
  brand: BrandProps
  /** Four or five words at most; each names a section or page that exists. */
  items: readonly NavItem[]
  /** The main action, the one button. */
  primary: ActionItem
  /** A plain word before it, only for something real (Log in on an app with accounts). */
  secondary?: ActionItem
  /** Where the skip link goes (the main element's id) and its words in the page's language. */
  skipTo?: string
  skipLabel?: string
  /** Mark the in-page link whose section is on screen (`aria-current="location"`). On by default. */
  spy?: boolean
}

/** The 2px underline under a nav word: grows from the left on hover or focus, stays for the current page or section. */
const underline =
  "before:pointer-events-none before:absolute before:-bottom-0.5 before:h-0.5 before:rounded-[2px] before:bg-current before:content-[''] " +
  'before:origin-right before:scale-x-0 before:transition-transform before:duration-[var(--dur)] before:ease-ui ' +
  'hover:before:origin-left hover:before:scale-x-100 focus-visible:before:origin-left focus-visible:before:scale-x-100 ' +
  "[&[aria-current]]:after:absolute [&[aria-current]]:after:-bottom-0.5 [&[aria-current]]:after:h-0.5 [&[aria-current]]:after:rounded-[2px] [&[aria-current]]:after:bg-current [&[aria-current]]:after:content-['']"

const linkBase =
  'relative h-9 items-center gap-1 whitespace-nowrap text-[15px] font-medium text-ink transition-[background-color,color] duration-[var(--dur)] ease-ui'

function Chevron() {
  return (
    <svg className="size-3 opacity-60 transition-transform duration-[var(--dur)] ease-ui item-open:rotate-180 sheet:hidden" viewBox="0 0 12 12" aria-hidden="true">
      <path d="M3 4.5l3 3 3-3" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** Card columns: three cards take four columns each, four take three. */
function cardSpan(count: number): string {
  return count >= 4 ? 'lg:col-span-3' : 'lg:col-span-4'
}

function MenuPanel({ menu }: { menu: NavMenu }) {
  return (
    <div
      className={cn(
        'absolute inset-x-0 top-full border-y border-line bg-bg pt-12 pb-16 text-ink',
        'invisible -translate-y-1.5 opacity-0 [transition:opacity_120ms_var(--ease),translate_120ms_var(--ease),visibility_0s_linear_120ms] motion-reduce:translate-y-0',
        'item-open:visible item-open:translate-y-0 item-open:opacity-100 item-open:[transition:opacity_200ms_var(--ease)_150ms,translate_200ms_var(--ease)_150ms,visibility_0s_linear_150ms]',
        'menu-instant:[transition-delay:0s] menu-instant:[transition-duration:0s]',
        'sheet:visible sheet:static sheet:col-span-full sheet:hidden sheet:translate-y-0 sheet:border-0 sheet:p-0 sheet:pb-4 sheet:opacity-100 sheet-expanded:block',
      )}
    >
      <div className={cn('wrap', grid12, 'gap-y-card-gap sheet:w-full sheet:gap-y-4')}>
        {menu.cards.map(card => (
          <AppLink key={card.href + card.label} href={card.href} className={cn('group/card col-span-full grid min-w-0 gap-3 sheet:gap-1 sheet:py-2', cardSpan(menu.cards.length))}>
            <div
              className="relative isolate block min-h-40 flex-[1_0_auto] overflow-clip rounded-md bg-[var(--ground-bg)] after:pointer-events-none after:absolute after:inset-0 after:z-[4] after:rounded-[inherit] after:shadow-[inset_0_0_0_1px_color-mix(in_oklab,var(--ink)_7%,transparent),inset_0_1px_0_color-mix(in_oklab,var(--ink)_4%,transparent)] after:content-[''] sheet:hidden"
            >
              {card.visual}
            </div>
            <span className="font-text text-[length:var(--t-card)] leading-[1.3] font-medium tracking-[-0.01em]">{card.label}</span>
            <span className="text-small leading-normal text-muted">{card.line}</span>
          </AppLink>
        ))}
      </div>
    </div>
  )
}

/**
 * The fixed nav: the name and mark, the words with hover panels on desktop, the main action, and the
 * phone sheet with its accordion under 1024px. Every state is React state, drawn by the nav variants in
 * theme.css: `.is-open` (sheet:), `.is-expanded` (sheet-expanded:), `.m-menu-open` (menu-instant:), and
 * a hovered or focused `.nav-item` (item-open:).
 */
export function Nav({ brand, items, primary, secondary, skipTo = '#top', skipLabel = 'Skip to content', spy = true }: NavProps) {
  const navRef = useRef<HTMLElement>(null)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const sheet = useNavSheet(navRef, toggleRef)
  const onScreen = useScrollSpy(items.map(item => item.href), spy)

  return (
    <header
      ref={navRef}
      className={cn('fixed inset-x-0 top-0 z-50 h-nav border-b border-line bg-bg text-ink', sheet.open && 'is-open', sheet.menuOpen && 'm-menu-open')}
      data-m-nav="1"
      onPointerLeave={sheet.leaveBar}
    >
      <div className="wrap flex h-full items-center gap-2 sm:gap-3 lg:gap-8 xl:gap-16">
        <a
          className={cn(
            'absolute top-1/2 left-[max(var(--gut),(100%-var(--container))/2)] z-[2] size-px overflow-hidden whitespace-nowrap [clip-path:inset(50%)]',
            'focus:inline-flex focus:h-btn focus:w-auto focus:-translate-y-1/2 focus:items-center focus:rounded-btn focus:border focus:border-line-strong focus:bg-bg',
            'focus:px-6 focus:font-text focus:text-body focus:leading-none focus:font-medium focus:text-ink focus:[clip-path:none]',
          )}
          href={skipTo}
        >
          {skipLabel}
        </a>
        <Brand {...brand} />
        <nav
          className={cn(
            'flex min-w-0 flex-1 gap-1 self-stretch max-lg:hidden lg:max-xl:gap-0',
            'sheet:fixed sheet:inset-x-0 sheet:top-nav sheet:bottom-0 sheet:flex sheet:flex-col sheet:gap-0 sheet:overflow-y-auto sheet:bg-bg sheet:px-gut sheet:pt-4 sheet:pb-[calc(var(--btn-h)*2+56px)]',
            'sheet:transition-[opacity,translate] sheet:duration-200 sheet:ease-ui sheet:starting:-translate-y-2 sheet:starting:opacity-0',
          )}
          aria-label="Main"
        >
          {items.map(item => {
            const key = item.href + item.label
            const expanded = sheet.expanded.has(key)
            return (
              <div
                key={key}
                className={cn('nav-item flex h-full items-center sheet:grid sheet:h-auto sheet:grid-cols-[1fr_auto] sheet:border-b sheet:border-line', expanded && 'is-expanded')}
                onPointerEnter={() => sheet.enterItem(!!item.menu)}
              >
                <AppLink
                  className={cn(
                    linkBase,
                    underline,
                    'inline-flex rounded-sm px-3 before:inset-x-3 [&[aria-current]]:after:inset-x-3 lg:max-xl:px-2 lg:max-xl:before:inset-x-2 lg:max-xl:[&[aria-current]]:after:inset-x-2',
                    'item-open:before:origin-left item-open:before:scale-x-100',
                    'sheet:h-14 sheet:bg-transparent sheet:p-0 sheet:text-[20px] sheet:before:hidden sheet:[&[aria-current]]:after:right-auto sheet:[&[aria-current]]:after:bottom-2.5 sheet:[&[aria-current]]:after:left-0 sheet:[&[aria-current]]:after:w-6',
                  )}
                  href={item.href}
                  aria-current={item.current ? 'page' : onScreen === item.href ? 'location' : undefined}
                >
                  {item.label}
                  {item.menu && <Chevron />}
                </AppLink>
                {item.menu && (
                  <button
                    type="button"
                    className="hidden sheet:inline-grid sheet:h-14 sheet:w-11 sheet:cursor-pointer sheet:place-items-center sheet:border-0 sheet:bg-transparent sheet:text-ink"
                    aria-label={`Show ${item.label} links`}
                    aria-expanded={expanded}
                    onClick={() => sheet.toggleItem(key)}
                  >
                    <svg className="max-lg:size-4 max-lg:transition-transform max-lg:duration-[var(--dur)] max-lg:ease-ui expanded:max-lg:rotate-180" viewBox="0 0 16 16" aria-hidden="true">
                      <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </button>
                )}
                {item.menu && <MenuPanel menu={item.menu} />}
              </div>
            )
          })}
        </nav>
        <div
          className={cn(
            'flex flex-none items-center gap-2.5 max-lg:ml-auto',
            'sheet:fixed sheet:inset-x-0 sheet:bottom-0 sheet:z-[51] sheet:grid sheet:border-t sheet:border-line sheet:bg-bg sheet:px-gut sheet:pt-4 sheet:pb-[calc(16px+env(safe-area-inset-bottom))]',
          )}
        >
          {secondary && (
            <AppLink
              className={cn(
                linkBase,
                underline,
                'hidden min-h-12 rounded-full border-[1.5px] border-[color-mix(in_oklab,var(--p-accent)_35%,transparent)] px-[22px] font-semibold text-p-accent before:inset-x-2 [&[aria-current]]:after:inset-x-2',
                'transition-[border-color,background-color] duration-160 ease-[ease] hover:border-p-accent hover:bg-[color-mix(in_oklab,var(--p-accent)_6%,transparent)]',
                'md:inline-flex md:sheet:order-2 md:sheet:h-btn md:sheet:justify-center md:sheet:text-body',
              )}
              href={secondary.href}
              onClick={secondary.onClick}
            >
              {secondary.label}
            </AppLink>
          )}
          <Button href={primary.href} onClick={primary.onClick} size="nav" className="sheet:h-12 sheet:w-full">{primary.label}</Button>
        </div>
        <button
          ref={toggleRef}
          type="button"
          className="hidden size-11 flex-none cursor-pointer place-items-center rounded-btn border border-line-strong bg-transparent text-ink max-lg:inline-grid sheet:ml-auto"
          aria-expanded={sheet.open}
          aria-label="Menu"
          onClick={() => sheet.setOpen(!sheet.open)}
        >
          <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
            <path d={sheet.open ? 'M4 4l10 10M14 4L4 14' : 'M2 5h14M2 13h14'} stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
          </svg>
        </button>
      </div>
    </header>
  )
}
