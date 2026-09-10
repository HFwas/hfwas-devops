<script setup lang="ts">
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import { NCard, NButton, NSpace, NTag, NTabs, NTabPane, NDescriptions, NDescriptionsItem, NDataTable, NEmpty, useMessage } from 'naive-ui'
import { nodeApi } from '@/modules/container/api/node'
import type { NodeDetail, NodeAddress, NodeTaint, NodeImage } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'
import NodeMonitorView from '@/modules/container/views/monitor/NodeMonitorView.vue'

const props = defineProps<{ clusterId: string; name: string }>()
const router = useRouter()
const message = useMessage()

const node = ref<NodeDetail | null>(null)
const loading = ref(false)

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '加载失败'
}

async function load() {
  loading.value = true
  try {
    node.value = await nodeApi.get(props.clusterId, props.name)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

const statusTagType = (s?: string) => {
  switch (s) {
    case 'Ready': return 'success' as const
    case 'NotReady': return 'error' as const
    default: return 'default' as const
  }
}

function formatMem(mb: number): string {
  if (mb >= 1024) return (mb / 1024).toFixed(1) + ' GB'
  return mb + ' MB'
}

const addressColumns = [
  { title: '类型', key: 'type', width: 120 },
  { title: '地址', key: 'address' },
]

const taintColumns = [
  { title: 'Key', key: 'key' },
  { title: 'Value', key: 'value' },
  { title: 'Effect', key: 'effect', width: 120 },
]

const imageColumns = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  { title: '大小', key: 'sizeBytes', width: 100,
    render: (row: NodeImage) => {
      const mb = row.sizeBytes / (1024 * 1024)
      return mb > 1 ? mb.toFixed(1) + ' MB' : (row.sizeBytes / 1024).toFixed(0) + ' KB'
    } },
]

onMounted(load)
</script>

<template>
  <div class="node-detail-page">
    <n-space style="margin-bottom: 16px">
      <n-button quaternary @click="router.push(`/container/clusters/${clusterId}/nodes`)">
        <template #icon><ArrowLeft :size="16" /></template>
        返回 Node 列表
      </n-button>
      <n-button quaternary @click="load">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </n-button>
    </n-space>

    <n-card :title="node?.name" :bordered="false" v-if="node">
      <template #header-extra>
        <n-space>
          <n-tag :type="statusTagType(node.status)">{{ node.status }}</n-tag>
          <n-tag>{{ node.role }}</n-tag>
        </n-space>
      </template>
      <n-descriptions label-placement="left" :column="2">
        <n-descriptions-item label="Kubelet 版本">{{ node.kubeletVersion || '-' }}</n-descriptions-item>
        <n-descriptions-item label="容器运行时">{{ node.containerRuntime || '-' }}</n-descriptions-item>
        <n-descriptions-item label="OS 镜像">{{ node.osImage || '-' }}</n-descriptions-item>
        <n-descriptions-item label="内核">{{ node.kernelVersion || '-' }}</n-descriptions-item>
        <n-descriptions-item label="架构">{{ node.architecture || '-' }}</n-descriptions-item>
        <n-descriptions-item label="CPU">{{ node.cpuCapacity }} 核</n-descriptions-item>
        <n-descriptions-item label="内存">{{ formatMem(node.memoryCapacity) }}</n-descriptions-item>
        <n-descriptions-item label="Pod 数">{{ node.podCount }}</n-descriptions-item>
        <n-descriptions-item label="Pod CIDR">{{ node.podCIDR || '-' }}</n-descriptions-item>
        <n-descriptions-item label="Provider ID">{{ node.providerID || '-' }}</n-descriptions-item>
      </n-descriptions>
    </n-card>

    <n-card :bordered="false" style="margin-top: 16px" v-if="node">
      <n-tabs type="line" animated>
        <n-tab-pane name="addresses" tab="地址">
          <n-data-table v-if="node.addresses?.length" :columns="addressColumns" :data="node.addresses" :bordered="false" />
          <n-empty v-else description="无地址信息" />
        </n-tab-pane>
        <n-tab-pane name="taints" tab="污点">
          <n-data-table v-if="node.taints?.length" :columns="taintColumns" :data="node.taints" :bordered="false" />
          <n-empty v-else description="无污点" />
        </n-tab-pane>
        <n-tab-pane name="nodeinfo" tab="系统信息" v-if="node.nodeInfo">
          <n-descriptions label-placement="left" :column="1">
            <n-descriptions-item label="Machine ID">{{ node.nodeInfo.machineID || '-' }}</n-descriptions-item>
            <n-descriptions-item label="System UUID">{{ node.nodeInfo.systemUUID || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Boot ID">{{ node.nodeInfo.bootID || '-' }}</n-descriptions-item>
            <n-descriptions-item label="内核版本">{{ node.nodeInfo.kernelVersion || '-' }}</n-descriptions-item>
            <n-descriptions-item label="OS 镜像">{{ node.nodeInfo.osImage || '-' }}</n-descriptions-item>
            <n-descriptions-item label="容器运行时版本">{{ node.nodeInfo.containerRuntimeVersion || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Kubelet 版本">{{ node.nodeInfo.kubeletVersion || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Kube Proxy 版本">{{ node.nodeInfo.kubeProxyVersion || '-' }}</n-descriptions-item>
            <n-descriptions-item label="操作系统">{{ node.nodeInfo.operatingSystem || '-' }}</n-descriptions-item>
            <n-descriptions-item label="架构">{{ node.nodeInfo.architecture || '-' }}</n-descriptions-item>
          </n-descriptions>
        </n-tab-pane>
        <n-tab-pane name="images" tab="镜像">
          <n-data-table v-if="node.images?.length" :columns="imageColumns" :data="node.images" :bordered="false" :max-height="400" />
          <n-empty v-else description="无镜像信息" />
        </n-tab-pane>
        <n-tab-pane name="monitor" tab="监控">
          <NodeMonitorView :clusterId :name />
        </n-tab-pane>
        <n-tab-pane name="capacity" tab="资源容量">
          <n-descriptions label-placement="left" :column="1" v-if="node.capacity">
            <n-descriptions-item v-for="(v, k) in node.capacity" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无容量信息" />
        </n-tab-pane>
        <n-tab-pane name="allocatable" tab="可分配">
          <n-descriptions label-placement="left" :column="1" v-if="node.allocatable">
            <n-descriptions-item v-for="(v, k) in node.allocatable" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无可分配信息" />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<style scoped>
.node-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}
</style>