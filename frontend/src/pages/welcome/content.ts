import { routes } from '@/shared/config/routes'
import type { NavItem } from '@/pages/welcome/ui/layout/Nav'
import type { Photo } from '@/pages/welcome/ui/Media'
import type { GlobePlace } from '@/features/offices/OfficeGlobe'
import type { JoinStep } from '@/pages/welcome/sections/HowToJoin'
import type { FooterColumn } from '@/pages/welcome/ui/layout/Closing'

export const site = {
  name: 'Levddit',
  title: 'Levddit: the forum for Levi9 people in all offices',
  description:
    'Levddit is the internal forum for Levi9 people in Serbia, the Netherlands, Romania and Ukraine. Register with your Levi9 email and join l/ communities like l/frontend.',
  markSrc: '/brand/levi9-mark.png',
  /** The same mark keyed to a transparent ground, drawn as a CSS mask in any colour. */
  markAlphaSrc: '/brand/levi9-mark-alpha.png',
} as const

export interface Office {
  id: string
  /** The tab and the map pin. */
  label: string
  title: string
  body: string
  photo: Photo
  lat: number
  lng: number
}

export const offices: readonly Office[] = [
  {
    id: 'serbia',
    label: 'Serbia',
    title: 'The Serbian office',
    body: 'Colleagues in Serbia post in the same threads as everyone else.',
    photo: { src: '/offices/serbia.webp', alt: 'The Levi9 office building in Serbia, with the 9 on its facade', width: 726, height: 484 },
    lat: 44.0,
    lng: 20.9,
  },
  {
    id: 'netherlands',
    label: 'Netherlands',
    title: 'The Dutch office',
    body: 'The Dutch office reads and answers the same communities as the rest.',
    photo: { src: '/offices/netherlands.webp', alt: 'The Levi9 office building in the Netherlands, on a tree-lined street', width: 1500, height: 1006 },
    lat: 52.2,
    lng: 5.3,
  },
  {
    id: 'romania',
    label: 'Romania',
    title: 'The Romanian office',
    body: 'Colleagues in Romania have the same l/ communities as everywhere else.',
    photo: { src: '/offices/romania.webp', alt: 'The Levi9 office in Romania, with a butterfly on its patterned facade', width: 768, height: 512 },
    lat: 47.16,
    lng: 27.59,
  },
  {
    id: 'ukraine',
    label: 'Ukraine',
    title: 'The Ukrainian office',
    body: 'Ukrainian colleagues sign in with the same Levi9 email as the others.',
    photo: { src: '/offices/ukraine.webp', alt: 'The Levi9 office building in Ukraine, red brick with large windows', width: 768, height: 512 },
    lat: 49.0,
    lng: 31.4,
  },
]

/** The offices on the hero globe; `country` is the Natural Earth name, whose land is drawn in Levi9 blue. */
export const globePlaces: readonly GlobePlace[] = [
  { id: 'netherlands', label: 'Netherlands', lat: 52.2, lng: 5.3, country: 'Netherlands', side: 'left' },
  { id: 'serbia', label: 'Serbia', lat: 44.0, lng: 20.9, country: 'Serbia', side: 'down' },
  { id: 'romania', label: 'Romania', lat: 45.9, lng: 24.9, country: 'Romania', side: 'right' },
  { id: 'ukraine', label: 'Ukraine', lat: 49.0, lng: 31.4, country: 'Ukraine', side: 'up' },
]

/** The globe looks at the middle of the four offices. */
export const globeFocus = { lat: 48.5, lng: 18 } as const

export const joinSteps: readonly JoinStep[] = [
  { id: 'register', title: 'Register with your Levi9 email', body: 'Create your account with the address you use at work.' },
  { id: 'join', title: 'Join the l/ communities you care about', body: 'Follow l/frontend, l/qa, l/food or any other community on the list.' },
  { id: 'post', title: 'Post, vote and reply in threads', body: 'Ask a question, vote up a good answer and reply to a colleague in another office.' },
]

export const navItems: readonly NavItem[] = [
  { label: 'Communities', href: '#communities' },
  { label: 'Join from anywhere', href: '#anywhere' },
  { label: 'How to join', href: '#join' },
]

export const footerColumns: readonly [FooterColumn, FooterColumn] = [
  {
    title: 'Levddit',
    links: [
      { label: 'Offices', href: '#offices' },
      { label: 'Communities', href: '#communities' },
      { label: 'How to join', href: '#join' },
    ],
  },
  {
    title: 'Account',
    links: [
      { label: 'Join now', href: routes.register },
      { label: 'Log in', href: routes.login },
    ],
  },
]
