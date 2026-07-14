<script setup lang="ts">
import { h } from 'vue'
import type { DataTableColumns } from 'naive-ui'
import { NButton, NTag, useMessage } from 'naive-ui'
import PmIssueTypeSchemeImportModal from '@/modules/pm/components/PmIssueTypeSchemeImportModal/index.vue'
import { pmIssueTypeSchemeApi, pmMetaApi, pmProjectIssueTypeApi } from '@/modules/pm/api'
import {
  invalidateIssueTypeCaches,
  useGlobalIssueTypes,
  useProjectIssueTypes,
} from '@/modules/pm/composables/useIssueTypes'
import type { PmWorkItemType } from '@/modules/pm/types'
import { typeColor } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'
import { downloadJsonFile, projectSchemeExportFilename } from '@/modules/pm/utils/jsonFile'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const projectId = computed(() => routeId(route.params.projectId))
const { types: projectTypes, loading, load: loadProjectTypes } = useProjectIssueTypes(projectId)
const { types: globalTypes, load: loadGlobalTypes } = useGlobalIssueTypes(true)

const keyword = ref('')
const exporting = ref(false)
const showImport = ref(false)
const showSchemeModal = ref(false)
const showTypeModal = ref(false)
const savingScheme = ref(false)
const savingType = ref(false)
const schemeCodes = ref<string[]>([])
const editingType = ref<PmWorkItemType | null>(null)
const typeForm = ref({
  code: '',
  name: '',
  color: '#2080f0',
  enabled: true,
})

const enabledGlobalOptions = computed(() =>
  globalTypes.value
    .filter((t) => t.enabled !== 0)
    .map((t) => ({ label: `${t.name} (${t.code})`, value: t.code })),
)

const filteredTypes = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return projectTypes.value
  return projectTypes.value.filter(
    (t) => t.name.toLowerCase().includes(kw) || t.code.toLowerCase().includes(kw),
  )
})

const columns = computed<DataTableColumns<PmWorkItemType>>(() => [
  {
    title: '事项类型',
    key: 'name',
    render: (row) =>
      h('div', { class: 'type-name-cell' }, [
        h(
          NTag,
          {
            bordered: false,
            size: 'small',
            color: { color: typeColor(row.code, projectTypes.value), textColor: '#fff' },
          },
          () => row.name,
        ),
        row.enabled === 0
          ? h(NTag, { size: 'small', bordered: false, style: 'margin-left: 6px' }, () => '已停用')
          : null,
      ]),
  },
  {
    title: '编码',
    key: 'code',
    width: 160,
    render: (row) => h('span', { class: 'type-code' }, row.code),
  },
  {
    title: '排序',
    key: 'sortOrder',
    width: 80,
    render: (row) => row.sortOrder ?? '—',
  },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row) =>
      h('div', { class: 'row-actions' }, [
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            size: 'small',
            onClick: (e: Event) => {
              e.stopPropagation()
              openType(row.code)
            },
          },
          () => '配置',
        ),
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            size: 'small',
            onClick: (e: Event) => {
              e.stopPropagation()
              openEditType(row)
            },
          },
          () => '编辑',
        ),
      ]),
  },
])

async function reload() {
  await Promise.all([loadProjectTypes(true), loadGlobalTypes(true)])
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

function openSchemeModal() {
  schemeCodes.value = projectTypes.value.map((t) => t.code)
  if (!schemeCodes.value.length) {
    schemeCodes.value = globalTypes.value.filter((t) => t.enabled !== 0).map((t) => t.code)
  }
  showSchemeModal.value = true
}

async function saveScheme() {
  if (!schemeCodes.value.length) {
    message.warning('至少启用一个事项类型')
    return
  }
  savingScheme.value = true
  try {
    await pmProjectIssueTypeApi.save(projectId.value, schemeCodes.value)
    invalidateIssueTypeCaches(projectId.value)
    message.success('项目启用类型已保存')
    showSchemeModal.value = false
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    savingScheme.value = false
  }
}

function openCreateType() {
  editingType.value = null
  typeForm.value = { code: '', name: '', color: '#2080f0', enabled: true }
  showTypeModal.value = true
}

function openEditType(t: PmWorkItemType) {
  editingType.value = t
  typeForm.value = {
    code: t.code,
    name: t.name,
    color: t.color || typeColor(t.code),
    enabled: t.enabled !== 0,
  }
  showTypeModal.value = true
}

async function saveTypeForm() {
  const name = typeForm.value.name.trim()
  const code = typeForm.value.code.trim().toLowerCase()
  if (!name) {
    message.warning('请填写名称')
    return
  }
  if (!editingType.value && !code) {
    message.warning('请填写编码')
    return
  }
  savingType.value = true
  try {
    await pmMetaApi.saveType({
      id: editingType.value?.id,
      code: editingType.value?.code ?? code,
      name,
      color: typeForm.value.color,
      enabled: typeForm.value.enabled ? 1 : 0,
    })
    invalidateIssueTypeCaches()
    message.success(editingType.value ? '类型已更新' : '类型已创建（已复制默认工作流）')
    showTypeModal.value = false
    await reload()
    if (!editingType.value) {
      const next = [...new Set([...projectTypes.value.map((t) => t.code), code])]
      await pmProjectIssueTypeApi.save(projectId.value, next)
      invalidateIssueTypeCaches(projectId.value)
      await reload()
      openType(code)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    savingType.value = false
  }
}

async function deleteType(t: PmWorkItemType) {
  try {
    await pmMetaApi.deleteType(t.code)
    invalidateIssueTypeCaches()
    message.success('类型已删除')
    await reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(reload)
</script>

<template>
  <div class="type-list-page">
    <div class="toolbar">
      <n-space align="center" :size="8">
        <n-button type="primary" size="small" @click="openCreateType">新增</n-button>
        <n-button size="small" @click="openSchemeModal">项目启用</n-button>
        <n-button size="small" :loading="exporting" @click="exportProjectScheme">导出方案</n-button>
        <n-button size="small" @click="showImport = true">导入方案</n-button>
      </n-space>
      <n-input
        v-model:value="keyword"
        size="small"
        clearable
        placeholder="搜索名称或编码"
        style="width: 220px"
      />
    </div>

    <n-spin :show="loading">
      <n-data-table
        size="small"
        :bordered="false"
        :columns="columns"
        :data="filteredTypes"
        :row-key="(row: PmWorkItemType) => row.code"
        :row-props="(row: PmWorkItemType) => ({
          style: 'cursor: pointer',
          onClick: () => openType(row.code),
        })"
      />
      <n-empty
        v-if="!loading && !filteredTypes.length"
        description="本项目尚未启用事项类型，请点击「项目启用」或「新增」"
        style="padding: 32px 0"
      />
    </n-spin>

    <n-card
      v-if="globalTypes.some((t) => t.enabled === 0)"
      size="small"
      style="margin-top: 12px"
      title="已停用的全局类型"
    >
      <n-space>
        <n-space
          v-for="t in globalTypes.filter((x) => x.enabled === 0)"
          :key="t.code"
          align="center"
          size="small"
        >
          <n-tag :bordered="false">{{ t.name }} ({{ t.code }})</n-tag>
          <n-button text size="tiny" type="primary" @click="openEditType(t)">恢复</n-button>
        </n-space>
      </n-space>
    </n-card>

    <PmIssueTypeSchemeImportModal
      v-model:show="showImport"
      :project-id="projectId"
      @imported="reload"
    />

    <n-modal v-model:show="showSchemeModal" preset="card" title="项目启用的事项类型" style="width: 480px">
      <n-form label-placement="top">
        <n-form-item label="启用类型" required>
          <n-select
            v-model:value="schemeCodes"
            multiple
            filterable
            :options="enabledGlobalOptions"
            placeholder="选择本项目可用的事项类型"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showSchemeModal = false">取消</n-button>
          <n-button type="primary" :loading="savingScheme" @click="saveScheme">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showTypeModal"
      preset="card"
      :title="editingType ? '编辑事项类型' : '新建事项类型'"
      style="width: 440px"
    >
      <n-form label-placement="top">
        <n-form-item label="编码" required>
          <n-input
            v-model:value="typeForm.code"
            placeholder="如 story"
            :disabled="!!editingType"
          />
        </n-form-item>
        <n-form-item label="名称" required>
          <n-input v-model:value="typeForm.name" placeholder="如 故事" />
        </n-form-item>
        <n-form-item label="颜色">
          <n-color-picker v-model:value="typeForm.color" :show-alpha="false" />
        </n-form-item>
        <n-form-item v-if="editingType" label="启用">
          <n-switch v-model:value="typeForm.enabled" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="space-between" style="width: 100%">
          <n-popconfirm v-if="editingType" @positive-click="deleteType(editingType!)">
            <template #trigger>
              <n-button type="error" quaternary>删除</n-button>
            </template>
            确定删除该类型？有事项时将失败。
          </n-popconfirm>
          <n-space v-else />
          <n-space>
            <n-button @click="showTypeModal = false">取消</n-button>
            <n-button type="primary" :loading="savingType" @click="saveTypeForm">保存</n-button>
          </n-space>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.type-list-page {
  background: var(--pm-surface, #fff);
  border: 1px solid var(--pm-border, #e8eaed);
  border-radius: 8px;
  padding: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.type-name-cell {
  display: flex;
  align-items: center;
}

.type-code {
  color: var(--pm-text-secondary, #646a73);
  font-size: 13px;
}

.row-actions {
  display: flex;
  gap: 8px;
}
</style>
