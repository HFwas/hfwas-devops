<script setup lang="ts">
import { LayoutGrid, Plus } from '@lucide/vue'
import type { ResourceEntry, ResourceSummary } from '@/modules/pm/composables/useWorkbench'

// 扩展点：资源方块与头部小结均由外部注入，追加即自动补格
const props = defineProps<{
  entries: ResourceEntry[]
  summary: ResourceSummary[]
}>()

const router = useRouter()
</script>

<template>
  <n-card :bordered="true" size="small" class="wb-card">
    <template #header>
      <span class="wb-card-title">
        <LayoutGrid :size="16" class="wb-card-title-icon" />
        我的资源
      </span>
      <div class="wb-card-subtitle">项目管理产品下的资源概览</div>
    </template>
    <template #header-extra>
      <n-space align="center" :size="12">
        <span v-for="item in props.summary" :key="item.key" class="res-summary">
          <component :is="item.icon" :size="14" />
          <span class="res-summary-label">{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </span>
        <n-button size="small" type="primary" @click="router.push('/pm/projects')">
          <template #icon><Plus :size="14" /></template>
          新建项目
        </n-button>
      </n-space>
    </template>

    <div class="res-grid">
      <button
        v-for="entry in props.entries"
        :key="entry.key"
        type="button"
        class="res-tile"
        @click="router.push(entry.path)"
      >
        <n-tag v-if="entry.tag" size="tiny" type="info" class="res-tile-tag">{{ entry.tag }}</n-tag>

        <span class="res-tile-icon" :class="`tone-${entry.tone}`">
          <component :is="entry.icon" :size="18" />
        </span>

        <span class="res-tile-body">
          <span class="res-tile-head">
            <span class="res-tile-count">{{ entry.count.toLocaleString() }}</span>
            <span class="res-tile-name">{{ entry.name }}</span>
          </span>
          <span class="res-tile-desc">{{ entry.description }}</span>
        </span>

        <span class="res-tile-action">进入管理 ›</span>
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

.wb-card-subtitle {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--wb-muted, #6b7280);
}

.res-summary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.res-summary-label {
  color: inherit;
}

.res-grid {
  display: grid;
  /* 3 列排布，6 个入口刚好两行；窄屏逐级降列 */
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 900px) {
  .res-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .res-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.res-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 10px;
  background: var(--wb-card-bg, #fff);
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.res-tile:hover {
  transform: translateY(-2px);
  border-color: #4098fc;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
}

.res-tile-tag {
  position: absolute;
  top: 10px;
  right: 10px;
}

.res-tile-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
}

/* 资源类型配色：新增 tone 时在此追加一条即可 */
.tone-blue { background: #eff6ff; color: #2563eb; }
.tone-violet { background: #f5f3ff; color: #7c3aed; }
.tone-green { background: #ecfdf5; color: #059669; }
.tone-amber { background: #fffbeb; color: #d97706; }
.tone-cyan { background: #ecfeff; color: #0891b2; }
.tone-rose { background: #fff1f2; color: #e11d48; }

.res-tile-body {
  display: block;
  width: 100%;
  min-width: 0;
}

.res-tile-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.res-tile-count {
  font-size: 20px;
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.res-tile-name {
  font-size: 13px;
  font-weight: 500;
}

.res-tile-desc {
  display: block;
  margin-top: 6px;
  overflow: hidden;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.res-tile-action {
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.res-tile:hover .res-tile-action {
  color: #2d80e6;
}
</style>
