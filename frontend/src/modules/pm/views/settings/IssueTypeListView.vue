<script setup lang="ts">
import { useMessage } from 'naive-ui'
import PmIssueTypeSchemeImportModal from '@/modules/pm/components/PmIssueTypeSchemeImportModal/index.vue'
import { pmIssueTypeSchemeApi, pmMetaApi } from '@/modules/pm/api'
import type { PmWorkItemType } from '@/modules/pm/types'
import { TYPE_META } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'
import { downloadJsonFile, projectSchemeExportFilename } from '@/modules/pm/utils/jsonFile'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const projectId = computed(() => routeId(route.params.projectId))
const types = ref<PmWorkItemType[]>([])
const loading = ref(false)
const exporting = ref(false)
const showImport = ref(false)

async function load() {
  loading.value = true
  try {
    types.value = await pmMetaApi.types()
  } finally {
    loading.value = false
  }
}

function openType(typeCode: string) {
  router.push(`/pm/projects/${projectId.value}/settings/types/${typeCode}`)
}

async function exportProjectScheme() {
  exporting.value = true
  try {
    const data = await pmIssueTypeSchemeApi.exportProject(projectId.value)
    downloadJsonFile(data, projectSchemeExportFilename())
    message.success('已导出项目事项类型方案（含字段与状态流转）')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="事项配置"
      subtitle="按类型管理字段布局与状态流转；支持导入导出完整方案"
    >
      <template #extra>
        <n-space>
          <n-button :loading="exporting" @click="exportProjectScheme">导出方案</n-button>
          <n-button type="primary" @click="showImport = true">导入方案</n-button>
        </n-space>
      </template>
    </n-page-header>

    <n-spin :show="loading">
      <n-grid :cols="2" :x-gap="16" :y-gap="16">
        <n-gi v-for="t in types" :key="t.code">
          <n-card hoverable @click="openType(t.code)">
            <n-space align="center" justify="space-between">
              <n-space align="center">
                <n-tag :bordered="false" :color="{ color: TYPE_META[t.code]?.color, textColor: '#fff' }">
                  {{ TYPE_META[t.code]?.label ?? t.name }}
                </n-tag>
                <n-text depth="3">{{ t.code }}</n-text>
              </n-space>
              <n-space>
                <n-button text type="primary" @click.stop="router.push(`/pm/projects/${projectId}/settings/workflow/${t.code}`)">
                  状态流转
                </n-button>
                <n-button text type="primary" @click.stop="openType(t.code)">字段布局 →</n-button>
              </n-space>
            </n-space>
            <n-text depth="3" style="display: block; margin-top: 12px">
              配置该事项的字段展示方案与状态流转规则
            </n-text>
          </n-card>
        </n-gi>
      </n-grid>
    </n-spin>

    <PmIssueTypeSchemeImportModal
      v-model:show="showImport"
      :project-id="projectId"
      @imported="load"
    />
  </n-space>
</template>
