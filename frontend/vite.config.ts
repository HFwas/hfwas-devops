/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  optimizeDeps: {
    // 排除 naive-ui 的预打包 —— 开发模式避免全量 barrel export 被 esbuild 打包为 3.8 MB
    // 改为按需加载真实导入的子模块，HTTP/2 多路复用性能可接受
    exclude: ['naive-ui'],
  },
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [NaiveUiResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
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
      // Trailing slash so SPA route `/api-test` is NOT proxied (prefix `/api` would match it).
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
          'naive-ui': ['naive-ui'],
          'vendor-base': ['vue', 'vue-router', 'pinia'],
          'shared-utils': ['lodash-es', 'date-fns'],
        },
      },
    },
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.test.ts'],
  },
})
