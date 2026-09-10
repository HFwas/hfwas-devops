<script setup lang="ts">
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import {
  NButton, NCard, NDescriptions, NDescriptionsItem, NEmpty, NSpace,
  NTabPane, NTabs, NTag, NDataTable, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { serviceApi } from '@/modules/container/api/service'
import { eventApi } from '@/modules/container/api/event'
import type { ServiceDetail, ServicePortItem } from '@/modules/container/types/resource'
import type { EventInfo } from '@/modules/container/types/event'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string; namespace: string; name: string }>()
const router = useRouter()
const message = useMessage()

const service = ref<ServiceDetail | null>(null)
const loading = ref(false)
const yamlContent = ref('')
const yamlLoading = ref(false)
const events = ref<EventInfo[]>([])
const eventsLoading = ref(false)

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '加载失败'
}

async function load() {
  loading.value = true
  try {
    service.value = await serviceApi.get(props.clusterId, props.namespace, props.name)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadYaml() {
  yamlLoading.value = true
  try {
    yamlContent.value = await serviceApi.yaml(props.clusterId, props.namespace, props.name)
  } catch (e) {
    yamlContent.value = `获取 YAML 失败: ${errorMessage(e)}`
  } finally {
    yamlLoading.value = false
  }
}

async function loadEvents() {
  if (!service.value?.uid) return
  eventsLoading.value = true
  try {
    events.value = await eventApi.list(props.clusterId, props.namespace, service.value.uid)
  } catch (e) {
    // silent
  } finally {
    eventsLoading.value = false
  }
}

const typeTagType = (t?: string) => {
  switch (t) {
    case 'ClusterIP': return 'info' as const
    case 'NodePort': return 'warning' as const
    case 'LoadBalancer': return 'success' as const
    case 'ExternalName': return 'default' as const
    default: return 'default' as const
  }
}

const portColumns: DataTableColumns<ServicePortItem> = [
  { title: '名称', key: 'name' },
  { title: '端口', key: 'port', width: 80 },
  { title: '目标端口', key: 'targetPort', width: 100 },
  { title: 'NodePort', key: 'nodePort', width: 100 },
  { title: '协议', key: 'protocol', width: 80 },
]

const eventColumns: DataTableColumns<EventInfo> = [
  { title: '类型', key: 'type', width: 80,
    render: (row) => h(NTag, { type: row.type === 'Normal' ? 'info' : 'warning', size: 'small' }, () => row.type) },
  { title: '原因', key: 'reason', width: 120 },
  { title: '消息', key: 'message', ellipsis: { tooltip: true } },
  { title: '次数', key: 'count', width: 60 },
  { title: '最后时间', key: 'lastTimestamp', width: 170 },
]

onMounted(() => {
  load()
  loadYaml()
})
</script>

<template>
  <div class="service-detail-page">
    <n-space style="margin-bottom: 16px">
      <n-button quaternary @click="router.push(`/container/clusters/${clusterId}/services`)">
        <template #icon><ArrowLeft :size="16" /></template>
        返回 Service 列表
      </n-button>
      <n-button quaternary @click="load">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </n-button>
    </n-space>

    <n-card :bordered="false" v-if="service">
      <template #header>
        <n-space align="center">
          <span style="font-weight: 600; font-size: 15px">{{ service.name }}</span>
          <n-tag size="small" :bordered="false" type="default">{{ service.namespace }}</n-tag>
          <n-tag size="small" :type="typeTagType(service.type)">{{ service.type }}</n-tag>
        </n-space>
      </template>

      <n-tabs type="line" animated @update:value="(tab) => { if (tab === 'events') loadEvents() }">
        <n-tab-pane name="overview" tab="概览">
          <n-descriptions label-placement="left" :column="2" bordered size="small">
            <n-descriptions-item label="Namespace">{{ service.namespace }}</n-descriptions-item>
            <n-descriptions-item label="类型">{{ service.type }}</n-descriptions-item>
            <n-descriptions-item label="Cluster IP">{{ service.clusterIP || '-' }}</n-descriptions-item>
            <n-descriptions-item label="外部 IP">{{ service.externalIP || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Session Affinity">{{ service.sessionAffinity || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Age">{{ service.age }}</n-descriptions-item>
            <n-descriptions-item label="端口数">{{ service.portCount }}</n-descriptions-item>
          </n-descriptions>

          <template v-if="service.selector && Object.keys(service.selector).length">
            <h3 class="section-title">Selector</h3>
            <n-descriptions label-placement="left" :column="1" bordered size="small">
              <n-descriptions-item v-for="(v, k) in service.selector" :key="k" :label="k">{{ v }}</n-descriptions-item>
            </n-descriptions>
          </template>

          <template v-if="service.ports?.length">
            <h3 class="section-title">端口映射</h3>
            <n-data-table
              :columns="portColumns"
              :data="service.ports"
              :bordered="false"
              :max-height="300"
              size="small"
            />
          </template>
        </n-tab-pane>

        <n-tab-pane name="yaml" tab="YAML">
          <pre class="yaml-block">{{ yamlLoading ? '加载中...' : yamlContent }}</pre>
        </n-tab-pane>

        <n-tab-pane name="events" tab="事件">
          <n-data-table
            :columns="eventColumns"
            :data="events"
            :loading="eventsLoading"
            :bordered="false"
            :max-height="400"
            size="small"
          />
          <n-empty v-if="!eventsLoading && events.length === 0" description="暂无事件" style="padding: 24px" />
        </n-tab-pane>

        <n-tab-pane name="labels" tab="标签">
          <n-descriptions label-placement="left" :column="1" bordered size="small" v-if="service.labels && Object.keys(service.labels).length">
            <n-descriptions-item v-for="(v, k) in service.labels" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无标签" />
        </n-tab-pane>

        <n-tab-pane name="annotations" tab="注解">
          <n-descriptions label-placement="left" :column="1" bordered size="small" v-if="service.annotations && Object.keys(service.annotations).length">
            <n-descriptions-item v-for="(v, k) in service.annotations" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无注解" />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<style scoped>
.service-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--wb-ink, #1f2329);
}
.yaml-block {
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