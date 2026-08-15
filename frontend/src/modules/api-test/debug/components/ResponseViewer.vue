<template>
  <div class="response-viewer">
    <!-- 响应概览 -->
    <div class="response-viewer__summary">
      <div class="response-viewer__status">
        <n-tag
          :type="statusTagType"
          size="small"
        >
          {{ result.responseStatusCode || '---' }}
        </n-tag>
      </div>
      <div class="response-viewer__meta">
        <span class="response-viewer__meta-item">
          耗时: <strong>{{ result.durationMs }}ms</strong>
        </span>
        <span class="response-viewer__meta-item">
          大小: <strong>{{ formatSize(result.responseSize) }}</strong>
        </span>
        <span class="response-viewer__meta-item">
          Content-Type: <strong>{{ result.responseContentType || '---' }}</strong>
        </span>
      </div>
      <div class="response-viewer__status-text">
        <n-tag
          :type="result.allAssertionsPassed ? 'success' : 'warning'"
          size="tiny"
          v-if="result.allAssertionsPassed !== undefined"
        >
          {{ result.allAssertionsPassed ? '断言通过' : '断言失败' }}
        </n-tag>
      </div>
    </div>

    <!-- 错误信息 -->
    <n-alert v-if="result.errorMessage" type="error" :title="result.status" closable>
      {{ result.errorMessage }}
    </n-alert>

    <!-- 脚本日志 -->
    <div v-if="result.preRequestLogs?.length || result.postResponseLogs?.length" class="response-viewer__logs">
      <n-collapse>
        <n-collapse-item v-if="result.preRequestLogs?.length" title="前置脚本日志" name="preLogs">
          <n-log :log="result.preRequestLogs.join('\n')" :rows="3" />
        </n-collapse-item>
        <n-collapse-item v-if="result.postResponseLogs?.length" title="后置脚本日志" name="postLogs">
          <n-log :log="result.postResponseLogs.join('\n')" :rows="3" />
        </n-collapse-item>
      </n-collapse>
    </div>

    <!-- 响应头 -->
    <n-collapse :default-expanded-names="['body']">
      <n-collapse-item title="响应头" name="headers">
        <n-data-table
          v-if="result.responseHeaders && Object.keys(result.responseHeaders).length > 0"
          :columns="headerColumns"
          :data="headerData"
          :bordered="false"
          size="small"
          :max-height="200"
        />
        <n-empty v-else description="无响应头" />
      </n-collapse-item>

      <!-- 响应体 -->
      <n-collapse-item title="响应体" name="body">
        <response-body-renderer
          v-if="result.responseBody"
          :content-type="result.responseContentType"
          :body="result.responseBody"
          :response-status-code="result.responseStatusCode"
        />
        <n-empty v-else-if="!result.responseBody && result.responseStatusCode" description="响应体为空" />
      </n-collapse-item>

      <!-- 断言结果 -->
      <n-collapse-item v-if="result.assertionResults?.length" title="断言结果" name="assertions">
        <n-data-table
          :columns="assertionColumns"
          :data="result.assertionResults"
          :bordered="false"
          size="small"
          :max-height="200"
        />
      </n-collapse-item>

      <!-- 提取变量 -->
      <n-collapse-item v-if="result.extractedVariables && Object.keys(result.extractedVariables).length > 0" title="提取变量" name="extracts">
        <n-data-table
          :columns="extractColumns"
          :data="extractData"
          :bordered="false"
          size="small"
          :max-height="200"
        />
      </n-collapse-item>
    </n-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import type { ApiDebugResultVO } from '@/modules/api-test/debug/types/debug'
import ResponseBodyRenderer from '@/modules/api-test/shared/components/ResponseBodyRenderer.vue'

const props = defineProps<{
  result: ApiDebugResultVO
}>()

const statusTagType = computed(() => {
  const code = props.result.responseStatusCode
  if (!code) return 'default'
  if (code >= 200 && code < 300) return 'success'
  if (code >= 300 && code < 400) return 'warning'
  if (code >= 400) return 'error'
  return 'default'
})

const headerColumns = [
  { title: '名称', key: 'key', width: 200 },
  { title: '值', key: 'value', ellipsis: { tooltip: true } },
]

const headerData = computed(() => {
  const headers = props.result.responseHeaders
  if (!headers) return []
  return Object.entries(headers).map(([key, value]) => ({ key, value }))
})

const assertionColumns = [
  { title: '名称', key: 'name', width: 150 },
  { title: '期望值', key: 'expected', width: 150 },
  { title: '实际值', key: 'actual', ellipsis: { tooltip: true } },
  {
    title: '结果',
    key: 'passed',
    width: 70,
    render: (row: any) => row.passed
      ? h('span', { style: 'color: #67C23A;' }, '✓ 通过')
      : h('span', { style: 'color: #F56C6C;' }, '✗ 失败'),
  },
]

const extractColumns = [
  { title: '变量名', key: 'key', width: 150 },
  { title: '值', key: 'value', ellipsis: { tooltip: true } },
]

const extractData = computed(() => {
  const vars = props.result.extractedVariables
  if (!vars) return []
  return Object.entries(vars).map(([key, value]) => ({ key, value }))
})

function formatSize(bytes: number | undefined | null): string {
  if (bytes == null) return '---'
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}
</script>

<style scoped>
.response-viewer__summary {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
}

.response-viewer__meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #666;
}

.response-viewer__meta-item strong {
  color: #333;
}

.response-viewer__logs {
  margin-bottom: 12px;
}
</style>