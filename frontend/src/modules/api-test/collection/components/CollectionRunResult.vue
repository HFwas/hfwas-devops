<template>
  <div class="collection-run-result">
    <!-- 运行概况 -->
    <n-card v-if="runDetail" size="small" class="run-summary">
      <n-descriptions :column="4" size="small" bordered>
        <n-descriptions-item label="执行名称">
          {{ runDetail.name }}
        </n-descriptions-item>
        <n-descriptions-item label="状态">
          <n-tag :type="statusType(runDetail.status)" size="small">
            {{ statusLabel(runDetail.status) }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="执行时间">
          {{ runDetail.createTime }}
        </n-descriptions-item>
        <n-descriptions-item label="总耗时">
          {{ runDetail.durationMs }}ms
        </n-descriptions-item>
        <n-descriptions-item label="总数">
          {{ runDetail.totalCount }}
        </n-descriptions-item>
        <n-descriptions-item label="通过">
          <span class="count-passed">{{ runDetail.passedCount }}</span>
        </n-descriptions-item>
        <n-descriptions-item label="失败">
          <span class="count-failed">{{ runDetail.failedCount }}</span>
        </n-descriptions-item>
        <n-descriptions-item label="错误">
          <span class="count-error">{{ runDetail.errorCount }}</span>
        </n-descriptions-item>
      </n-descriptions>
    </n-card>

    <!-- 执行项列表 -->
    <n-card title="执行详情" size="small" class="run-items">
      <n-data-table
        :columns="itemColumns"
        :data="runDetail?.items || []"
        :bordered="false"
        size="small"
        :max-height="400"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { NTag, NButton, NDataTable, NDescriptions, NCard, NDescriptionsItem } from 'naive-ui'
import type { CollectionRunDetailVO, CollectionRunItemVO } from '@/modules/api-test/collection/types/collection'

defineProps<{
  runDetail: CollectionRunDetailVO | null
}>()

const emit = defineEmits<{
  'viewItem': [item: CollectionRunItemVO]
}>()

const itemColumns = [
  { title: '#', key: 'sortOrder', width: 40 },
  {
    title: '接口名称',
    key: 'name',
    ellipsis: { tooltip: true },
  },
  { title: '请求方式', key: 'requestMethod', width: 80 },
  {
    title: '状态码',
    key: 'responseStatusCode',
    width: 70,
    render: (row: CollectionRunItemVO) => {
      if (!row.responseStatusCode) return '-'
      return h(NTag, {
        size: 'small',
        type: row.responseStatusCode < 400 ? 'success' : 'error',
      }, { default: () => row.responseStatusCode })
    },
  },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row: CollectionRunItemVO) => h(NTag, {
      size: 'small',
      type: statusType(row.status),
    }, { default: () => statusLabel(row.status) }),
  },
  {
    title: '耗时',
    key: 'durationMs',
    width: 70,
    render: (row: CollectionRunItemVO) => `${row.durationMs}ms`,
  },
  {
    title: '断言',
    key: 'allAssertionsPassed',
    width: 60,
    render: (row: CollectionRunItemVO) => {
      if (row.allAssertionsPassed === undefined) return '-'
      return h(NTag, {
        size: 'small',
        type: row.allAssertionsPassed ? 'success' : 'error',
      }, { default: () => row.allAssertionsPassed ? '通过' : '失败' })
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: (row: CollectionRunItemVO) => h(NButton, {
      size: 'tiny',
      onClick: () => emit('viewItem', row),
    }, { default: () => '详情' }),
  },
]

function statusType(status: string): 'success' | 'warning' | 'error' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'error' | 'info'> = {
    COMPLETED: 'success',
    SUCCESS: 'success',
    RUNNING: 'info',
    FAILED: 'error',
    FAILURE: 'error',
    ERROR: 'error',
    PENDING: 'warning',
    SKIPPED: 'warning',
  }
  return map[status] || 'default'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    COMPLETED: '完成',
    SUCCESS: '成功',
    RUNNING: '运行中',
    FAILED: '失败',
    FAILURE: '失败',
    ERROR: '错误',
    PENDING: '等待',
    SKIPPED: '跳过',
  }
  return map[status] || status
}
</script>

<style scoped>
.run-summary {
  margin-bottom: 12px;
}

.run-items {
  margin-bottom: 12px;
}

.count-passed {
  color: #18a058;
  font-weight: bold;
}

.count-failed {
  color: #d03050;
  font-weight: bold;
}

.count-error {
  color: #f0a020;
  font-weight: bold;
}
</style>