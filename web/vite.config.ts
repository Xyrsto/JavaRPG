import { defineConfig } from 'vitest/config';

// The page is always same-origin: in development Vite proxies the game API to the game server.
const target = process.env.VITE_PROXY_TARGET ?? 'http://127.0.0.1:8080';

export default defineConfig({
  // ES2022 covers the current and previous major versions of Chrome, Edge, Firefox and Safari.
  build: { target: 'es2022', sourcemap: false, outDir: 'dist', emptyOutDir: true },
  server: {
    proxy: {
      '/api': target,
      '/health': target,
    },
  },
  test: {
    include: ['test/**/*.test.ts'],
    environment: 'node',
  },
});
