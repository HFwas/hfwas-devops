/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    hmr: {
      clientPort: 5173,
    },
    proxy: {
      '/api/': {
        target: 'http://localhost:8089',
        changeOrigin: true,
        ws: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            const remoteAddress = req.socket?.remoteAddress
            if (remoteAddress) {
              proxyReq.setHeader('X-Forwarded-For', remoteAddress)
            }
          })
        },
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router'],
        },
      },
    },
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.test.ts'],
  },
})
