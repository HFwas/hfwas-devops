<template>
  <div class="workspace-response">
    <!-- 空状态 -->
    <div v-if="!result" class="workspace-response__empty">
      <n-empty description="点击「发送」开始调试" />
    </div>

    <template v-else>
      <!-- 响应状态栏 -->
      <div class="workspace-response__summary">
        <div class="workspace-response__status">
          <n-tag :type="statusTagType" size="small">
            {{ result.responseStatusCode || '---' }}
          </n-tag>
        </div>
        <div class="workspace-response__meta">
          <span class="workspace-response__meta-item">
            耗时: <strong>{{ result.durationMs }}ms</strong>
          </span>
          <span class="workspace-response__meta-item">
            大小: <strong>{{ formatSize(result.responseSize) }}</strong>
          </span>
          <span class="workspace-response__meta-item">
            Content-Type: <strong>{{ result.responseContentType || '---' }}</strong>
          </span>
        </div>
        <div class="workspace-response__status-text">
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
      <div v-if="result.preRequestLogs?.length || result.postResponseLogs?.length" class="workspace-response__logs">
        <n-collapse>
          <n-collapse-item v-if="result.preRequestLogs?.length" title="前置脚本日志" name="preLogs">
            <n-log :log="result.preRequestLogs.join('\n')" :rows="3" />
          </n-collapse-item>
          <n-collapse-item v-if="result.postResponseLogs?.length" title="后置脚本日志" name="postLogs">
            <n-log :log="result.postResponseLogs.join('\n')" :rows="3" />
          </n-collapse-item>
        </n-collapse>
      </div>

      <!-- 响应内容 Tab -->
      <n-tabs type="line" default-value="body" size="small" class="workspace-response__tabs">
        <!-- 响应体 -->
        <n-tab-pane name="body" tab="响应体">
          <response-body-renderer
            v-if="result"
            :content-type="result.responseContentType"
            :body="result.responseBody"
            :response-status-code="result.responseStatusCode"
          />
          <n-empty v-else description="暂无响应数据" />
        </n-tab-pane>

        <!-- 响应头 -->
        <n-tab-pane name="headers" tab="响应头">
          <n-data-table
            v-if="headerData.length > 0"
            :columns="headerColumns"
            :data="headerData"
            :bordered="false"
            size="small"
            :max-height="300"
          />
          <n-empty v-else description="无响应头" />
        </n-tab-pane>

        <!-- 断言结果 -->
        <n-tab-pane v-if="result.assertionResults?.length" name="assertions" tab="断言结果">
          <n-data-table
            :columns="assertionColumns"
            :data="result.assertionResults"
            :bordered="false"
            size="small"
            :max-height="300"
          />
        </n-tab-pane>

        <!-- 提取变量 -->
        <n-tab-pane v-if="extractData.length > 0" name="extracts" tab="提取变量">
          <n-data-table
            :columns="extractColumns"
            :data="extractData"
            :bordered="false"
            size="small"
            :max-height="300"
          />
        </n-tab-pane>
      </n-tabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { NTag } from 'naive-ui'
import type { ApiDebugResultVO } from '@/modules/api-test/debug/types/debug'
import ResponseBodyRenderer from '@/modules/api-test/shared/components/ResponseBodyRenderer.vue'

const props = defineProps<{
  result: ApiDebugResultVO | null
}>()

const statusTagType = computed(() => {
  const code = props.result?.responseStatusCode
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
  const headers = props.result?.responseHeaders
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
    render: (row: { passed: boolean }) => h(NTag, {
      size: 'small',
      type: row.passed ? 'success' : 'error',
    }, { default: () => (row.passed ? '✓ 通过' : '✗ 失败') }),
  },
]

const extractColumns = [
  { title: '变量名', key: 'key', width: 150 },
  { title: '值', key: 'value', ellipsis: { tooltip: true } },
]

const extractData = computed(() => {
  const vars = props.result?.extractedVariables
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
.workspace-response {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-top: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-chip-bg, #f8fafc);
  color: inherit;
}

.workspace-response__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.workspace-response__summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: var(--api-density-pad-y, 6px) 0;
  background: var(--wb-card-bg, #fff);
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.workspace-response__meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--wb-muted, #6b7280);
}

.workspace-response__meta-item strong {
  font-weight: 600;
}

.workspace-response__logs {
  padding: 8px 16px;
  background: var(--wb-card-bg, #fff);
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.workspace-response__tabs {
  flex: 1;
  padding: 0 16px;
  overflow: auto;
  background: var(--wb-card-bg, #fff);
}
</style>