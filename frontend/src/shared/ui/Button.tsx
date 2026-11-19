import type { ButtonHTMLAttributes, MouseEvent, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { AppLink } from '@/shared/ui/Link'
import type { ActionItem } from '@/shared/ui/types'

export type ButtonVariant = 'primary' | 'secondary'
/** md: forum screens. nav: the header's main action. hero: the big pill in a hero or a band. xl: the closing band's full-width button. */
export type ButtonSize = 'md' | 'nav' | 'hero' | 'xl'

interface ButtonLookProps {
  variant?: ButtonVariant
  size?: ButtonSize
  /** The drawn arrow after the words (the main action in a hero or a close). */
  arrow?: boolean
  className?: string
  children: ReactNode
}

export type LinkButtonProps = ButtonLookProps & {
  href: string
  onClick?: (event: MouseEvent<HTMLAnchorElement>) => void
}

export type ActionButtonProps = ButtonLookProps & Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className' | 'children'> & {
  href?: undefined
}

export type ButtonProps = LinkButtonProps | ActionButtonProps

const base =
  'group relative inline-flex items-center gap-2 rounded-btn border font-text leading-none tracking-normal whitespace-nowrap cursor-pointer ' +
  // the press: 1px down and 1.5% smaller (translate and scale are their own properties in Tailwind v4, so they are what transitions)
  'motion-safe:active:translate-y-px motion-safe:active:scale-[.985]'

const sizes: Record<ButtonSize, string> = {
  md: 'justify-center h-btn px-6 text-body font-medium transition-[background-color,color,border-color,box-shadow,translate,scale] duration-120 ease-ui hover:duration-150 motion-safe:hover:-translate-y-px',
  nav: 'justify-center h-11 px-[18px] text-[15px] font-semibold md:h-12 md:px-6 md:text-body transition-[background-color,color,border-color,box-shadow,translate,scale] duration-120 ease-ui hover:duration-150 motion-safe:hover:-translate-y-px',
  hero: 'justify-center h-14 px-[34px] text-[17px] font-semibold md:h-[62px] md:text-[18px] transition-[translate,scale,box-shadow,background-color] duration-180 ease-[ease] motion-safe:hover:-translate-y-[3px]',
  xl:
    'w-full h-12 px-12 justify-between text-[18px] font-medium transition-[background-color,color,border-color,box-shadow,translate,scale] duration-120 ease-ui hover:duration-150 motion-safe:hover:-translate-y-0.5 ' +
    'sm:h-[clamp(64px,3vw+40px,88px)] sm:font-display sm:font-[number:var(--display-weight)] sm:text-[clamp(22px,0.95vw+18px,32px)] sm:tracking-[var(--display-tracking)]',
}

const variants: Record<ButtonVariant, string> = {
  primary: 'border-transparent bg-action text-action-ink hover:bg-[color-mix(in_oklab,var(--action)_84%,var(--bg))]',
  secondary:
    'bg-bg text-ink border-line-strong hover:bg-[color-mix(in_oklab,var(--bg)_92%,var(--ink))] hover:border-[color-mix(in_oklab,var(--ink)_32%,transparent)]',
}

/** The hero's quiet action: accent outline on white. */
const heroSecondary = 'bg-[#fff] text-accent border-[color-mix(in_oklab,var(--accent)_35%,transparent)] hover:bg-[#fff] hover:border-accent'

/** The design system's drawn arrow: the stem grows in and the head nudges right when its button is hovered. */
export function Arrow({ size = 'md' }: { size?: ButtonSize }) {
  return (
    <svg
      className={cn(
        'block flex-none -mr-0.5 overflow-visible fill-none stroke-current [stroke-linecap:round] [stroke-linejoin:round]',
        size === 'xl' ? 'size-[0.62em] stroke-[1.4]' : size === 'hero' ? 'size-4 stroke-[1.6]' : 'size-3 stroke-[1.6]',
      )}
      viewBox="0 0 12 12"
      aria-hidden="true"
    >
      <path
        className="origin-[1px_50%] opacity-0 scale-x-[.2] transition-[opacity,scale] duration-120 ease-ui [transform-box:fill-box] group-hover:opacity-100 group-hover:scale-x-100 group-hover:duration-150 group-focus-visible:opacity-100 group-focus-visible:scale-x-100 motion-reduce:opacity-100 motion-reduce:scale-x-100"
        d="M1 6h8"
      />
      <path className="transition-[translate] duration-120 ease-ui group-hover:translate-x-[3px] group-hover:duration-150 group-focus-visible:translate-x-[3px] motion-reduce:translate-x-0" d="M4.5 2.5 8 6l-3.5 3.5" />
    </svg>
  )
}

function buttonClasses({ variant = 'primary', size = 'md', className }: Pick<ButtonLookProps, 'variant' | 'size' | 'className'>) {
  return cn(base, sizes[size], size === 'hero' && variant === 'secondary' ? heroSecondary : variants[variant], className)
}

/**
 * The button: a link when it has an href (through the app's router link), a `<button>` otherwise.
 * Words: a verb and an object ("Join now").
 */
export function Button(props: ButtonProps) {
  const { variant = 'primary', size = 'md', arrow = false, className, children } = props
  const classes = buttonClasses({ variant, size, className })
  const content = (
    <>
      {children}
      {arrow && <Arrow size={size} />}
    </>
  )
  if (props.href !== undefined) {
    return <AppLink className={classes} href={props.href} onClick={props.onClick}>{content}</AppLink>
  }
  const { variant: _v, size: _s, arrow: _a, className: _c, children: _ch, href: _h, type = 'button', ...rest } = props
  return <button type={type} className={classes} {...rest}>{content}</button>
}

export interface ButtonRowProps {
  /** The one main action. */
  primary: ActionItem
  /** At most one more, quieter. */
  secondary?: ActionItem
  /** Draw the arrow on the main action. */
  arrow?: boolean
  size?: Extract<ButtonSize, 'md' | 'hero'>
  /** Extra classes for the main action, such as its shadow on a band. */
  primaryClassName?: string
  className?: string
}

/** An action row: the main action and at most one quieter one. On a phone they stack and the quiet one becomes a link. */
export function ButtonRow({ primary, secondary, arrow = false, size = 'md', primaryClassName, className }: ButtonRowProps) {
  return (
    <div data-enter-row="" className={cn('flex flex-wrap items-center max-sm:w-full max-sm:flex-col max-sm:items-stretch', size === 'hero' ? 'gap-3.5' : 'gap-3', className)}>
      <Button href={primary.href} onClick={primary.onClick} arrow={arrow} size={size} className={primaryClassName}>{primary.label}</Button>
      {secondary && (
        <Button
          variant="secondary"
          size={size}
          href={secondary.href}
          onClick={secondary.onClick}
          className={cn(
            'max-sm:underline max-sm:underline-offset-4 max-sm:decoration-line-strong',
            size === 'md' && 'max-sm:bg-transparent max-sm:border-transparent',
          )}
        >
          {secondary.label}
        </Button>
      )}
    </div>
  )
}
