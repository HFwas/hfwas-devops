<script setup lang="ts">
import AccountCard from '@/modules/pm/components/workbench/AccountCard.vue'
import MonitorStatsCard from '@/modules/pm/components/workbench/MonitorStatsCard.vue'
import MyResourcesCard from '@/modules/pm/components/workbench/MyResourcesCard.vue'
import RecentVisitsCard from '@/modules/pm/components/workbench/RecentVisitsCard.vue'
import { useWorkbench } from '@/modules/pm/composables/useWorkbench'
import { useAuthStore } from '@/modules/user/stores/auth'

const auth = useAuthStore()
const { recentVisits, resourceEntries, resourceSummary, monitorMetrics } = useWorkbench()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(() => auth.user?.displayName || auth.user?.username || '')
</script>

<template>
  <div class="wb-page">
    <header class="wb-hero">
      <div>
        <h1 class="wb-hero-title">{{ greeting }}{{ displayName ? `，${displayName}` : '' }}</h1>
        <p class="wb-hero-desc">平台工作台 · 租户 {{ auth.activeTenantName || '未指定' }}</p>
      </div>
    </header>

    <!-- 左右流式：左侧最近访问 + 我的资源，右侧账号信息 + 业务监控 -->
    <div class="wb-grid">
      <div class="wb-col">
        <RecentVisitsCard :items="recentVisits" />
        <MyResourcesCard :entries="resourceEntries" :summary="resourceSummary" />
      </div>
      <div class="wb-col">
        <AccountCard />
        <MonitorStatsCard :metrics="monitorMetrics" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.wb-page {
  /* 淡渐变背景，与卡片形成层次 */
  padding: 20px 24px 28px;
  background:
    radial-gradient(1200px 320px at 8% -10%, rgba(64, 152, 252, 0.12), transparent 60%),
    radial-gradient(900px 300px at 100% 0%, rgba(124, 58, 237, 0.1), transparent 60%);
}

.wb-hero {
  margin-bottom: 16px;
}

.wb-hero-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.wb-hero-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--wb-muted, #6b7280);
}

.wb-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.wb-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

@media (max-width: 1200px) {
  .wb-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
