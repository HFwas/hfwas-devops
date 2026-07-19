<script setup lang="ts">
import AppShell from './AppShell.vue'
import { appDateLocale, appLocale } from '@/shared/naive-locale'
</script>

<template>
  <n-config-provider :locale="appLocale" :date-locale="appDateLocale">
    <n-message-provider>
      <n-dialog-provider>
        <AppShell />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style>
body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: #f5f7fb;
}
a {
  color: #2080f0;
  text-decoration: none;
}
</style>
