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
    // The heaviest tests (Schedule 8's sample editor: full render + a dozen userEvent interactions)
    // run ~1s locally but ~5x slower on the shared CI runner — Schedule8.test.tsx takes 21s here and
    // 89s there — which puts them over Vitest's 5s default and fails them on wall-clock, not on any
    // assertion. Raised for the whole suite so the next-slowest test doesn't need its own override.
    testTimeout: 20000,
    setupFiles: 'src/test-setup.ts',
    // The RTL + userEvent + MSW component suites run ~5x slower under coverage instrumentation on CI,
    // so the default 5s timeout flakily trips on the async-heavy edit/save tests (which complete in
    // ~2s uninstrumented). 20s gives ample headroom without masking a genuine hang.
    testTimeout: 20000,
    hookTimeout: 20000,
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
