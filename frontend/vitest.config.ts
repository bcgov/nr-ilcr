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
