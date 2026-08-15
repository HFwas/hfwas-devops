<script setup lang="ts">
import { computed } from 'vue'
import type { ApiDebugHistoryVO } from '@/modules/api-test/debug/types/debug'

const props = withDefaults(defineProps<{
  records: ApiDebugHistoryVO[]
  loading?: boolean
  keyword?: string
}>(), {
  loading: false,
  keyword: '',
})

const emit = defineEmits<{
  select: [id: number]
}>()

const filteredRecords = computed(() => {
  const kw = props.keyword.trim().toLowerCase()
  if (!kw) return props.records
  return props.records.filter((record) =>
    record.name.toLowerCase().includes(kw)
    || record.requestUrl.toLowerCase().includes(kw)
    || record.requestMethod.toLowerCase().includes(kw),
  )
})

function methodTagClass(method: string) {
  return ['method-tag', `method-tag--${method.toUpperCase()}`]
}

function onSelect(id: number) {
  emit('select', id)
}
</script>

<template>
  <div class="history-sidebar-list" data-testid="history-sidebar-list">
    <div v-if="loading" class="history-sidebar-list__loading">
      <n-spin size="small" />
    </div>

    <template v-else-if="filteredRecords.length">
      <div
        v-for="record in filteredRecords"
        :key="record.id"
        class="history-sidebar-list__row"
        :data-testid="`history-row-${record.id}`"
        @click="onSelect(record.id)"
      >
        <span :class="methodTagClass(record.requestMethod)">
          {{ record.requestMethod.toUpperCase() }}
        </span>
        <span class="history-sidebar-list__name">{{ record.name }}</span>
        <span class="history-sidebar-list__status">
          {{ record.responseStatusCode ?? '—' }}
        </span>
        <span class="history-sidebar-list__time">{{ record.createTime }}</span>
      </div>
    </template>

    <n-empty
      v-else
      description="发送请求后会出现在这里"
      class="history-sidebar-list__empty"
    />
  </div>
</template>

<style scoped>
.history-sidebar-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding: var(--api-density-pad-y, 4px) 0;
}

.history-sidebar-list__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 12px;
}

.history-sidebar-list__row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: var(--api-row-height, 28px);
  padding: var(--api-density-pad-y, 4px) var(--api-density-pad-x, 8px);
  cursor: pointer;
  font-size: var(--api-font, 13px);
}

.history-sidebar-list__row:hover {
  background: var(--wb-chip-bg, #f8fafc);
}

.method-tag {
  flex-shrink: 0;
  display: inline-block;
  font-size: var(--api-font-sm, 10px);
  font-weight: 700;
  padding: 1px 4px;
  border: 1px solid currentColor;
  border-radius: 2px;
  line-height: 1.2;
  color: var(--api-method-default, #64748b);
}

.method-tag--GET {
  color: var(--api-method-get, #10b981);
}

.method-tag--POST {
  color: var(--api-method-post, #f59e0b);
}

.method-tag--PUT {
  color: var(--api-method-put, #3b82f6);
}

.method-tag--PATCH {
  color: var(--api-method-patch, #8b5cf6);
}

.method-tag--DELETE {
  color: var(--api-method-delete, #ef4444);
}

.method-tag--HEAD,
.method-tag--OPTIONS,
.method-tag--TRACE {
  color: var(--api-method-default, #64748b);
}

.history-sidebar-list__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-sidebar-list__status {
  flex-shrink: 0;
  font-size: var(--api-font-sm, 11px);
  font-weight: 600;
  color: var(--wb-text-secondary, #64748b);
}

.history-sidebar-list__time {
  flex-shrink: 0;
  font-size: var(--api-font-sm, 10px);
  color: var(--wb-text-secondary, #94a3b8);
}

.history-sidebar-list__empty {
  padding: 24px 12px;
}
</style>
