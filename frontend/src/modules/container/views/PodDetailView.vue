<script setup lang="ts">
import { ArrowLeft, RefreshCw, Terminal } from '@lucide/vue'
import { NCard, NButton, NSpace, NTag, NTabs, NTabPane, NDescriptions, NDescriptionsItem, NDataTable, NEmpty, useMessage } from 'naive-ui'
import { podApi } from '@/modules/container/api/pod'
import type { PodDetail, ContainerStatus, PodCondition } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string; namespace: string; name: string }>()
const router = useRouter()
const message = useMessage()

const pod = ref<PodDetail | null>(null)
const loading = ref(false)
const logContent = ref('')
const logLoading = ref(false)
const yamlContent = ref('')
const yamlLoading = ref(false)

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '加载失败'
}

async function load() {
  loading.value = true
  try {
    pod.value = await podApi.get(Number(props.clusterId), props.namespace, props.name)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadLogs(container?: string, tailLines = 100) {
  logLoading.value = true
  try {
    logContent.value = await podApi.logs(Number(props.clusterId), props.namespace, props.name, { container, tailLines })
  } catch (e) {
    logContent.value = `获取日志失败: ${errorMessage(e)}`
  } finally {
    logLoading.value = false
  }
}

async function loadYaml() {
  yamlLoading.value = true
  try {
    yamlContent.value = await podApi.yaml(Number(props.clusterId), props.namespace, props.name)
  } catch (e) {
    yamlContent.value = `获取 YAML 失败: ${errorMessage(e)}`
  } finally {
    yamlLoading.value = false
  }
}

const statusTagType = (s?: string) => {
  switch (s) {
    case 'Running': return 'success' as const
    case 'Pending': return 'warning' as const
    case 'Succeeded': return 'info' as const
    case 'Failed': return 'error' as const
    default: return 'default' as const
  }
}

const containerColumns = [
  { title: '名称', key: 'name' },
  { title: '镜像', key: 'image', ellipsis: { tooltip: true } },
  { title: '状态', key: 'state',
    render: (row: ContainerStatus) => h(NTag, { type: row.state === 'running' ? 'success' : row.state === 'waiting' ? 'warning' : 'error', size: 'small' }, () => row.state) },
  { title: 'Ready', key: 'ready', width: 70,
    render: (row: ContainerStatus) => row.ready ? '✓' : '✗' },
  { title: '重启', key: 'restartCount', width: 60 },
  { title: '日志', key: 'logs', width: 80,
    render: (row: ContainerStatus) => h(NButton, { size: 'tiny', quaternary: true, onClick: () => loadLogs(row.name) }, () => '查看') },
]

const conditionColumns = [
  { title: '类型', key: 'type' },
  { title: '状态', key: 'status', width: 90 },
  { title: '原因', key: 'reason' },
  { title: '消息', key: 'message', ellipsis: { tooltip: true } },
]

onMounted(() => {
  load()
  loadLogs()
  loadYaml()
})
</script>

<template>
  <div class="pod-detail-page">
    <n-space style="margin-bottom: 16px">
      <n-button quaternary @click="router.push(`/container/clusters/${clusterId}/pods`)">
        <template #icon><ArrowLeft :size="16" /></template>
        返回 Pod 列表
      </n-button>
      <n-button quaternary @click="load">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </n-button>
    </n-space>

    <n-card :title="pod?.name" :bordered="false" v-if="pod">
      <template #header-extra>
        <n-tag :type="statusTagType(pod.status)">{{ pod.status }}</n-tag>
      </template>
      <n-descriptions label-placement="left" :column="2">
        <n-descriptions-item label="Namespace">{{ pod.namespace }}</n-descriptions-item>
        <n-descriptions-item label="Node">{{ pod.nodeName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="Pod IP">{{ pod.podIP || '-' }}</n-descriptions-item>
        <n-descriptions-item label="QoS Class">{{ pod.qosClass || '-' }}</n-descriptions-item>
        <n-descriptions-item label="Owner">{{ pod.ownerReference || '-' }}</n-descriptions-item>
        <n-descriptions-item label="Age">{{ pod.age }}</n-descriptions-item>
      </n-descriptions>
    </n-card>

    <n-card :bordered="false" style="margin-top: 16px" v-if="pod">
      <n-tabs type="line" animated>
        <n-tab-pane name="containers" tab="容器">
          <n-data-table :columns="containerColumns" :data="pod.containers || []" :bordered="false" :max-height="300" />
        </n-tab-pane>
        <n-tab-pane name="conditions" tab="Conditions">
          <n-data-table :columns="conditionColumns" :data="pod.conditions || []" :bordered="false" :max-height="300" />
        </n-tab-pane>
        <n-tab-pane name="yaml" tab="YAML">
          <pre class="yaml-block">{{ yamlLoading ? '加载中...' : yamlContent }}</pre>
        </n-tab-pane>
        <n-tab-pane name="logs" tab="日志">
          <pre class="log-block">{{ logLoading ? '加载中...' : (logContent || '(无日志)') }}</pre>
          <n-button size="tiny" quaternary style="margin-top: 8px" @click="loadLogs()">重新加载日志</n-button>
        </n-tab-pane>
        <n-tab-pane name="labels" tab="标签">
          <n-descriptions label-placement="left" :column="1" v-if="pod.labels && Object.keys(pod.labels).length">
            <n-descriptions-item v-for="(v, k) in pod.labels" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无标签" />
        </n-tab-pane>
        <n-tab-pane name="annotations" tab="注解">
          <n-descriptions label-placement="left" :column="1" v-if="pod.annotations && Object.keys(pod.annotations).length">
            <n-descriptions-item v-for="(v, k) in pod.annotations" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无注解" />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<style scoped>
.pod-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}
.yaml-block, .log-block {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  overflow: auto;
  max-height: 500px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>