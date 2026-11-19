import { Closing, type FooterColumn } from '@/pages/welcome/ui/layout/Closing'
import type { ActionItem } from '@/shared/ui/types'
import { LevddMark } from '@/pages/welcome/sections/SiteNav'

export interface SiteFooterProps {
  name: string
  lede: string
  /** The h2 of the closing call and the line under it. */
  title: string
  line: string
  register: ActionItem
  columns: readonly [FooterColumn, FooterColumn]
  legal: string
}

/** The last screen on the brand-blue footer: one ask set large with its button, then the links. */
export function SiteFooter({ name, lede, title, line, register, columns, legal }: SiteFooterProps) {
  return (
    <Closing
      title={title}
      line={line}
      primary={register}
      brand={{ name, href: '#top', mark: <LevddMark className="size-7 rounded-sm bg-white p-0.5" /> }}
      lede={lede}
      columns={columns}
      legal={legal}
      socials={<img src="/brand/levi9-logo-light.svg" alt="Levi9" width={90} height={45} style={{ display: 'block', height: 40, width: 'auto' }} />}
    />
  )
}
