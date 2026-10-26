/// <reference types="vitest/config" />
import { defineConfig, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const apiTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

/**
 * The browser only ever talks to this origin; the dev/preview server forwards API calls to the backend.
 * The Origin header is dropped because the call is same-origin from the browser's point of view, so the
 * backend's CORS check (meant for cross-origin callers) doesn't depend on which port the UI runs on.
 */
const forward: ProxyOptions = {
  target: apiTarget,
  configure: (proxy) => {
    proxy.on('proxyReq', (proxyReq) => {
      proxyReq.removeHeader('origin');
    });
  },
};

// The REST API plus the backend's Swagger UI and OpenAPI document.
const proxy: Record<string, ProxyOptions> = {
  '/api': forward,
  '/swagger-ui': forward,
  '/v3/api-docs': forward,
};

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy,
  },
  preview: {
    port: 4173,
    strictPort: true,
    proxy,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    restoreMocks: true,
  },
});
