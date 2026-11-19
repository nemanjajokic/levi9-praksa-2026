import { Section } from '@/pages/welcome/ui/Section'
import { LiveWord } from '@/pages/welcome/ui/hero/LiveWord'
import { ButtonRow } from '@/shared/ui/Button'
import type { ActionItem } from '@/shared/ui/types'

export interface JoinAnywhereProps {
  /** The fixed start of the statement: "Join us from". */
  lead: string
  /** The word at rest first ("anywhere."), then the places it turns through. */
  words: readonly [string, string, ...string[]]
  sub: string
  /** The offices, set small along the foot of the band. */
  places: readonly string[]
  register: ActionItem
}

/**
 * The brand band: one large statement whose last word slides through the offices out of a mask and
 * rests on "anywhere.", the Levi9 9 large and faint behind it, the offices and the one action ruled
 * along its foot. Under reduced motion the word at rest stands.
 */
export function JoinAnywhere({ lead, words, sub, places, register }: JoinAnywhereProps) {
  return (
    <Section
      id="anywhere"
      screens={1}
      ground="brand"
      className={
        // the Levi9 9 as a large cut-off mark behind the statement
        "overflow-hidden isolate after:pointer-events-none after:absolute after:-right-[8vw] after:-bottom-[18vh] after:-z-1 after:aspect-square after:w-[min(68vw,980px)] after:bg-[#fff] after:opacity-[0.09] after:content-[''] " +
        'after:[mask:url(/brand/levi9-mark-2048.png)_center/contain_no-repeat] max-md:after:-right-[40vw] max-md:after:-bottom-[6vh] max-md:after:w-[110vw]'
      }
    >
      <div className="grid min-h-full grid-rows-[1fr_auto_auto] content-end">
        <h2 className="self-end text-[clamp(44px,13vw,72px)] leading-[0.98] tracking-[-0.045em] text-[#fff] md:text-[clamp(56px,9.2vw,148px)] [&_.ly-word>span]:bg-none [&_.ly-word>span]:text-[#fff]">
          <span className="block">{lead}</span>
          <LiveWord words={words} />
        </h2>
        <p className="mt-7 max-w-[46ch] text-[clamp(17px,1.4vw,20px)] leading-normal text-[rgb(255_255_255/0.82)]">{sub}</p>
        <div className="mt-12 flex items-center justify-between gap-6 border-t border-[rgb(255_255_255/0.22)] pt-6 max-md:flex-col max-md:items-stretch">
          <ul className="flex flex-wrap gap-x-7 gap-y-2 text-[15px] text-[rgb(255_255_255/0.82)]" aria-label="Offices">
            {places.map(p => (
              <li key={p} className="inline-flex items-center gap-2.5 before:size-1.5 before:rounded-full before:bg-[#fff] before:content-['']">{p}</li>
            ))}
          </ul>
          <ButtonRow primary={register} arrow size="hero" primaryClassName="shadow-[0_14px_30px_-12px_rgb(5_10_40/0.5)]" />
        </div>
      </div>
    </Section>
  )
}
