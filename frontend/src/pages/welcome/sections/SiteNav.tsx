import { Nav, type NavItem } from '@/pages/welcome/ui/layout/Nav'
import { PhotoImg } from '@/pages/welcome/ui/Media'
import type { ActionItem } from '@/shared/ui/types'
import { offices, site } from '@/pages/welcome/content'

export interface SiteNavProps {
  items: readonly NavItem[]
  register: ActionItem
  login: ActionItem
}

export interface LevddMarkProps {
  className?: string
}

/** The Levi9 9 beside the name. */
export function LevddMark({ className = 'size-7 rounded-sm' }: LevddMarkProps) {
  return <img className={className} src={site.markSrc} alt="" width={28} height={28} />
}

export function SiteNav({ items, register, login }: SiteNavProps) {
  const withOffices: readonly NavItem[] = [
    {
      label: 'Offices',
      href: '#offices',
      menu: {
        cards: offices.map(o => ({
          label: o.label,
          href: '#offices',
          line: 'See the office',
          visual: <PhotoImg photo={o.photo} className="absolute inset-0 z-0 size-full max-w-none object-cover transition-transform duration-400 ease-ui group-hover/card:scale-[1.03] group-hover/card:duration-600 motion-reduce:group-hover/card:scale-100" />,
        })),
      },
    },
    ...items,
  ]
  return <Nav brand={{ name: site.name, href: '#top', mark: <LevddMark /> }} items={withOffices} primary={register} secondary={login} />
}
