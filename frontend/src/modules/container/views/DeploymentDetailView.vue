<script setup lang="ts">
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import {
  NButton, NCard, NDescriptions, NDescriptionsItem, NModal, NInputNumber,
  NEmpty, NSpace, NSpin, NTabPane, NTabs, NTag, NDataTable,
  useDialog, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { deploymentApi } from '@/modules/container/api/deployment'
import { eventApi } from '@/modules/container/api/event'
import type { DeploymentDetail, ContainerResource, VolumeMount, ContainerPort, EventInfo } from '@/modules/container/types/resource'
import ResourceYamlEditor from '@/modules/container/components/ResourceYamlEditor.vue'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string; namespace: string; name: string }>()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const deployment = ref<DeploymentDetail | null>(null)
const loading = ref(false)
const yamlContent = ref('')
const yamlLoading = ref(false)
const saving = ref(false)
const events = ref<EventInfo[]>([])
const eventsLoading = ref(false)

const showScaleModal = ref(false)
const scaleReplicas = ref(1)

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    deployment.value = await deploymentApi.get(props.clusterId, props.namespace, props.name)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadYaml() {
  yamlLoading.value = true
  try {
    yamlContent.value = await deploymentApi.yaml(props.clusterId, props.namespace, props.name)
  } catch (e) {
    yamlContent.value = `获取 YAML 失败: ${errorMessage(e)}`
  } finally {
    yamlLoading.value = false
  }
}

async function saveYaml(yaml: string) {
  saving.value = true
  try {
    await deploymentApi.updateYaml(props.clusterId, props.namespace, props.name, yaml)
    message.success('已保存')
    yamlContent.value = yaml
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

async function loadEvents() {
  if (!deployment.value?.uid) return
  eventsLoading.value = true
  try {
    events.value = await eventApi.list(props.clusterId, props.namespace, deployment.value.uid)
  } catch (e) {
    // silent
  } finally {
    eventsLoading.value = false
  }
}

function openScale() {
  if (!deployment.value) return
  scaleReplicas.value = deployment.value.desiredReplicas
  showScaleModal.value = true
}

async function submitScale() {
  try {
    await deploymentApi.scale(props.clusterId, props.namespace, props.name, scaleReplicas.value)
    message.success(`已缩放到 ${scaleReplicas.value} 副本`)
    showScaleModal.value = false
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function restartDeployment() {
  try {
    await deploymentApi.restart(props.clusterId, props.namespace, props.name)
    message.success('已触发滚动重启')
  } catch (e) {
    message.error(errorMessage(e))
  }
}

function confirmDelete() {
  dialog.warning({
    title: '删除 Deployment',
    content: `确认删除「${props.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deploymentApi.delete(props.clusterId, props.namespace, props.name)
        message.success('已删除')
        router.back()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const containerColumns: DataTableColumns<ContainerResource> = [
  { title: '名称', key: 'name' },
  { title: '镜像', key: 'image', ellipsis: { tooltip: true } },
  { title: 'CPU 请求', key: 'cpuRequest', width: 100 },
  { title: 'CPU 限制', key: 'cpuLimit', width: 100 },
  { title: '内存请求', key: 'memRequest', width: 110 },
  { title: '内存限制', key: 'memLimit', width: 110 },
]

const volumeColumns: DataTableColumns<VolumeMount> = [
  { title: '名称', key: 'name' },
  { title: '类型', key: 'volumeType', width: 100 },
]

const portColumns: DataTableColumns<ContainerPort> = [
  { title: '名称', key: 'name' },
  { title: '容器端口', key: 'containerPort', width: 100 },
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
  <div class="deploy-detail-page">
    <n-space style="margin-bottom: 16px">
      <n-button quaternary @click="router.push(`/container/clusters/${clusterId}/deployments`)">
        <template #icon><ArrowLeft :size="16" /></template>
        返回 Deployment 列表
      </n-button>
      <n-button quaternary @click="load">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </n-button>
    </n-space>

    <n-card :bordered="false" v-if="deployment">
      <template #header>
        <n-space align="center">
          <span style="font-weight: 600; font-size: 15px">{{ deployment.name }}</span>
          <n-tag size="small" :bordered="false" type="default">{{ deployment.namespace }}</n-tag>
          <n-tag
            size="small"
            :type="deployment.status?.includes('ready') || deployment.readyReplicas > 0 ? 'success' : 'warning'"
          >
            {{ deployment.readyReplicas }}/{{ deployment.desiredReplicas }} 就绪
          </n-tag>
          <div style="flex: 1" />
          <n-button size="small" quaternary @click="openScale">扩缩容</n-button>
          <n-button size="small" quaternary @click="restartDeployment">重启</n-button>
          <n-button size="small" quaternary type="error" @click="confirmDelete">删除</n-button>
        </n-space>
      </template>

      <n-tabs type="line" animated @update:value="(tab) => { if (tab === 'events') loadEvents() }">
        <n-tab-pane name="overview" tab="概览">
          <n-descriptions label-placement="left" :column="2" bordered size="small">
            <n-descriptions-item label="Namespace">{{ deployment.namespace }}</n-descriptions-item>
            <n-descriptions-item label="策略">{{ deployment.strategy || '-' }}</n-descriptions-item>
            <n-descriptions-item label="副本">
              {{ deployment.readyReplicas }}/{{ deployment.desiredReplicas }} 就绪
              (可用: {{ deployment.availableReplicas }})
            </n-descriptions-item>
            <n-descriptions-item label="镜像" :span="2">{{ deployment.image || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Selector">{{ deployment.selector || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Age">{{ deployment.age }}</n-descriptions-item>
            <n-descriptions-item label="Revision History Limit">{{ deployment.revisionHistoryLimit || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Min Ready Seconds">{{ deployment.minReadySeconds || '-' }}</n-descriptions-item>
            <n-descriptions-item label="状态" :span="2">{{ deployment.status || '-' }}</n-descriptions-item>
          </n-descriptions>

          <h3 class="section-title">容器</h3>
          <n-data-table
            :columns="containerColumns"
            :data="deployment.containers || []"
            :bordered="false"
            :max-height="300"
            size="small"
          />

          <template v-if="deployment.containers?.some(c => c.ports?.length)">
            <h3 class="section-title">容器端口</h3>
            <n-data-table
              :columns="portColumns"
              :data="(deployment.containers || []).flatMap(c => (c.ports || []).map(p => ({ ...p, containerName: c.name })))"
              :bordered="false"
              :max-height="200"
              size="small"
            />
          </template>

          <template v-if="deployment.volumes?.length">
            <h3 class="section-title">存储卷</h3>
            <n-data-table
              :columns="volumeColumns"
              :data="deployment.volumes"
              :bordered="false"
              :max-height="200"
              size="small"
            />
          </template>
        </n-tab-pane>

        <n-tab-pane name="yaml" tab="YAML">
          <ResourceYamlEditor
            :content="yamlContent"
            :loading="yamlLoading || saving"
            :editable="true"
            max-height="calc(100vh - 260px)"
            @save="saveYaml"
          />
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
          <n-descriptions label-placement="left" :column="1" bordered size="small" v-if="deployment.labels && Object.keys(deployment.labels).length">
            <n-descriptions-item v-for="(v, k) in deployment.labels" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无标签" />
        </n-tab-pane>

        <n-tab-pane name="annotations" tab="注解">
          <n-descriptions label-placement="left" :column="1" bordered size="small" v-if="deployment.annotations && Object.keys(deployment.annotations).length">
            <n-descriptions-item v-for="(v, k) in deployment.annotations" :key="k" :label="k">{{ v }}</n-descriptions-item>
          </n-descriptions>
          <n-empty v-else description="无注解" />
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <n-modal v-model:show="showScaleModal" title="扩缩容" preset="card" style="width: 400px">
      <p>当前副本: <strong>{{ deployment?.desiredReplicas }}</strong></p>
      <n-input-number v-model:value="scaleReplicas" :min="0" :max="100" style="width: 100%; margin-top: 12px" />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showScaleModal = false">取消</n-button>
          <n-button type="primary" @click="submitScale">确认</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.deploy-detail-page {
  max-width: 1200px;
  margin: 0 auto;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--wb-ink, #1f2329);
}
</style>