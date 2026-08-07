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
