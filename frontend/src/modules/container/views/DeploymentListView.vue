<script setup lang="ts">
import { RefreshCw, RotateCcw, Trash2 } from '@lucide/vue'
import { NButton, NCard, NDataTable, NInput, NSpace, NTag, NModal, NInputNumber, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { deploymentApi } from '@/modules/container/api/deployment'
import type { DeploymentSummary } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'

const props = defineProps<{ clusterId: string }>()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const rows = ref<DeploymentSummary[]>([])
const keyword = ref('')
const pagination = usePagination({ pageSize: 20 })
const showScaleModal = ref(false)
const scaleDeploy = ref<DeploymentSummary | null>(null)
const scaleReplicas = ref(1)

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    const page = await deploymentApi.list(Number(props.clusterId), {
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
    })
    rows.value = page.records ?? []
    pagination.setTotal(page.total)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pagination.resetPage()
  load()
}

function openScale(row: DeploymentSummary) {
  scaleDeploy.value = row
  scaleReplicas.value = row.desiredReplicas
  showScaleModal.value = true
}

async function submitScale() {
  if (!scaleDeploy.value) return
  try {
    await deploymentApi.scale(Number(props.clusterId), scaleDeploy.value.namespace, scaleDeploy.value.name, scaleReplicas.value)
    message.success(`已缩放到 ${scaleReplicas.value} 副本`)
    showScaleModal.value = false
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function restartDeploy(row: DeploymentSummary) {
  try {
    await deploymentApi.restart(Number(props.clusterId), row.namespace, row.name)
    message.success('已触发滚动重启')
  } catch (e) {
    message.error(errorMessage(e))
  }
}

function confirmDelete(row: DeploymentSummary) {
  dialog.warning({
    title: '删除 Deployment',
    content: `确认删除「${row.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deploymentApi.delete(Number(props.clusterId), row.namespace, row.name)
        message.success('已删除')
        await load()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const columns: DataTableColumns<DeploymentSummary> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  { title: 'Namespace', key: 'namespace', width: 140 },
  { title: '副本', key: 'readyReplicas', width: 100,
    render: (row) => `${row.readyReplicas}/${row.desiredReplicas} 就绪` },
  { title: '可用', key: 'availableReplicas', width: 80,
    render: (row) => `${row.availableReplicas}` },
  { title: '策略', key: 'strategy', width: 100 },
  { title: '年龄', key: 'age', width: 80 },
  { title: '操作', key: 'actions', width: 220,
    render: (row) => h(NSpace, { size: 'small' }, () => [
      h(NButton, { size: 'tiny', quaternary: true, onClick: () => openScale(row) }, () => '扩缩容'),
      h(NButton, { size: 'tiny', quaternary: true, onClick: () => restartDeploy(row) }, () => '重启'),
      h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => confirmDelete(row) }, () => '删除'),
    ]) },
]

onMounted(load)
</script>

<template>
  <div class="deploy-list-page">
    <n-card :bordered="false">
      <template #header>
        <n-space align="center">
          <span>Deployment 列表</span>
          <n-input v-model:value="keyword" placeholder="搜索" clearable style="width: 240px" @keyup.enter="onSearch" />
          <n-button quaternary @click="onSearch">搜索</n-button>
          <n-button quaternary @click="load">
            <template #icon><RefreshCw :size="16" /></template>
            刷新
          </n-button>
        </n-space>
      </template>
      <n-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        :bordered="false"
        :row-key="(row: DeploymentSummary) => `${row.namespace}/${row.name}`"
      />
      <div class="pagination-wrap">
        <AppPagination
          :pagination="pagination"
          @change="load"
        />
      </div>
    </n-card>

    <n-modal v-model:show="showScaleModal" title="扩缩容" preset="card" style="width: 400px">
      <div v-if="scaleDeploy">
        <p>Deployment: <strong>{{ scaleDeploy.name }}</strong></p>
        <p>当前副本: {{ scaleDeploy.desiredReplicas }}</p>
        <n-input-number v-model:value="scaleReplicas" :min="0" :max="100" />
      </div>
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
.deploy-list-page {
  max-width: 1200px;
  margin: 0 auto;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>