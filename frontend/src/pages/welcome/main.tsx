import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@/shared/styles/index.css'
import '@/pages/welcome/ui/hero/hero-enter.css'
import { WelcomePage } from '@/pages/welcome/WelcomePage'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <WelcomePage />
  </StrictMode>,
)
