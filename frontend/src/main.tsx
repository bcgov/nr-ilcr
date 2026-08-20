import { StrictMode } from 'react'
import * as ReactDOM from 'react-dom/client'
import { RouterProvider, createRouter } from '@tanstack/react-router'
import AppProviders from '@/app/AppProviders'
import { configureAmplify } from '@/config/auth/amplify-initializer'
import { isMockAuth } from '@/env'
import '@/styles/index.scss'

// Import the generated route tree
import { routeTree } from './routeTree.gen'

// Real FAM/Cognito builds configure Amplify from the runtime config before the app renders; local
// mock runs skip it (no Cognito round-trip).
if (!isMockAuth()) {
  configureAmplify()
}

// Create a new router instance
const router = createRouter({ routeTree })

// Register the router instance for type safety
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
)
