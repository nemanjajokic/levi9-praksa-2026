/*
  The page rhythm: every section (and the closing footer) is one screen tall (--h screens, set by Section).
  From 640 to 1279px a section keeps at least that height and grows past it rather than overflow;
  on a phone it grows too, with --hm screens if set. --padt above, --padb (plus the phone action bar, --bar) below.
*/
export const screenSection =
  'relative h-[calc(var(--h,1)*100svh)] pt-padt pb-[calc(var(--padb)+var(--bar))] ' +
  'max-sm:grid max-sm:h-auto max-sm:min-h-[calc(var(--hm,var(--h,1))*100lvh)] max-sm:grid-cols-[minmax(0,1fr)] max-sm:grid-rows-[1fr] max-sm:auto-rows-auto ' +
  'sm:max-xl:flex sm:max-xl:h-auto sm:max-xl:min-h-[calc(var(--h,1)*100svh)] sm:max-xl:flex-col'

/** The section's one in-flow child: the centred column, filling the section's height. */
export const screenWrap = 'wrap relative z-[1] h-full max-sm:h-auto sm:max-xl:h-auto sm:max-xl:min-h-0 sm:max-xl:flex-[1_0_auto]'

/** The 12-column grid (the caller sets the row gap). Children span the full row until lg, then their own columns (col-span-*, col-start-*). */
export const grid12 = 'grid grid-cols-12 gap-x-gutter'
