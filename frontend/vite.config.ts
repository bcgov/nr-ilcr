import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { tanstackRouter } from '@tanstack/router-plugin/vite'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    tanstackRouter({
      target: 'react',
      autoCodeSplitting: true,
    }),
    react(),
  ],
  server: {
    port: Number.parseInt(process.env.PORT ?? '3000', 10),
    fs: {
      // Allow serving files from one level up to the project root
      allow: ['..'],
    },
    proxy: {
      // Proxy API requests to the backend
      '/api': {
        target: process.env.BACKEND_URL ?? 'http://localhost:3001',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    // https://vitejs.dev/config/shared-options.html#resolve-alias
    alias: [{ find: /^~(.+)$/, replacement: '$1' }],
    tsconfigPaths: true,
    extensions: ['.js', '.json', '.jsx', '.mjs', '.ts', '.tsx', '.vue'],
  },
  build: {
    // Build Target
    // https://vitejs.dev/config/build-options.html#build-target
    target: 'esnext',
    // Rollup Options
    // https://vitejs.dev/config/build-options.html#build-rollupoptions
    rollupOptions: {},
  },
  css: {
    preprocessorOptions: {
      scss: {
        // Silence deprecation warnings caused by Bootstrap SCSS
        // which is out of our control.
        silenceDeprecations: ['color-functions', 'global-builtin', 'import'],
      },
    },
  },
})
