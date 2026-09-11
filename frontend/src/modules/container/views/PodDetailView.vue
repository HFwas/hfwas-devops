<script setup lang="ts">
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import { NCard, NButton, NSpace, NTag, NTabs, NTabPane, NDescriptions, NDescriptionsItem, NDataTable, NEmpty, useMessage } from 'naive-ui'
import { podApi } from '@/modules/container/api/pod'
import type { PodDetail, ContainerStatus, PodCondition } from '@/modules/container/types/resource'
import type { MonitorRange } from '@/modules/container/types/monitor'
import { monitorApi } from '@/modules/container/api/monitor'
import { isApiError } from '@/shared/errors/apiError'
import PodShellTerminal from '@/modules/container/components/PodShellTerminal.vue'
import PodLogStream from '@/modules/container/components/PodLogStream.vue'
import PodMonitorView from '@/modules/container/views/monitor/PodMonitorView.vue'
import PodJvmMonitor from '@/modules/container/views/monitor/PodJvmMonitor.vue'

const props = defineProps<{ clusterId: string; namespace: string; name: string }>()
const router = useRouter()
const message = useMessage()

const pod = ref<PodDetail | null>(null)
const loading = ref(false)
const yamlContent = ref('')
const yamlLoading = ref(false)

// Monitor state
const range = ref<MonitorRange>('1h')
const hasJvm = ref(false)
const jvmChecking = ref(true)
const jvmRange = ref<MonitorRange>('1h')

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '加载失败'
}

async function load() {
  loading.value = true
  try {
    pod.value = await podApi.get(props.clusterId, props.namespace, props.name)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadYaml() {
  yamlLoading.value = true
  try {
    yamlContent.value = await podApi.yaml(props.clusterId, props.namespace, props.name)
  } catch (e) {
    yamlContent.value = `获取 YAML 失败: ${errorMessage(e)}`
  } finally {
    yamlLoading.value = false
  }
}

async function checkJvm() {
  jvmChecking.value = true
  try {
    const result = await monitorApi.jvmCheck(props.clusterId, props.namespace, props.name)
    hasJvm.value = result.hasJvmMetrics
  } catch {
    hasJvm.value = false
  } finally {
    jvmChecking.value = false
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
]

const conditionColumns = [
  { title: '类型', key: 'type' },
  { title: '状态', key: 'status', width: 90 },
  { title: '原因', key: 'reason' },
  { title: '消息', key: 'message', ellipsis: { tooltip: true } },
]

onMounted(() => {
  load()
  loadYaml()
  checkJvm()
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

    <n-card :bordered="false" v-if="pod">
      <template #header>
        <n-space align="center">
          <span style="font-weight: 600; font-size: 15px">{{ pod.name }}</span>
          <n-tag :type="statusTagType(pod.status)" size="small">{{ pod.status }}</n-tag>
        </n-space>
      </template>
      <n-tabs type="line" animated>
        <n-tab-pane name="info" tab="POD信息">
          <n-descriptions label-placement="left" :column="2">
            <n-descriptions-item label="Namespace">{{ pod.namespace }}</n-descriptions-item>
            <n-descriptions-item label="Node">{{ pod.nodeName || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Pod IP">{{ pod.podIP || '-' }}</n-descriptions-item>
            <n-descriptions-item label="QoS Class">{{ pod.qosClass || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Owner">{{ pod.ownerReference || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Age">{{ pod.age }}</n-descriptions-item>
          </n-descriptions>
        </n-tab-pane>
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
          <PodLogStream
            :clusterId="clusterId"
            :namespace="pod.namespace"
            :podName="pod.name"
            :containers="(pod.containers || []).map(c => ({ name: c.name, state: c.state }))"
          />
        </n-tab-pane>
        <n-tab-pane name="console" tab="控制台">
          <PodShellTerminal
            :clusterId="clusterId"
            :namespace="pod.namespace"
            :podName="pod.name"
            :containers="(pod.containers || []).map(c => ({ name: c.name, state: c.state }))"
          />
        </n-tab-pane>
        <n-tab-pane name="labels" tab="标签">
          <n-descriptions label-placement="left" :column="1" v-if="pod.labels && Object.keys(pod.labels).length">
            <n-descriptions-item v-for="(v, k) in pod.labels" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无标签" />
        </n-tab-pane>
        <n-tab-pane name="monitor" tab="监控" display-directive="show:lazy">
          <PodMonitorView
            :clusterId
            :namespace="pod.namespace"
            :name="pod.name"
            :containers="(pod.containers || []).map(c => c.name)"
            :range
            @update:range="(v) => range = v"
          />
        </n-tab-pane>
        <n-tab-pane v-if="hasJvm" name="jvm" tab="JVM 监控" display-directive="show:lazy">
          <PodJvmMonitor
            :clusterId
            :namespace="pod.namespace"
            :name="pod.name"
            :range
          />
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