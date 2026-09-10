<script setup lang="ts">
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import { NCard, NButton, NSpace, NGrid, NGi, NStatistic, NDataTable, NTag, NDescriptions, NDescriptionsItem, useMessage } from 'naive-ui'
import { clusterApi } from '@/modules/container/api/cluster'
import type { ClusterStatsVO, ClusterVO } from '@/modules/container/types/cluster'
import { useClusterStore } from '@/modules/container/stores/cluster'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ id: string }>()
const router = useRouter()
const message = useMessage()
const clusterStore = useClusterStore()

const cluster = ref<ClusterVO | null>(null)
const stats = ref<ClusterStatsVO | null>(null)
const loading = ref(false)

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

onMounted(load)
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
  </div>
</template>

<style scoped>
.cluster-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}
</style>