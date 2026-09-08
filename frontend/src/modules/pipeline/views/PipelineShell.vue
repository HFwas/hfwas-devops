<script setup lang="ts">
import { GitBranch, KeyRound } from '@lucide/vue'
import type { MenuOption } from 'naive-ui'

const route = useRoute()
const router = useRouter()

const menuOptions: MenuOption[] = [
  { label: '流水线', key: '/pipeline/pipelines', icon: () => h(GitBranch, { size: 16 }) },
  { label: '凭证', key: '/pipeline/credentials', icon: () => h(KeyRound, { size: 16 }) },
]

const activeKey = computed(() => {
  if (route.path.startsWith('/pipeline/credentials')) return '/pipeline/credentials'
  return '/pipeline/pipelines'
})

const fill = computed(() => route.meta.fill === true)

function onSelect(key: string) {
  if (key !== route.path) void router.push(key)
}
</script>

<template>
  <n-layout has-sider class="pl-shell">
    <n-layout-sider bordered :width="168" :collapsed-width="168" content-style="padding-top: 8px">
      <n-menu :value="activeKey" :options="menuOptions" :indent="16" @update:value="onSelect" />
    </n-layout-sider>
    <n-layout-content class="pl-shell-main" :class="{ 'is-fill': fill }">
      <RouterView />
    </n-layout-content>
  </n-layout>
</template>

<style scoped>
.pl-shell {
  height: calc(100vh - 56px);
}

.pl-shell-main {
  height: 100%;
  overflow: auto;
  background: var(--wb-page-bg, #f5f7fb);
}

.pl-shell-main.is-fill {
  overflow: hidden;
}
</style>
