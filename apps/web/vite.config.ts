import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 3000,
  },
  preview: {
    host: '127.0.0.1',
    port: 3000,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup/setup.ts'],
    css: true,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
