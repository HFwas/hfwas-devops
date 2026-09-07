<script setup lang="ts">
import { h } from 'vue'
import { NButton, NSpace, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import type { CredentialKind, PipelineCredential } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import { useAuthStore } from '@/modules/user/stores/auth'

const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const loading = ref(false)
const rows = ref<PipelineCredential[]>([])
const showModal = ref(false)
const form = ref({
  name: '',
  kind: 'TOKEN' as CredentialKind,
  username: '',
  secret: '',
})

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

const columns: DataTableColumns<PipelineCredential> = [
  { title: '名称', key: 'name' },
  {
    title: '类型',
    key: 'kind',
    render: (row) => (row.kind === 'TOKEN' ? 'Token' : '用户名密码'),
  },
  { title: '用户名', key: 'username', render: (row) => row.username || '—' },
  { title: '密钥', key: 'secret', render: () => '••••' },
  { title: '更新时间', key: 'updateTime', render: (row) => formatTime(row.updateTime) },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row) =>
      h(NSpace, () => [
        h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => confirmDelete(row) }, () => '删除'),
      ]),
  },
]

async function load() {
  loading.value = true
  try {
    rows.value = await pipelineCredentialApi.list()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = { name: '', kind: 'TOKEN', username: '', secret: '' }
  showModal.value = true
}

async function save() {
  if (!form.value.name.trim()) {
    message.warning('凭证名称不能为空')
    return
  }
  if (!form.value.secret.trim()) {
    message.warning('密钥不能为空')
    return
  }
  try {
    await pipelineCredentialApi.save({
      name: form.value.name.trim(),
      kind: form.value.kind,
      username: form.value.username.trim() || undefined,
      secret: form.value.secret,
    })
    message.success('已保存')
    showModal.value = false
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

function confirmDelete(row: PipelineCredential) {
  dialog.warning({
    title: '删除凭证',
    content: `确认删除「${row.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await pipelineCredentialApi.delete(row.id)
        message.success('已删除')
        await load()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

onMounted(load)
watch(
  () => [auth.activeTenantId, auth.tenantVersion] as const,
  () => void load(),
)
</script>

<template>
  <n-space vertical size="large" style="padding: 20px 24px 28px">
    <n-page-header title="凭证" subtitle="GitHub HTTPS 克隆使用的用户名密码或 Token，密钥不会回传">
      <template #extra>
        <n-button type="primary" @click="openCreate">新建</n-button>
      </template>
    </n-page-header>
    <n-data-table :columns="columns" :data="rows" :loading="loading" :bordered="false" />
  </n-space>

  <n-modal v-model:show="showModal" preset="card" title="新建凭证" style="width: 480px">
    <n-form label-placement="top">
      <n-form-item label="名称">
        <n-input v-model:value="form.name" placeholder="例如 github-pat" />
      </n-form-item>
      <n-form-item label="类型">
        <n-select
          v-model:value="form.kind"
          :options="[
            { label: 'Token', value: 'TOKEN' },
            { label: '用户名密码', value: 'PASSWORD' },
          ]"
        />
      </n-form-item>
      <n-form-item :label="form.kind === 'TOKEN' ? '用户名（可空，默认 x-access-token）' : '用户名'">
        <n-input v-model:value="form.username" />
      </n-form-item>
      <n-form-item :label="form.kind === 'TOKEN' ? 'Token' : '密码'">
        <n-input v-model:value="form.secret" type="password" show-password-on="mousedown" />
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showModal = false">取消</n-button>
        <n-button type="primary" @click="save">确定</n-button>
      </n-space>
    </template>
  </n-modal>
</template>
