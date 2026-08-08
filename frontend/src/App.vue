<script setup lang="ts">
import { darkTheme } from 'naive-ui'
import type { GlobalThemeOverrides } from 'naive-ui'
import AppShell from './AppShell.vue'
import { appDateLocale, appLocale } from '@/shared/naive-locale'
</script>

<template>
  <n-config-provider>
    <n-message-provider>
      <n-dialog-provider>
        <AppShell />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style>
/* 控制台卡片配色变量，暗色下整体跟随 */
:root {
  --wb-border: #e5e7eb;
  --wb-muted: #6b7280;
  --wb-card-bg: #ffffff;
  --wb-chip-bg: #f8fafc;
  --wb-page-bg: #f5f7fb;
}

html.dark {
  --wb-border: #2b3240;
  --wb-muted: #9aa4b2;
  --wb-card-bg: #1b1f27;
  --wb-chip-bg: #232935;
  --wb-page-bg: #101317;
}

body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: var(--wb-page-bg);
}
a {
  color: #2d80e6;
  text-decoration: none;
}
</style>
