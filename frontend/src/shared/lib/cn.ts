import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

/*
  Mirrors the @theme names in src/shared/styles/theme.css. A token added there must be added here too:
  tailwind-merge only knows Tailwind's own names, and guesses the rest are colours, so text-h2 next to
  text-ink would lose the font size. cn.test.ts checks each group.
*/
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      color: [
        'bg', 'surface', 'card', 'ink', 'muted', 'accent', 'accent-ink', 'action', 'action-ink',
        'line', 'line-strong', 'p-bg', 'p-ink', 'p-muted', 'p-accent', 'p-surface',
      ],
      font: ['display', 'text', 'mono'],
      text: ['label', 'small', 'body', 'lead', 'card', 'h3', 'h2', 'h1', 'stat'],
      radius: ['sm', 'md', 'lg', 'btn'],
      spacing: ['gut', 'gutter', 'nav', 'padt', 'padb', 'card-gap', 'card-pad', 'head-gap', 'btn', 'btn-sm', 'icon'],
      container: ['container', 'measure'],
      ease: ['ui'],
      animate: ['album-run'],
    },
  },
  override: {
    // Tailwind v4's text-* reads --tw-leading, so leading-* wins whatever the order: a size must not drop it.
    conflictingClassGroups: { 'font-size': [] },
  },
})

/** Joins class names, dropping empty ones; when two set the same thing, the later one wins. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}
