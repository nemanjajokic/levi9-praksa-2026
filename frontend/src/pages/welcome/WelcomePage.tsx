/**
 * Levddit welcome page (/welcome/): what the forum is, the four offices, the communities and how to join.
 * Brand from the Levi9 logo (#2b3fa0 on white). The forum itself lives at / (src/app).
 */
import { PageShell } from '@/pages/welcome/ui/layout/PageShell'
import { routes } from '@/shared/config/routes'
import { SAMPLE_COMMUNITIES, SAMPLE_POSTS, SAMPLE_THREAD, samplePost } from '@/features/posts/sample'
import { footerColumns, globeFocus, globePlaces, joinSteps, navItems, offices, site } from '@/pages/welcome/content'
import { Communities } from '@/pages/welcome/sections/Communities'
import { JoinAnywhere } from '@/pages/welcome/sections/JoinAnywhere'
import { Hero } from '@/pages/welcome/sections/Hero'
import { HowToJoin } from '@/pages/welcome/sections/HowToJoin'
import { Offices } from '@/pages/welcome/sections/Offices'
import { SiteFooter } from '@/pages/welcome/sections/SiteFooter'
import { SiteNav } from '@/pages/welcome/sections/SiteNav'

const register = { label: 'Join now', href: routes.register }
const login = { label: 'Log in', href: routes.login }

export function WelcomePage() {
  return (
    <PageShell radius="round" title={site.title} description={site.description}>
      <SiteNav items={navItems} register={register} login={login} />
      <main id="top">
        <Hero
          kicker="Levddit · join us from anywhere"
          title="A Levi9 community. All offices in one place."
          lead="Levddit is the forum for Levi9 people in Serbia, the Netherlands, Romania and Ukraine. Ask in l/frontend, compare notes in l/devops, argue about lunch in l/food."
          places={globePlaces}
          focus={globeFocus}
          globeLabel="A globe turned to Europe with the Levi9 offices labelled: the Netherlands, Serbia, Romania and Ukraine."
          post={samplePost('frontend')}
          register={register}
          login={login}
        />
        <Offices offices={offices} title="Four offices, one forum" lead="Serbia, the Netherlands, Romania and Ukraine." />
        <JoinAnywhere
          lead="Join us from"
          words={['anywhere.', 'Serbia.', 'the Netherlands.', 'Romania.', 'Ukraine.']}
          sub="An office in Serbia, a desk in the Netherlands or your kitchen table: the same communities, the same feed."
          places={offices.map(o => o.label)}
          register={register}
        />
        <Communities title="Seven communities to start with" lead="Topic by topic, open to every office." communities={SAMPLE_COMMUNITIES} posts={SAMPLE_POSTS} joinHref={routes.register} />
        <HowToJoin title="How to join in three steps" steps={joinSteps} post={samplePost('frontend')} replies={SAMPLE_THREAD} />
      </main>
      <SiteFooter name={site.name} lede="The internal forum for Levi9 people." title="Register with your Levi9 email" line="Already have an account? Log in and carry on where you left off." register={register} columns={footerColumns} legal="© 2026 Levddit" />
    </PageShell>
  )
}
