import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { host: '127.0.0.1', strictPort: true },
  build: { target: 'es2022', sourcemap: false },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'], maxWorkers: 1 },
});
