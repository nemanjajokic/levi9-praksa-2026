/* The welcome page's layout vocabulary. Each value is a class or attribute theme.css already knows;
   widening a union means the CSS has to learn the new value first. */

/** `<html data-radius>`: the radius scale (theme.css). */
export type Radius = 'soft' | 'round'

/** A section's ground band: `.ground-surface`, `.ground-inverse` and `.ground-brand` flip the colour roles inside. */
export type Ground = 'base' | 'surface' | 'inverse' | 'brand'

/** How many screens tall a section is (`--h`). */
export type Screens = 1 | 2
