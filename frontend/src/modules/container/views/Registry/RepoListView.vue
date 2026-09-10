<template>
  <div v-if="loading" style="text-align: center; padding: 60px">
    <n-spin size="large" />
  </div>
  <div v-else>
    <n-page-header>
      <template #title>
        <n-button text @click="$router.push(`/container/registries/${registryId}`)">
          <template #icon><n-icon><ArrowLeft /></n-icon></template>
        </n-button>
        {{ projectName }}
      </template>
      <template #header>
        <n-tag :bordered="false" size="small" type="info">镜像列表</n-tag>
      </template>
    </n-page-header>

    <n-data-table
      :columns="columns"
      :data="repos"
      :bordered="false"
      :single-line="false"
      size="small"
      style="margin-top: 16px"
    />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'
import { NButton, NIcon, useMessage } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import type { RegistryRepoVO } from '../../types/registry'
import { imageApi } from '../../api/image'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const registryId = route.params.registryId as string
const projectName = route.params.project as string

const repos = ref<RegistryRepoVO[]>([])
const loading = ref(true)

const columns: DataTableColumn[] = [
  {
    title: '镜像名称',
    key: 'name',
    render: (row) => {
      const r = row as unknown as RegistryRepoVO
      const shortName = r.name.replace(`${r.projectName}/`, '')
      return h('a', {
        style: 'cursor: pointer; color: var(--primary-color)',
        onClick: () => router.push(`/container/registries/${registryId}/projects/${projectName}/repos/${encodeURIComponent(shortName)}`),
      }, shortName)
    },
  },
  {
    title: 'Tag 数',
    key: 'artifactCount',
    width: 100,
  },
  {
    title: '拉取次数',
    key: 'pullCount',
    width: 100,
  },
  {
    title: '更新时间',
    key: 'updateTime',
    width: 200,
  },
]

onMounted(async () => {
  try {
    repos.value = await imageApi.listRepositories(registryId, projectName) || []
  } catch (e: any) {
    message.error(e.message || '加载镜像列表失败')
  } finally {
    loading.value = false
  }
})
</script>