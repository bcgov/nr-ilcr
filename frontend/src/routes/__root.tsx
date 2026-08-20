import { createRootRoute, ErrorComponent, Outlet } from '@tanstack/react-router'
import Layout from '@/components/Layout'
import NotFound from '@/components/NotFound'
import RouteGuard from '@/components/RouteGuard'

export const Route = createRootRoute({
  component: () => (
    <Layout>
      <RouteGuard>
        <Outlet />
      </RouteGuard>
    </Layout>
  ),
  notFoundComponent: () => <NotFound />,
  errorComponent: ({ error }) => <ErrorComponent error={error} />,
})
