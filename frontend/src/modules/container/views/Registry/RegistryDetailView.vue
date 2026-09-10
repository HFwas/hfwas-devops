<template>
  <div v-if="loading" style="text-align: center; padding: 60px">
    <n-spin size="large" />
  </div>
  <div v-else-if="registry">
    <n-page-header>
      <template #title>
        <n-button text @click="$router.push('/container/registries')">
          <template #icon><n-icon><ArrowLeft /></n-icon></template>
        </n-button>
        {{ registry.alias || registry.name }}
      </template>
      <template #header>
        <n-tag :bordered="false" size="small" style="margin-left: 12px">
          {{ registry.type === 'harbor' ? 'Harbor' : 'Registry V2' }}
        </n-tag>
        <RegistryStatusBadge :status="registry.status" />
        <n-tag v-if="registry.source === 'builtin'" size="small" type="info" :bordered="false" style="margin-left: 4px">
          内置
        </n-tag>
      </template>
      <template #extra>
        <n-button size="small" :loading="testing" @click="handleTest">测试连接</n-button>
      </template>
    </n-page-header>

    <n-card style="margin-top: 16px">
      <n-descriptions label-placement="left" :column="2">
        <n-descriptions-item label="地址">{{ registry.url }}</n-descriptions-item>
        <n-descriptions-item label="用户名">{{ registry.credentialUsername || '-' }}</n-descriptions-item>
        <n-descriptions-item label="跳过 TLS">{{ registry.insecure ? '是' : '否' }}</n-descriptions-item>
        <n-descriptions-item label="创建时间">{{ registry.createdAt }}</n-descriptions-item>
        <n-descriptions-item v-if="registry.lastError" label="错误信息" :span="2">
          <n-text type="error">{{ registry.lastError }}</n-text>
        </n-descriptions-item>
      </n-descriptions>
    </n-card>

    <n-tabs type="line" animated style="margin-top: 16px" default-value="projects">
      <n-tab-pane name="projects" tab="项目列表">
        <div v-if="loadingProjects" style="text-align: center; padding: 40px">
          <n-spin />
        </div>
        <div v-else>
          <n-data-table
            :columns="projectColumns"
            :data="projects"
            :bordered="false"
            :single-line="false"
            size="small"
          />
        </div>
      </n-tab-pane>
      <n-tab-pane name="scans" tab="扫描结果">
        <div v-if="loadingScans" style="text-align: center; padding: 40px">
          <n-spin />
        </div>
        <div v-else-if="scanResults.length === 0">
          <n-empty description="暂无扫描结果" />
        </div>
        <div v-else>
          <n-data-table
            :columns="scanColumns"
            :data="scanResults"
            :bordered="false"
            :single-line="false"
            size="small"
          />
        </div>
      </n-tab-pane>
    </n-tabs>
  </div>
  <div v-else>
    <n-result status="404" title="镜像仓库不存在" />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@lucide/vue'
import { NButton, NIcon, NText, useMessage } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import type { RegistryVO, RegistryProjectVO, ArtifactVO } from '../../types/registry'
import { registryApi } from '../../api/registry'
import { imageApi } from '../../api/image'
import RegistryStatusBadge from '../../components/Registry/RegistryStatusBadge.vue'
import ScanSeverityBadge from '../../components/Registry/ScanSeverityBadge.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const registryId = route.params.id as string
const registry = ref<RegistryVO | null>(null)
const loading = ref(true)
const testing = ref(false)

const projects = ref<RegistryProjectVO[]>([])
const loadingProjects = ref(false)

const scanResults = ref<ArtifactVO[]>([])
const loadingScans = ref(false)

const projectColumns: DataTableColumn[] = [
  {
    title: '项目名称',
    key: 'name',
    render: (row) => {
      const r = row as unknown as RegistryProjectVO
      return h('a', {
        style: 'cursor: pointer; color: var(--primary-color)',
        onClick: () => router.push(`/container/registries/${registryId}/projects/${r.name}/repos`),
      }, r.name)
    },
  },
  {
    title: '镜像数',
    key: 'repoCount',
    width: 100,
  },
  {
    title: '创建时间',
    key: 'creationTime',
    width: 200,
  },
  {
    title: '更新时间',
    key: 'updateTime',
    width: 200,
  },
]

const scanColumns: DataTableColumn[] = [
  {
    title: 'Digest',
    key: 'digest',
    width: 100,
    ellipsis: { tooltip: true },
  },
  {
    title: 'Tag',
    key: 'tags',
    width: 120,
    render: (row) => {
      const r = row as unknown as ArtifactVO
      return r.tags?.map(t => t.name).join(', ') || '-'
    },
  },
  {
    title: '状态',
    key: 'scanOverview',
    width: 100,
    render: (row) => {
      const r = row as unknown as ArtifactVO
      if (!r.scanOverview) return '-'
      return h(ScanSeverityBadge, { severity: r.scanOverview.severity })
    },
  },
  {
    title: '漏洞数',
    key: 'scanOverview',
    width: 200,
    render: (row) => {
      const r = row as unknown as ArtifactVO
      const s = r.scanOverview
      if (!s) return '-'
      return `严重:${s.critical || 0} 高危:${s.high || 0} 中:${s.medium || 0} 低:${s.low || 0}`
    },
  },
]

onMounted(async () => {
  try {
    registry.value = await registryApi.get(registryId)
    await loadProjects()
  } catch (e: any) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
})

async function loadProjects() {
  loadingProjects.value = true
  try {
    projects.value = await imageApi.listProjects(registryId) || []
  } catch (e: any) {
    message.error(e.message || '加载项目列表失败')
  } finally {
    loadingProjects.value = false
  }
}

async function handleTest() {
  testing.value = true
  try {
    const ok = await registryApi.test(registryId)
    message.success(ok ? '连接成功' : '连接失败')
  } catch (e: any) {
    message.error(e.message || '测试失败')
  } finally {
    testing.value = false
  }
}
</script>