<script setup lang="ts">
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import { NCard, NButton, NSpace, NGrid, NGi, NStatistic, NDataTable, NTag, NText, NDescriptions, NDescriptionsItem, NTabs, NTabPane, NEmpty, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { useRouter } from 'vue-router'
import { clusterApi } from '@/modules/container/api/cluster'
import type { ClusterComponentVO, ClusterStatsVO, ClusterVO, NodeComponentVO, SystemComponentVO } from '@/modules/container/types/cluster'
import { useClusterStore } from '@/modules/container/stores/cluster'
import { isApiError } from '@/shared/errors/apiError'
import ClusterMonitorView from '@/modules/container/views/monitor/ClusterMonitorView.vue'

const props = defineProps<{ id: string }>()
const router = useRouter()
const message = useMessage()
const clusterStore = useClusterStore()

const cluster = ref<ClusterVO | null>(null)
const stats = ref<ClusterStatsVO | null>(null)
const components = ref<ClusterComponentVO | null>(null)
const loading = ref(false)
const compLoading = ref(false)

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '加载失败'
}

async function load() {
  loading.value = true
  try {
    cluster.value = await clusterApi.get(props.id)
    clusterStore.setCurrent(cluster.value)
    stats.value = await clusterApi.stats(props.id)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function testConnection() {
  try {
    const ok = await clusterApi.test(props.id)
    message.success(ok ? '连接成功' : '连接失败')
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

const statusTagType = (s?: string) => {
  switch (s) {
    case 'Connected': return 'success' as const
    case 'Degraded': return 'warning' as const
    case 'Disconnected': return 'error' as const
    default: return 'default' as const
  }
}

const navigateItems = [
  { label: 'Pod', path: `/container/clusters/${props.id}/pods`, color: '#18a058' },
  { label: 'Deployment', path: `/container/clusters/${props.id}/deployments`, color: '#2080f0' },
  { label: 'Service', path: `/container/clusters/${props.id}/services`, color: '#f0a020' },
]

async function loadComponents() {
  compLoading.value = true
  try {
    components.value = await clusterApi.components(props.id)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    compLoading.value = false
  }
}

const nodeColumns: DataTableColumns<NodeComponentVO> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  { title: 'Kubelet 版本', key: 'kubeletVersion', width: 130 },
  { title: '容器运行时', key: 'containerRuntime', width: 170, ellipsis: { tooltip: true } },
  { title: 'OS 镜像', key: 'osImage', width: 180, ellipsis: { tooltip: true } },
  { title: '内核版本', key: 'kernelVersion', width: 140, ellipsis: { tooltip: true } },
  { title: '架构', key: 'architecture', width: 80 },
  { title: '状态', key: 'status', width: 100,
    render: (row) => h(NTag, {
      type: row.status === 'Ready' ? 'success' : 'error',
      size: 'small'
    }, () => row.status) },
]

const systemColumns: DataTableColumns<SystemComponentVO> = [
  { title: '名称', key: 'name', width: 140 },
  { title: '命名空间', key: 'namespace', width: 120 },
  { title: '状态', key: 'status', width: 90,
    render: (row) => h(NTag, {
      type: row.status === 'Healthy' ? 'success' : 'warning',
      size: 'small'
    }, () => row.status) },
  { title: '版本', key: 'version', width: 120 },
  { title: '就绪/期望', key: 'replicas', width: 100,
    render: (row) => `${row.readyReplicas}/${row.desiredReplicas}` },
]

onMounted(() => {
  load()
  loadComponents()
})
</script>

<template>
  <div class="cluster-detail-page">
    <n-space style="margin-bottom: 16px">
      <n-button quaternary @click="router.push('/container/clusters')">
        <template #icon><ArrowLeft :size="16" /></template>
        返回集群列表
      </n-button>
    </n-space>

    <n-card :title="cluster?.alias || cluster?.name" :bordered="false" v-if="cluster">
      <template #header-extra>
        <n-space>
          <n-tag :type="statusTagType(cluster.status)">{{ cluster.status }}</n-tag>
          <n-button quaternary size="small" @click="testConnection">
            <template #icon><RefreshCw :size="14" /></template>
            测试连接
          </n-button>
        </n-space>
      </template>
      <n-descriptions label-placement="left" :column="2">
        <n-descriptions-item label="名称">{{ cluster.name }}</n-descriptions-item>
        <n-descriptions-item label="别名">{{ cluster.alias || '-' }}</n-descriptions-item>
        <n-descriptions-item label="提供商">{{ cluster.provider || '-' }}</n-descriptions-item>
        <n-descriptions-item label="版本">{{ cluster.version || '-' }}</n-descriptions-item>
        <n-descriptions-item label="模式">{{ cluster.mode }}</n-descriptions-item>
      </n-descriptions>
    </n-card>

    <n-grid :cols="4" :x-gap="12" style="margin-top: 16px" v-if="stats">
      <n-gi>
        <n-card :bordered="false" size="small">
          <n-statistic title="节点数" :value="stats.nodeCount" />
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="false" size="small">
          <n-statistic title="Pod 数" :value="stats.podCount" />
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="false" size="small">
          <n-statistic title="CPU 总量" :value="stats.cpuTotal.toFixed(1)" />
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="false" size="small">
          <n-statistic title="内存总量" :value="(stats.memoryTotal / 1024 / 1024 / 1024).toFixed(1) + 'GB'" />
        </n-card>
      </n-gi>
    </n-grid>

    <n-card title="资源导航" :bordered="false" style="margin-top: 16px">
      <n-space>
        <n-button v-for="item in navigateItems" :key="item.label"
          :style="{ borderLeft: `3px solid ${item.color}` }"
          @click="router.push(item.path)">
          {{ item.label }}
        </n-button>
      </n-space>
    </n-card>

    <n-card title="集群组件" :bordered="false" style="margin-top: 16px" v-if="components">
      <template #header-extra>
        <n-text depth="3" style="font-size: 13px">
          Kubernetes {{ components.kubernetesVersion }} · {{ components.nodeCount }} 节点
        </n-text>
      </template>
      <n-tabs type="line" animated>
        <n-tab-pane name="nodes" tab="Node 组件">
          <n-data-table
            :columns="nodeColumns"
            :data="components.nodes"
            :loading="compLoading"
            :bordered="false"
            :max-height="400"
            size="small"
          />
          <n-empty v-if="!compLoading && components.nodes.length === 0" description="暂无节点数据" style="padding: 24px" />
        </n-tab-pane>
        <n-tab-pane name="system" tab="系统组件">
          <n-data-table
            :columns="systemColumns"
            :data="components.systemComponents"
            :loading="compLoading"
            :bordered="false"
            :max-height="400"
            size="small"
          />
          <n-empty v-if="!compLoading && components.systemComponents.length === 0" description="暂无系统组件数据" style="padding: 24px" />
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <n-card title="监控" :bordered="false" style="margin-top: 16px">
      <ClusterMonitorView :clusterId="id" />
    </n-card>
  </div>
</template>

<style scoped>
.cluster-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}
</style>