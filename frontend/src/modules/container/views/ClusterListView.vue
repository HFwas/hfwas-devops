<script setup lang="ts">
import { Plus, RefreshCw } from '@lucide/vue'
import { NButton, NCard, NDataTable, NModal, NInput, NForm, NFormItem, NInputGroup, NInputGroupLabel, NSelect, NSpace, NTag, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns, FormInst, FormRules } from 'naive-ui'
import { clusterApi } from '@/modules/container/api/cluster'
import type { ClusterSaveDTO, ClusterVO } from '@/modules/container/types/cluster'
import { useClusterStore } from '@/modules/container/stores/cluster'
import { isApiError } from '@/shared/errors/apiError'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const clusterStore = useClusterStore()

const loading = ref(false)
const rows = ref<ClusterVO[]>([])
const pagination = usePagination({ pageSize: 20 })
const showModal = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInst | null>(null)

const form = reactive<ClusterSaveDTO>({
  name: '',
  alias: '',
  provider: '',
  kubeconfig: '',
  mode: 'proxy',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入集群名称', trigger: 'blur' }],
  kubeconfig: [{ required: true, message: '请输入 kubeconfig 内容', trigger: 'blur' }],
}

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    const page = await clusterApi.page({ ...pagination.query.value })
    rows.value = page.records ?? []
    pagination.setTotal(page.total)
    clusterStore.clusterList = rows.value
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  modalMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.alias = ''
  form.provider = ''
  form.kubeconfig = ''
  form.mode = 'proxy'
  form.labels = undefined
  showModal.value = true
}

function openEdit(row: ClusterVO) {
  modalMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.alias = row.alias || ''
  form.provider = row.provider || ''
  form.kubeconfig = ''
  form.mode = row.mode as 'proxy' | 'direct'
  showModal.value = true
}

function doUpdateKubeconfig() {
  const value = prompt('粘贴新的 kubeconfig')
  if (value) form.kubeconfig = value
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  try {
    if (modalMode.value === 'create') {
      await clusterApi.create({ ...form })
      message.success('集群创建成功')
    } else if (editingId.value) {
      await clusterApi.update(editingId.value, {
        alias: form.alias || undefined,
        provider: form.provider || undefined,
        kubeconfig: form.kubeconfig || undefined,
        labels: form.labels,
      })
      message.success('集群已更新')
    }
    showModal.value = false
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function testConnection(row: ClusterVO) {
  try {
    const ok = await clusterApi.test(row.id)
    message.success(ok ? '连接成功' : '连接失败')
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

function confirmDelete(row: ClusterVO) {
  dialog.warning({
    title: '删除集群',
    content: `确认删除「${row.alias || row.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await clusterApi.delete(row.id)
      message.success('已删除')
      await load()
    },
  })
}

function openDetail(row: ClusterVO) {
  clusterStore.setCurrent(row)
  router.push(`/container/clusters/${row.id}`)
}

const statusTagType = (s: string) => {
  switch (s) {
    case 'Connected': return 'success' as const
    case 'Degraded': return 'warning' as const
    case 'Disconnected': return 'error' as const
    default: return 'default' as const
  }
}

const columns: DataTableColumns<ClusterVO> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true },
    render: (row) => h('button', { type: 'button', class: 'link-btn', onClick: () => openDetail(row) }, row.alias || row.name) },
  { title: '提供商', key: 'provider', width: 120 },
  { title: '版本', key: 'version', width: 100 },
  { title: '模式', key: 'mode', width: 80 },
  { title: '状态', key: 'status', width: 110,
    render: (row) => h(NTag, { type: statusTagType(row.status), size: 'small' }, () => row.status) },
  { title: '操作', key: 'actions', width: 200,
    render: (row) => h(NSpace, { size: 'small' }, () => [
      h(NButton, { size: 'tiny', quaternary: true, onClick: () => testConnection(row) }, () => '测试连接'),
      h(NButton, { size: 'tiny', quaternary: true, onClick: () => openEdit(row) }, () => '编辑'),
      h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => confirmDelete(row) }, () => '删除'),
    ]) },
]

onMounted(load)
</script>

<template>
  <div class="cluster-list-page">
    <n-card title="集群管理" :bordered="false">
      <template #header-extra>
        <n-space>
          <n-button quaternary @click="load">
            <template #icon><RefreshCw :size="16" /></template>
            刷新
          </n-button>
          <n-button type="primary" @click="openCreate">
            <template #icon><Plus :size="16" /></template>
            注册集群
          </n-button>
        </n-space>
      </template>
      <n-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        :bordered="false"
        :row-key="(row: ClusterVO) => row.id"
        @update:page="pagination.onPageChange($event); load()"
      />
      <div class="pagination-wrap">
        <AppPagination
          :pagination="pagination"
          @change="load"
        />
      </div>
    </n-card>

    <n-modal v-model:show="showModal" :title="modalMode === 'create' ? '注册集群' : '编辑集群'" preset="card" style="width: 600px">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
        <n-form-item label="集群名称" path="name">
          <n-input v-model:value="form.name" placeholder="必填，如 prod-cluster" :disabled="modalMode === 'edit'" />
        </n-form-item>
        <n-form-item label="别名(可选)" path="alias">
          <n-input v-model:value="form.alias" placeholder="友好名称" />
        </n-form-item>
        <n-form-item label="提供商" path="provider">
          <n-input v-model:value="form.provider" placeholder="如 self-hosted /阿里云/ AWS" />
        </n-form-item>
        <n-form-item label="连接模式" path="mode">
          <n-select v-model:value="form.mode" :options="[{ label: '代理 (proxy)', value: 'proxy' }, { label: '直连 (direct)', value: 'direct' }]" />
        </n-form-item>
        <n-form-item label="Kubeconfig" path="kubeconfig" v-if="modalMode === 'create' || form.kubeconfig">
          <n-input v-model:value="form.kubeconfig" type="textarea" :rows="6" placeholder="集群 kubeconfig YAML/JSON 内容" />
        </n-form-item>
        <n-form-item v-if="modalMode === 'edit'">
          <n-button quaternary size="tiny" @click="doUpdateKubeconfig">更换 kubeconfig</n-button>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" @click="submit">{{ modalMode === 'create' ? '创建' : '保存' }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.cluster-list-page {
  max-width: 1200px;
  margin: 0 auto;
}
.link-btn {
  background: none;
  border: none;
  color: var(--n-primary-color);
  cursor: pointer;
  padding: 0;
  font-size: inherit;
}
.link-btn:hover {
  text-decoration: underline;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>