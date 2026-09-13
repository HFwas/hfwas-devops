<script setup lang="ts">
import { KeyRound, Plus } from '@lucide/vue'
import { useDialog, useMessage } from 'naive-ui'
import { pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import { formatDateTime } from '@/modules/pipeline/status'
import type { CredentialKind, PipelineCredential } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import { useAuthStore } from '@/modules/user/stores/auth'
import '@/modules/pipeline/styles/pipeline-theme.css'

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

function kindLabel(kind: string): string {
  if (kind === 'TOKEN') return 'Token'
  if (kind === 'PASSWORD') return '用户名密码'
  if (kind === 'KUBECONFIG') return 'Kubeconfig'
  return kind
}

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
  <div class="pl-page">
    <header class="pl-hero">
      <div class="pl-hero-main">
        <h1 class="pl-hero-title">凭证</h1>
        <p class="pl-hero-desc">Git、Kubernetes 等平台使用的凭证，密钥不会回传</p>
      </div>
      <div class="pl-hero-extra">
        <n-button type="primary" @click="openCreate">
          <template #icon><Plus :size="14" /></template>
          新建凭证
        </n-button>
      </div>
    </header>

    <n-spin :show="loading">
      <n-empty v-if="!loading && rows.length === 0" description="暂无凭证" />
      <div v-else class="pl-grid">
        <article v-for="row in rows" :key="String(row.id)" class="pl-tile">
          <div class="pl-tile-top">
            <span class="pl-tile-icon" :class="row.kind === 'TOKEN' ? 'tone-token' : row.kind === 'KUBECONFIG' ? 'tone-kubeconfig' : 'tone-password'">
              <KeyRound :size="18" />
            </span>
            <n-tag size="small" :bordered="false">{{ kindLabel(row.kind) }}</n-tag>
          </div>
          <div class="pl-tile-body">
            <div class="pl-tile-name">{{ row.name }}</div>
            <div class="pl-tile-meta">
              <span>{{ row.username || '—' }}</span>
              <span>密钥 ••••</span>
            </div>
          </div>
          <div class="pl-tile-actions">
            <span>{{ formatDateTime(row.updateTime) }}</span>
            <n-button size="tiny" text type="error" style="margin-left: auto" @click="confirmDelete(row)">
              删除
            </n-button>
          </div>
        </article>
      </div>
    </n-spin>
  </div>

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
            { label: 'Kubeconfig', value: 'KUBECONFIG' },
          ]"
        />
      </n-form-item>
      <template v-if="form.kind === 'KUBECONFIG'">
        <n-form-item label="Kubeconfig YAML">
          <n-input v-model:value="form.secret" type="textarea" :rows="10" placeholder="粘贴 kubeconfig YAML 内容" />
        </n-form-item>
      </template>
      <template v-else>
        <n-form-item :label="form.kind === 'TOKEN' ? '用户名（可空，默认 x-access-token）' : '用户名'">
          <n-input v-model:value="form.username" />
        </n-form-item>
        <n-form-item :label="form.kind === 'TOKEN' ? 'Token' : '密码'">
          <n-input v-model:value="form.secret" type="password" show-password-on="mousedown" />
        </n-form-item>
      </template>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showModal = false">取消</n-button>
        <n-button type="primary" @click="save">确定</n-button>
      </n-space>
    </template>
  </n-modal>
</template>
