<script setup lang="ts">
import { Clock4 } from '@lucide/vue'
import type { RecentVisitItem } from '@/modules/pm/composables/useWorkbench'

// 扩展点：条目由外部注入，数量不限，超出宽度自动换行
const props = defineProps<{ items: RecentVisitItem[] }>()

const router = useRouter()

function open(item: RecentVisitItem) {
  void router.push(item.path)
}
</script>

<template>
  <n-card :bordered="true" size="small" class="wb-card">
    <template #header>
      <span class="wb-card-title">
        <Clock4 :size="16" class="wb-card-title-icon" />
        最近访问
      </span>
    </template>
    <template #header-extra>
      <n-button text size="small" @click="router.push('/pm/projects')">全部项目 ›</n-button>
    </template>

    <n-empty v-if="props.items.length === 0" description="暂无访问记录" size="small" />
    <div v-else class="visit-list">
      <button
        v-for="item in props.items"
        :key="item.key"
        type="button"
        class="visit-chip"
        :title="`${item.scope} · ${item.visitedAt}`"
        @click="open(item)"
      >
        <span class="visit-chip-icon">
          <component :is="item.icon" :size="14" />
        </span>
        <span class="visit-chip-name">{{ item.name }}</span>
        <span class="visit-chip-time">{{ item.visitedAt }}</span>
      </button>
    </div>
  </n-card>
</template>

<style scoped>
.wb-card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
}

.wb-card-title-icon {
  color: var(--n-text-color-3, #909399);
}

.visit-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.visit-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  padding: 5px 12px 5px 6px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 999px;
  background: var(--wb-chip-bg, #f8fafc);
  font: inherit;
  font-size: 13px;
  color: inherit;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}

.visit-chip:hover {
  border-color: #4098fc;
  background: rgba(64, 152, 252, 0.08);
}

.visit-chip-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--wb-card-bg, #fff);
  color: var(--wb-muted, #6b7280);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.visit-chip:hover .visit-chip-icon,
.visit-chip:hover .visit-chip-name {
  color: #2d80e6;
}

.visit-chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.visit-chip-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}
</style>
