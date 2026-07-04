<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmIssueTypeSchemeApi } from '@/modules/pm/api'
import type {
  IssueTypeSchemeExport,
  ProjectFieldSchemeExport,
  ProjectIssueTypeSchemeExport,
  SchemeImportMode,
  TypeFieldSchemeExport,
} from '@/modules/pm/types'
import { SCHEME_SECTION_LABELS } from '@/modules/pm/types'
import { readJsonFile } from '@/modules/pm/utils/jsonFile'
import type { EntityId } from '@/modules/pm/utils/id'

const props = defineProps<{
  show: boolean
  projectId: EntityId
  /** Target type when importing single scheme; omit for project-wide only */
  typeCode?: string
}>()

const emit = defineEmits<{ 'update:show': [boolean]; imported: [] }>()

const message = useMessage()
const fileInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const importing = ref(false)
const importMode = ref<SchemeImportMode>('MERGE')
const parsedScheme = ref<IssueTypeSchemeExport | null>(null)
const parsedLegacyScheme = ref<TypeFieldSchemeExport | null>(null)
const parsedProjectScheme = ref<ProjectIssueTypeSchemeExport | null>(null)
const parsedLegacyProjectScheme = ref<ProjectFieldSchemeExport | null>(null)
const preview = ref<Awaited<ReturnType<typeof pmIssueTypeSchemeApi.previewImport>> | null>(null)

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
})

const isProjectImport = computed(() => !!parsedProjectScheme.value || !!parsedLegacyProjectScheme.value)
const isTypeImport = computed(() => !!parsedScheme.value || !!parsedLegacyScheme.value)

const modeOptions = [
  { label: '合并（保留现有额外字段）', value: 'MERGE' as SchemeImportMode },
  { label: '替换（移除未包含的自定义字段）', value: 'REPLACE' as SchemeImportMode },
]

const sectionLabels = computed(() =>
  (preview.value?.sections ?? []).map((k) => SCHEME_SECTION_LABELS[k] ?? k),
)

function reset() {
  parsedScheme.value = null
  parsedLegacyScheme.value = null
  parsedProjectScheme.value = null
  parsedLegacyProjectScheme.value = null
  preview.value = null
  importMode.value = 'MERGE'
}

function openFilePicker() {
  fileInput.value?.click()
}

function isIssueTypeScheme(data: unknown): data is IssueTypeSchemeExport {
  return !!data && typeof data === 'object' && (data as IssueTypeSchemeExport).kind === 'pm_issue_type_scheme'
}

function isProjectIssueTypeScheme(data: unknown): data is ProjectIssueTypeSchemeExport {
  return !!data && typeof data === 'object' && (data as ProjectIssueTypeSchemeExport).kind === 'pm_project_issue_type_schemes'
}

function isLegacyTypeScheme(data: unknown): data is TypeFieldSchemeExport {
  return !!data && typeof data === 'object' && (data as TypeFieldSchemeExport).kind === 'pm_type_field_scheme'
}

function isLegacyProjectScheme(data: unknown): data is ProjectFieldSchemeExport {
  return !!data && typeof data === 'object' && (data as ProjectFieldSchemeExport).kind === 'pm_project_field_schemes'
}

async function loadPreview() {
  if (!props.typeCode) return
  if (parsedScheme.value) {
    preview.value = await pmIssueTypeSchemeApi.previewImport(props.projectId, props.typeCode, {
      scheme: parsedScheme.value,
    })
  } else if (parsedLegacyScheme.value) {
    preview.value = await pmIssueTypeSchemeApi.previewImport(props.projectId, props.typeCode, {
      legacyScheme: parsedLegacyScheme.value,
    })
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  loading.value = true
  reset()
  try {
    const data = await readJsonFile<unknown>(file)
    if (isIssueTypeScheme(data)) {
      parsedScheme.value = data
      await loadPreview()
    } else if (isLegacyTypeScheme(data)) {
      parsedLegacyScheme.value = data
      message.info('检测到旧版字段方案格式，将仅导入字段配置')
      await loadPreview()
    } else if (isProjectIssueTypeScheme(data)) {
      parsedProjectScheme.value = data
    } else if (isLegacyProjectScheme(data)) {
      parsedLegacyProjectScheme.value = data
      message.info('检测到旧版字段方案格式，将仅导入字段配置')
    } else {
      message.error('无法识别的配置文件格式')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '文件解析失败')
  } finally {
    loading.value = false
  }
}

function formatImportSummary(results: Awaited<ReturnType<typeof pmIssueTypeSchemeApi.importProject>>) {
  const created = results.reduce((s, r) => s + r.fieldsCreated, 0)
  const updated = results.reduce((s, r) => s + r.fieldsUpdated, 0)
  const workflows = results.filter((r) => r.statusWorkflowApplied).length
  return `已导入 ${results.length} 个事项类型（字段新建 ${created} / 更新 ${updated}${workflows ? `，状态流转 ${workflows} 个` : ''}）`
}

async function confirmImport() {
  importing.value = true
  try {
    if (parsedProjectScheme.value) {
      const results = await pmIssueTypeSchemeApi.importProject(
        props.projectId,
        { projectScheme: parsedProjectScheme.value },
        importMode.value,
      )
      message.success(formatImportSummary(results))
    } else if (parsedLegacyProjectScheme.value) {
      const results = await pmIssueTypeSchemeApi.importProject(
        props.projectId,
        { legacyProjectScheme: parsedLegacyProjectScheme.value },
        importMode.value,
      )
      message.success(formatImportSummary(results))
    } else if (props.typeCode) {
      let result
      if (parsedScheme.value) {
        result = await pmIssueTypeSchemeApi.importType(
          props.projectId,
          props.typeCode,
          { scheme: parsedScheme.value },
          importMode.value,
        )
      } else if (parsedLegacyScheme.value) {
        result = await pmIssueTypeSchemeApi.importType(
          props.projectId,
          props.typeCode,
          { legacyScheme: parsedLegacyScheme.value },
          importMode.value,
        )
      } else {
        message.warning('请选择有效的配置文件')
        return
      }
      const parts = [`新建 ${result.fieldsCreated} / 更新 ${result.fieldsUpdated} 字段`]
      if (result.statusWorkflowApplied) {
        parts.push(`状态流转 ${result.statusCount} 个`)
      }
      message.success(`导入完成：${parts.join('，')}`)
    } else {
      message.warning('请选择有效的配置文件')
      return
    }
    visible.value = false
    emit('imported')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导入失败')
  } finally {
    importing.value = false
  }
}

const displayScheme = computed(() => parsedScheme.value ?? parsedLegacyScheme.value)
const displayProjectScheme = computed(() => parsedProjectScheme.value ?? parsedLegacyProjectScheme.value)

watch(visible, (v) => {
  if (!v) reset()
})
</script>

<template>
  <n-modal v-model:show="visible" preset="card" title="导入事项类型方案" style="width: 560px">
    <input ref="fileInput" type="file" accept=".json,application/json" hidden @change="onFileChange" />
    <n-spin :show="loading">
      <n-space vertical size="large">
        <n-alert type="info" :bordered="false">
          支持导入统一的事项类型方案 JSON，包含字段配置与状态流转；兼容旧版仅含字段的配置包。
          适用于需求、任务、缺陷、测试用例等类型，后续可扩展更多配置节。
        </n-alert>
        <n-button dashed block @click="openFilePicker">选择 JSON 文件</n-button>

        <template v-if="isTypeImport && displayScheme">
          <n-descriptions bordered size="small" :column="1">
            <n-descriptions-item label="源事项类型">
              {{ displayScheme.typeName ?? displayScheme.typeCode }}
            </n-descriptions-item>
            <n-descriptions-item v-if="preview?.sections?.length" label="包含配置节">
              {{ sectionLabels.join('、') }}
            </n-descriptions-item>
            <n-descriptions-item v-if="preview" label="自定义字段">
              {{ preview.customFieldCount }} 个（预计新建 {{ preview.fieldsToCreate }} / 更新 {{ preview.fieldsToUpdate }}）
            </n-descriptions-item>
            <n-descriptions-item v-if="preview?.statusWorkflowWillApply" label="状态流转">
              {{ preview.statusCount }} 个状态（将覆盖当前流转规则）
            </n-descriptions-item>
          </n-descriptions>
          <n-alert v-for="(w, i) in preview?.warnings ?? []" :key="i" type="warning" :bordered="false">{{ w }}</n-alert>
        </template>

        <template v-else-if="isProjectImport && displayProjectScheme">
          <n-descriptions bordered size="small" :column="1">
            <n-descriptions-item label="事项类型数">
              {{ (parsedProjectScheme?.schemes ?? parsedLegacyProjectScheme?.schemes)?.length ?? 0 }} 个
            </n-descriptions-item>
            <n-descriptions-item label="导出时间">{{ displayProjectScheme.exportedAt ?? '—' }}</n-descriptions-item>
          </n-descriptions>
        </template>

        <n-form-item v-if="isTypeImport || isProjectImport" label="字段导入模式" label-placement="left">
          <n-radio-group v-model:value="importMode">
            <n-space vertical>
              <n-radio v-for="opt in modeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</n-radio>
            </n-space>
          </n-radio-group>
          <n-text v-if="preview?.statusWorkflowWillApply" depth="3" style="display: block; margin-top: 8px">
            状态流转配置导入时将覆盖当前规则（不受上方模式影响）
          </n-text>
        </n-form-item>
      </n-space>
    </n-spin>
    <template #footer>
      <n-space justify="end">
        <n-button @click="visible = false">取消</n-button>
        <n-button
          type="primary"
          :loading="importing"
          :disabled="!isTypeImport && !isProjectImport"
          @click="confirmImport"
        >
          确认导入
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>
