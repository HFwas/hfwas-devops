<template>
  <div v-if="loading" style="text-align: center; padding: 60px">
    <n-spin size="large" />
  </div>
  <div v-else>
    <n-page-header>
      <template #title>
        <n-button text @click="goBack">
          <template #icon><n-icon><ArrowLeft /></n-icon></template>
        </n-button>
        {{ repoName }}
      </template>
    </n-page-header>

    <n-data-table
      :columns="columns"
      :data="artifacts"
      :bordered="false"
      :single-line="false"
      size="small"
      style="margin-top: 16px"
    />

    <DeployFromImageDialog
      v-model:show="showDeployDialog"
      :registry-id="registryId"
      :image="selectedImage"
      @deployed="handleDeployed"
    />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'
import { NButton, NIcon, useMessage, useDialog } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import type { ArtifactVO } from '../../types/registry'
import { imageApi } from '../../api/image'
import ScanSeverityBadge from '../../components/Registry/ScanSeverityBadge.vue'
import DeployFromImageDialog from '../../components/Registry/DeployFromImageDialog.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const registryId = route.params.registryId as string
const projectName = route.params.project as string
const repoName = route.params.repo as string

const artifacts = ref<ArtifactVO[]>([])
const loading = ref(true)
const showDeployDialog = ref(false)
const selectedImage = ref('')

const columns: DataTableColumn[] = [
  {
    title: 'Digest',
    key: 'digest',
    width: 120,
    ellipsis: { tooltip: true },
  },
  {
    title: 'Tag',
    key: 'tags',
    width: 150,
    render: (row) => {
      const r = row as unknown as ArtifactVO
      return r.tags?.map(t => t.name).join(', ') || '-'
    },
  },
  {
    title: '大小',
    key: 'size',
    width: 100,
  },
  {
    title: '推送时间',
    key: 'tags',
    width: 180,
    render: (row) => {
      const r = row as unknown as ArtifactVO
      return r.tags?.[0]?.pushTime || '-'
    },
  },
  {
    title: '扫描',
    key: 'scanOverview',
    width: 100,
    render: (row) => {
      const r = row as unknown as ArtifactVO
      if (!r.scanOverview) return '-'
      return h(ScanSeverityBadge, { severity: r.scanOverview.severity })
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row) => {
      const r = row as unknown as ArtifactVO
      const tag = r.tags?.[0]?.name
      return h('div', { style: 'display: flex; gap: 8px' }, [
        tag
          ? h(NButton, {
              size: 'small',
              type: 'primary',
              onClick: () => {
                selectedImage.value = `${repoName}:${tag}`
                showDeployDialog.value = true
              },
            }, () => '部署')
          : null,
        h(NButton, {
          size: 'small',
          onClick: () => confirmDelete(r),
        }, () => '删除'),
      ])
    },
  },
]

onMounted(async () => {
  try {
    artifacts.value = await imageApi.listArtifacts(registryId, projectName, repoName) || []
  } catch (e: any) {
    message.error(e.message || '加载制品列表失败')
  } finally {
    loading.value = false
  }
})

function goBack() {
  router.push(`/container/registries/${registryId}`)
}

function confirmDelete(row: ArtifactVO) {
  const tagName = row.tags?.[0]?.name || row.digest.substring(0, 16)
  dialog.warning({
    title: '确认删除',
    content: `确定删除制品「${tagName}」？此操作不可撤销。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await imageApi.deleteArtifact(registryId, projectName, repoName, row.digest)
        message.success('删除成功')
        artifacts.value = artifacts.value.filter(a => a.digest !== row.digest)
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

function handleDeployed() {
  showDeployDialog.value = false
  message.success('部署成功')
}
</script>