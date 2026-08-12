import { defineConfig } from 'vitest/config'

// https://vitejs.dev/config/
export default defineConfig({
  resolve: {
    tsconfigPaths: true,
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
    globals: true,
    environment: 'jsdom',
    setupFiles: 'src/test-setup.ts',
    // The heaviest RTL + userEvent + MSW component tests (Schedule 8's sample editor, the Schedule 7A
    // multi-row delete/add flows) do a full render plus a dozen async interactions. They run a few
    // seconds locally but ~5x slower on the shared CI runner under coverage instrumentation AND lose
    // more to parallel contention across a 500-test suite — so they trip Vitest's timeout on
    // wall-clock, not on any assertion. 30s gives headroom for that worst case without masking a hang.
    testTimeout: 30000,
    hookTimeout: 30000,
    // you might want to disable it, if you don't have tests that rely on CSS
    // since parsing CSS is slow
    css: false,
    coverage: {
      reporter: ['lcov', 'text-summary', 'text', 'json', 'html'],
      exclude: [
        '**/node_modules/**',
        '**/dist/**',
        '**/coverage/**',
        '**/*.config.*',
        'src/routeTree.gen.ts', // Auto-generated file
        'src/**/*.test.ts',
        'src/**/*.spec.ts',
        'src/**/*.test.tsx',
        'src/**/*.spec.tsx',
        'src/__tests__/**',
      ],
    },
  },
})
