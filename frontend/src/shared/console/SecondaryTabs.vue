<script setup lang="ts">
import { CONSOLE_TABS, resolveActiveTab, type ConsoleTab } from '@/shared/console/tabs'
import { useAuthStore } from '@/modules/user/stores/auth'

// 扩展点：Tab 清单来自 tabs.ts，新增 Tab 追加配置即可
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const tabs = computed(() => CONSOLE_TABS.filter((tab) => !tab.adminOnly || auth.isAdmin))

const activeKey = computed(() => resolveActiveTab(route.path, tabs.value))

function go(tab: ConsoleTab) {
  if (tab.key === activeKey.value) return
  void router.push(tab.path)
}
</script>

<template>
  <nav class="tabs-bar">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      type="button"
      class="tab-item"
      :class="{ 'is-active': tab.key === activeKey }"
      @click="go(tab)"
    >
      <component :is="tab.icon" :size="15" />
      {{ tab.label }}
    </button>
  </nav>
</template>

<style scoped>
.tabs-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 42px;
  padding: 0 24px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-card-bg, #fff);
}

.tab-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 100%;
  padding: 0 12px;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: var(--wb-muted, #6b7280);
  cursor: pointer;
  transition: color 0.2s;
}

.tab-item:hover {
  color: #2d80e6;
}

.tab-item.is-active {
  color: #2d80e6;
  font-weight: 500;
}

/* 激活态下划线 */
.tab-item.is-active::after {
  position: absolute;
  right: 8px;
  bottom: 0;
  left: 8px;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: #2d80e6;
  content: '';
}
</style>
