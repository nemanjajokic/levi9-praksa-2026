import { describe, expect, it } from 'vitest'
import { cn } from './cn'

describe('cn', () => {
  it('keeps a theme font size next to a theme colour', () => {
    expect(cn('text-h2', 'text-ink')).toBe('text-h2 text-ink')
    expect(cn('text-label', 'text-p-muted')).toBe('text-label text-p-muted')
  })

  it('lets the later class win inside one group', () => {
    expect(cn('px-6', 'px-4')).toBe('px-4')
    expect(cn('px-gut', 'px-6')).toBe('px-6')
    expect(cn('h-btn', 'h-12')).toBe('h-12')
    expect(cn('rounded-btn', 'rounded-lg')).toBe('rounded-lg')
    expect(cn('text-body', 'text-h3')).toBe('text-h3')
    expect(cn('bg-surface', 'bg-card')).toBe('bg-card')
    expect(cn('max-w-container', 'max-w-measure')).toBe('max-w-measure')
  })

  it('keeps a font family next to a font weight', () => {
    expect(cn('font-text', 'font-medium')).toBe('font-text font-medium')
  })

  it('keeps a line height next to a later font size (Button: leading-none, then its size)', () => {
    expect(cn('leading-none', 'text-[17px]')).toBe('leading-none text-[17px]')
    expect(cn('leading-none', 'md:text-body')).toBe('leading-none md:text-body')
  })

  it('treats a prefixed class as separate from the plain one', () => {
    expect(cn('bg-bg', 'max-sm:bg-transparent')).toBe('bg-bg max-sm:bg-transparent')
    expect(cn('h-11', 'sheet:h-12')).toBe('h-11 sheet:h-12')
  })

  it('drops empty values like cx did', () => {
    expect(cn('a', false, null, undefined, '')).toBe('a')
  })
})
