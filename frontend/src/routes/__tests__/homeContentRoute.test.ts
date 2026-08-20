import { describe, expect, test, vi } from 'vitest'

// The real createFileRoute demands a generated route tree; capturing the options object is all this
// suite needs to pin that /home-content mounts the HomeContent surface.
vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => (options: unknown) => options,
}))
const { HomeContentStub } = vi.hoisted(() => ({ HomeContentStub: () => null }))
vi.mock('@/components/homeContent', () => ({ default: HomeContentStub }))

import { Route } from '@/routes/home-content'

describe('/home-content route', () => {
  test('mounts the HomeContent component', () => {
    expect((Route as unknown as { component: unknown }).component).toBe(HomeContentStub)
  })
})
