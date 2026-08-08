<script setup lang="ts">
import { ArrowDownRight, ArrowUpRight, ChartColumn } from '@lucide/vue'
import type { MonitorMetric } from '@/modules/pm/composables/useWorkbench'

// 扩展点：指标由外部注入，新增指标自动补格，无需改动本组件
const props = defineProps<{ metrics: MonitorMetric[] }>()

const router = useRouter()

function open(metric: MonitorMetric) {
  if (metric.path) void router.push(metric.path)
}
</script>

<template>
  <n-card :bordered="true" size="small" class="wb-card">
    <template #header>
      <span class="wb-card-title">
        <ChartColumn :size="16" class="wb-card-title-icon" />
        业务监控
      </span>
      <div class="wb-card-subtitle">近 7 天项目运行概况</div>
    </template>
    <template #header-extra>
      <n-tag size="tiny" :bordered="false">mock 数据</n-tag>
    </template>

    <div class="mon-grid">
      <div
        v-for="metric in props.metrics"
        :key="metric.key"
        class="mon-item"
        :class="{ 'is-clickable': !!metric.path }"
        @click="open(metric)"
      >
        <span class="mon-icon" :class="`tone-${metric.tone}`">
          <component :is="metric.icon" :size="16" />
        </span>
        <div class="mon-body">
          <div class="mon-label">{{ metric.label }}</div>
          <div class="mon-value-line">
            <span class="mon-value">{{ metric.value.toLocaleString() }}</span>
            <span class="mon-unit">{{ metric.unit }}</span>
            <span
              v-if="metric.delta !== undefined && metric.delta !== 0"
              class="mon-delta"
              :class="metric.delta > 0 ? 'is-up' : 'is-down'"
            >
              <ArrowUpRight v-if="metric.delta > 0" :size="12" />
              <ArrowDownRight v-else :size="12" />
              {{ Math.abs(metric.delta) }}
            </span>
          </div>
        </div>
      </div>
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

.mon-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.mon-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: var(--wb-chip-bg, #f8fafc);
  transition: border-color 0.2s, background-color 0.2s;
}

.mon-item.is-clickable {
  cursor: pointer;
}

.mon-item.is-clickable:hover {
  border-color: #4098fc;
  background: rgba(64, 152, 252, 0.08);
}

.mon-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
}

/* 指标语义配色：新增 tone 时在此追加一条即可 */
.tone-neutral { background: #eef2ff; color: #4f46e5; }
.tone-positive { background: #ecfdf5; color: #059669; }
.tone-warning { background: #fffbeb; color: #d97706; }
.tone-danger { background: #fef2f2; color: #dc2626; }

.mon-body {
  min-width: 0;
}

.mon-label {
  overflow: hidden;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mon-value-line {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
}

.mon-value {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.mon-unit {
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.mon-delta {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  margin-left: 2px;
  font-size: 12px;
}

.mon-delta.is-up {
  color: #059669;
}

.mon-delta.is-down {
  color: #dc2626;
}
</style>
