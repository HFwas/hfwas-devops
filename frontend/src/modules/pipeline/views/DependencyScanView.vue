<script setup lang="ts">
import { NButton, NCard, NDataTable, NInput, NSelect, NTag, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { ref } from 'vue'
import { dependencyApi } from '@/modules/pipeline/api/dependency'
import type { DependencyScanSubmitPayload, DependencyScanTask } from '@/modules/pipeline/types/dependency'

const message = useMessage()
const submitting = ref(false)
const tasks = ref<DependencyScanTask[]>([])

// Single scan form
const repoUrl = ref('')
const gitRef = ref('main')
const stack = ref<string | null>(null)

// Batch scan
const batchInput = ref('')
const batchTasks = ref<DependencyScanSubmitPayload[]>([])

const stackOptions = [
  { label: '自动检测', value: '' },
  { label: 'Java (Maven)', value: 'JAVA_MAVEN' },
  { label: 'Node.js', value: 'NODE' },
  { label: 'Go', value: 'GO' },
  { label: 'Python', value: 'PYTHON' },
]

async function submitSingle() {
  if (!repoUrl.value.trim()) {
    message.warning('请输入仓库地址')
    return
  }
  submitting.value = true
  try {
    const task = await dependencyApi.submitScan({
      repoUrl: repoUrl.value.trim(),
      gitRef: gitRef.value || 'main',
      stack: stack.value || undefined,
    })
    tasks.value.unshift(task)
    message.success('扫描已提交')
    repoUrl.value = ''
  } catch (e) {
    message.error('提交失败')
  } finally {
    submitting.value = false
  }
}

function parseBatchInput() {
  // 每行一个仓库 URL，支持逗号分隔
  const lines = batchInput.value
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  batchTasks.value = lines.map((url) => ({ repoUrl: url, gitRef: 'main' }))
}

async function submitBatch() {
  if (batchTasks.value.length === 0) {
    message.warning('请先输入仓库地址')
    return
  }
  submitting.value = true
  try {
    const results = await dependencyApi.batchSubmitScan({ scans: batchTasks.value })
    tasks.value.unshift(...results)
    message.success(`已提交 ${results.length} 个扫描任务`)
    batchInput.value = ''
    batchTasks.value = []
  } catch (e) {
    message.error('批量提交失败')
  } finally {
    submitting.value = false
  }
}

async function loadTasks() {
  try {
    tasks.value = await dependencyApi.listScanTasks({ limit: 50 })
  } catch {
    // silent
  }
}

const columns: DataTableColumns<DependencyScanTask> = [
  { title: '仓库', key: 'pipelineName', width: 200, ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 100,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: row.status === 'SUCCEEDED' ? 'success' : row.status === 'FAILED' ? 'error' : 'warning' }, () => row.status),
  },
  { title: '开始时间', key: 'startedAt', width: 160 },
  { title: '结束时间', key: 'finishedAt', width: 160 },
]

onMounted(loadTasks)
</script>

<template>
  <div class="ds-list">
    <header class="ds-header">
      <h3>依赖扫描</h3>
      <p class="ds-subtitle">批量提交仓库进行依赖分析，自动生成 CycloneDX SBOM</p>
    </header>

    <div class="ds-grid">
      <!-- 单仓库扫描 -->
      <n-card title="单仓库扫描" class="ds-card">
        <div class="ds-form">
          <n-input v-model:value="repoUrl" placeholder="仓库 URL (https://github.com/org/repo.git)" clearable />
          <div class="ds-form-row">
            <n-input v-model:value="gitRef" placeholder="分支 (默认 main)" style="flex: 1" />
            <n-select v-model:value="stack" :options="stackOptions" placeholder="语言栈" style="width: 180px" clearable />
          </div>
          <n-button type="primary" :loading="submitting" :disabled="!repoUrl.trim()" @click="submitSingle">
            提交扫描
          </n-button>
        </div>
      </n-card>

      <!-- 批量扫描 -->
      <n-card title="批量仓库扫描" class="ds-card">
        <div class="ds-form">
          <n-input
            v-model:value="batchInput"
            type="textarea"
            placeholder="每行一个仓库 URL，例如：&#10;https://github.com/org/repo1.git&#10;https://github.com/org/repo2.git"
            :rows="5"
          />
          <div v-if="batchInput" class="ds-batch-preview">
            <p>共解析 {{ batchInput.split('\n').filter(Boolean).length }} 个仓库</p>
          </div>
          <div class="ds-form-row">
            <n-button @click="parseBatchInput">解析仓库列表</n-button>
            <n-button type="primary" :loading="submitting" :disabled="batchTasks.length === 0" @click="submitBatch">
              批量提交 ({{ batchTasks.length }})
            </n-button>
          </div>
        </div>
      </n-card>
    </div>

    <!-- 扫描记录 -->
    <section class="ds-tasks">
      <h4>扫描记录</h4>
      <n-data-table
        :data="tasks"
        :columns="columns"
        :bordered="false"
        :single-line="false"
        :row-key="(row: DependencyScanTask) => String(row.runId)"
      />
    </section>
  </div>
</template>

<style scoped>
.ds-list {
  padding: 16px;
}
.ds-header {
  margin-bottom: 16px;
}
.ds-header h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.ds-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--wb-muted, #646a73);
}
.ds-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}
.ds-card {
  height: fit-content;
}
.ds-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ds-form-row {
  display: flex;
  gap: 8px;
}
.ds-batch-preview {
  font-size: 13px;
  color: var(--wb-muted, #646a73);
}
.ds-batch-preview p {
  margin: 0;
}
.ds-tasks h4 {
  margin: 0 0 12px;
  font-size: 15px;
}
</style>