import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  base: '/argo/',
  build: {
    outDir: 'dist',
  },
  server: {
    port: 5173,
    proxy: {
      '/api/argo': {
        target: 'http://localhost:8089',
        changeOrigin: true,
        rewrite: (path) => path.replace('/api', ''),
      },
    },
  },
})