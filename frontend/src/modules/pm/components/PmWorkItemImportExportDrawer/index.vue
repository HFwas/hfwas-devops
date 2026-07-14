<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmWorkItemIoApi } from '@/modules/pm/api'
import type { FieldDefinition, QuerySpec, WorkItemImportMode, WorkItemIoColumn } from '@/modules/pm/types'
import type { EntityId } from '@/modules/pm/utils/id'
import { asId } from '@/modules/pm/utils/id'
import {
  buildIoColumns,
  resolveDefaultFieldKeys,
} from '@/modules/pm/utils/workItemIoColumns'

const props = defineProps<{
  show: boolean
  mode: 'export' | 'import'
  projectId: EntityId
  typeCode: string
  typeLabel: string
  fieldDefs: FieldDefinition[]
  querySpec: QuerySpec
  selectedIds: string[]
  /** 类型功能配置的默认导出字段；空则回退 showInList */
  defaultExportFieldKeys?: string[]
  /** 类型功能配置的默认导入字段；空则回退现有逻辑 */
  defaultImportFieldKeys?: string[]
}>()

const emit = defineEmits<{ 'update:show': [boolean]; done: [] }>()

const message = useMessage()
const columns = ref<WorkItemIoColumn[]>([])
const selectedFieldKeys = ref<string[]>([])
const exporting = ref(false)
const importing = ref(false)
const downloadingTemplate = ref(false)
const previewing = ref(false)
const importMode = ref<WorkItemImportMode>('CREATE')
const importFile = ref<File | null>(null)
const preview = ref<Awaited<ReturnType<typeof pmWorkItemIoApi.previewImport>> | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
})

const isExport = computed(() => props.mode === 'export')

const exportableColumns = computed(() => columns.value.filter((c) => c.exportable !== false))

const importableColumns = computed(() => columns.value.filter((c) => c.importable !== false))

const activeColumns = computed(() => (isExport.value ? exportableColumns.value : importableColumns.value))

const exportHint = computed(() => {
  if (props.selectedIds.length) {
    return `已勾选 ${props.selectedIds.length} 条，将批量导出所选事项`
  }
  return `未勾选事项，将全量导出当前类型下全部${props.typeLabel}`
})

const importModeOptions = [
  { label: '仅新建', value: 'CREATE' as WorkItemImportMode },
  { label: '按编号更新或新建', value: 'UPSERT' as WorkItemImportMode },
]

function initColumns() {
  columns.value = buildIoColumns(props.fieldDefs)
  if (isExport.value) {
    selectedFieldKeys.value = resolveDefaultFieldKeys(
      props.defaultExportFieldKeys,
      columns.value,
      'export',
    )
  } else {
    selectedFieldKeys.value = resolveDefaultFieldKeys(
      props.defaultImportFieldKeys,
      columns.value,
      'import',
    )
    if (importMode.value === 'UPSERT' && !selectedFieldKeys.value.includes('itemKey')) {
      selectedFieldKeys.value = ['itemKey', ...selectedFieldKeys.value]
    }
  }
}

function selectAllFields() {
  selectedFieldKeys.value = activeColumns.value.map((c) => c.fieldKey)
}

function resetImportState() {
  importFile.value = null
  preview.value = null
  importMode.value = 'CREATE'
}

async function doExport() {
  if (!selectedFieldKeys.value.length) {
    message.warning('请至少选择一个导出字段')
    return
  }
  exporting.value = true
  try {
    const payload: Parameters<typeof pmWorkItemIoApi.exportExcel>[0] = {
      projectId: props.projectId,
      typeCode: props.typeCode,
      fieldKeys: selectedFieldKeys.value,
    }
    if (props.selectedIds.length) {
      payload.ids = props.selectedIds
    } else {
      payload.querySpec = { projectId: props.projectId, typeCode: props.typeCode, pageNo: 1, pageSize: 10000 }
    }
    const { blob, filename } = await pmWorkItemIoApi.exportExcel(payload)
    triggerDownload(blob, filename)
    message.success('导出成功')
    visible.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

async function downloadTemplate() {
  if (!selectedFieldKeys.value.length) {
    message.warning('请至少选择一个导入字段')
    return
  }
  downloadingTemplate.value = true
  try {
    const keys =
      importMode.value === 'UPSERT' && !selectedFieldKeys.value.includes('itemKey')
        ? ['itemKey', ...selectedFieldKeys.value]
        : selectedFieldKeys.value
    const { blob, filename } = await pmWorkItemIoApi.downloadImportTemplate({
      projectId: props.projectId,
      typeCode: props.typeCode,
      fieldKeys: keys,
    })
    triggerDownload(blob, filename)
    message.success('模板已下载')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '下载模板失败')
  } finally {
    downloadingTemplate.value = false
  }
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function openFilePicker() {
  fileInput.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importFile.value = file
  preview.value = null
  previewing.value = true
  try {
    preview.value = await pmWorkItemIoApi.previewImport(props.projectId, props.typeCode, file)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '预览失败')
    importFile.value = null
  } finally {
    previewing.value = false
  }
}

async function doImport() {
  if (!importFile.value) {
    message.warning('请选择 Excel 文件')
    return
  }
  importing.value = true
  try {
    const keys =
      importMode.value === 'UPSERT' && !selectedFieldKeys.value.includes('itemKey')
        ? ['itemKey', ...selectedFieldKeys.value]
        : selectedFieldKeys.value
    const result = await pmWorkItemIoApi.importExcel(
      props.projectId,
      props.typeCode,
      importFile.value,
      importMode.value,
      keys,
    )
    const parts = [`新建 ${result.created}`, `更新 ${result.updated}`]
    if (result.failed) parts.push(`失败 ${result.failed}`)
    message.success(`导入完成：${parts.join('，')}`)
    if (result.errors.length) {
      message.warning(result.errors.slice(0, 3).join('；'))
    }
    visible.value = false
    emit('done')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导入失败')
  } finally {
    importing.value = false
  }
}

watch(
  () => props.show,
  (v) => {
    if (v) {
      initColumns()
      if (!isExport.value) resetImportState()
    } else if (!isExport.value) {
      resetImportState()
    }
  },
)

watch(
  () => [props.mode, props.fieldDefs, props.defaultExportFieldKeys, props.defaultImportFieldKeys] as const,
  () => {
    if (props.show) initColumns()
  },
  { deep: true },
)

watch(importMode, (mode) => {
  if (mode === 'UPSERT' && !selectedFieldKeys.value.includes('itemKey')) {
    selectedFieldKeys.value = ['itemKey', ...selectedFieldKeys.value]
  }
})
</script>

<template>
  <n-drawer v-model:show="visible" :width="520" placement="right">
    <n-drawer-content :title="isExport ? `导出${typeLabel}` : `导入${typeLabel}`" closable>
      <!-- 导出 -->
      <n-space v-if="isExport" vertical size="large">
        <n-alert type="info" :bordered="false">{{ exportHint }}</n-alert>
        <n-form-item label="导出字段" label-placement="top">
          <n-space vertical style="width: 100%">
            <n-space align="center">
              <n-button size="small" @click="selectAllFields">全选</n-button>
              <n-text depth="3">已选 {{ selectedFieldKeys.length }} / {{ exportableColumns.length }}</n-text>
            </n-space>
            <n-scrollbar style="max-height: 320px">
              <n-checkbox-group v-model:value="selectedFieldKeys">
                <n-space vertical>
                  <n-checkbox v-for="col in exportableColumns" :key="col.fieldKey" :value="col.fieldKey">
                    {{ col.fieldName }}
                  </n-checkbox>
                </n-space>
              </n-checkbox-group>
            </n-scrollbar>
          </n-space>
        </n-form-item>
        <n-button type="primary" block :loading="exporting" @click="doExport">下载 Excel</n-button>
      </n-space>

      <!-- 导入 -->
      <n-space v-else vertical size="large">
        <n-alert type="info" :bordered="false">
          先下载导入模板，按模板填写后上传。按编号更新时需包含「编号」列。
        </n-alert>
        <n-form-item label="导入字段" label-placement="top">
          <n-space vertical style="width: 100%">
            <n-space align="center">
              <n-button size="small" @click="selectAllFields">全选</n-button>
              <n-text depth="3">已选 {{ selectedFieldKeys.length }} / {{ importableColumns.length }}</n-text>
            </n-space>
            <n-scrollbar style="max-height: 240px">
              <n-checkbox-group v-model:value="selectedFieldKeys">
                <n-space vertical>
                  <n-checkbox v-for="col in importableColumns" :key="col.fieldKey" :value="col.fieldKey">
                    {{ col.fieldName }}
                    <n-text v-if="col.fieldKey === 'itemKey'" depth="3">（更新模式必填）</n-text>
                    <n-text v-else-if="col.fieldKey === 'title'" depth="3">（必填）</n-text>
                  </n-checkbox>
                </n-space>
              </n-checkbox-group>
            </n-scrollbar>
          </n-space>
        </n-form-item>
        <n-button block :loading="downloadingTemplate" @click="downloadTemplate">下载导入模板</n-button>
        <n-divider />
        <input ref="fileInput" type="file" accept=".xlsx,.xls" hidden @change="onFileChange" />
        <n-spin :show="previewing">
          <n-space vertical size="large">
            <n-button dashed block @click="openFilePicker">{{ importFile?.name ?? '选择 Excel 文件' }}</n-button>
            <n-form-item label="导入模式" label-placement="left">
              <n-radio-group v-model:value="importMode">
                <n-space vertical>
                  <n-radio v-for="opt in importModeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</n-radio>
                </n-space>
              </n-radio-group>
            </n-form-item>
            <template v-if="preview">
              <n-descriptions bordered size="small" :column="1">
                <n-descriptions-item label="数据行">{{ preview.totalRows }}</n-descriptions-item>
                <n-descriptions-item label="有效行">{{ preview.validRows }}</n-descriptions-item>
                <n-descriptions-item label="识别列">{{ preview.detectedHeaders.join('、') || '—' }}</n-descriptions-item>
              </n-descriptions>
              <n-alert v-for="(w, i) in preview.warnings" :key="i" type="warning" :bordered="false">{{ w }}</n-alert>
            </template>
            <n-button type="primary" block :loading="importing" :disabled="!importFile" @click="doImport">
              开始导入
            </n-button>
          </n-space>
        </n-spin>
      </n-space>
    </n-drawer-content>
  </n-drawer>
</template>
