<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmFieldApi } from '@/modules/pm/api'
import { invalidateFieldOptionsCache } from '@/modules/pm/composables/useFieldOptions'
import type { FieldDefinition, FieldOption, FieldOptionSource, FieldRemoteOptionsConfig } from '@/modules/pm/types'
import { FIELD_TYPE_OPTIONS, TYPE_META, WORK_ITEM_TYPE_CODES } from '@/modules/pm/types'

type FieldOptionRow = FieldOption & { _uid: number }

let optionUidSeq = 0

function wrapOption(option: FieldOption): FieldOptionRow {
  return { ...option, _uid: ++optionUidSeq }
}

function wrapOptions(list: FieldOption[]): FieldOptionRow[] {
  return list.map((o) => wrapOption(o))
}

const props = defineProps<{
  show: boolean
  projectId: number | string
  fieldId?: number | string | null
  /** Pre-select issue type when creating from type layout page */
  defaultTypeCode?: string
  lockTypeCode?: boolean
}>()

const emit = defineEmits<{ 'update:show': [boolean]; saved: [id?: string] }>()

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const testingRemote = ref(false)
const previewOptions = ref<Array<{ label: string; value: string }>>([])

const form = ref<Partial<FieldDefinition>>({})
const options = ref<FieldOptionRow[]>([])
const optionSource = ref<FieldOptionSource>('static')
const remoteConfig = ref<FieldRemoteOptionsConfig>(emptyRemoteConfig())
const headerRows = ref<Array<{ key: string; value: string }>>([])
const dragFromIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

const isEdit = computed(() => !!props.fieldId)

const typeOptions = WORK_ITEM_TYPE_CODES.map((code) => ({
  label: TYPE_META[code].label,
  value: code,
}))

const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
]

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
})

function emptyRemoteConfig(): FieldRemoteOptionsConfig {
  return {
    url: '',
    method: 'GET',
    valueField: 'value',
    labelField: 'label',
    dataPath: '',
    cacheSeconds: 300,
  }
}

function emptyForm(): Partial<FieldDefinition> {
  const types = props.defaultTypeCode ? [props.defaultTypeCode] : ['task']
  return {
    fieldKey: '',
    fieldName: '',
    fieldType: 'TEXT',
    requiredFlag: 0,
    sortOrder: 100,
    projectId: props.projectId as unknown as number,
    scope: 'project',
    applicableTypes: types,
  }
}

function applyRemoteFromConfig(cfg?: FieldRemoteOptionsConfig) {
  remoteConfig.value = { ...emptyRemoteConfig(), ...cfg }
  headerRows.value = Object.entries(cfg?.headers ?? {}).map(([key, value]) => ({ key, value }))
  if (headerRows.value.length === 0) {
    headerRows.value = [{ key: '', value: '' }]
  }
}

async function loadField() {
  previewOptions.value = []
  if (!props.fieldId) {
    form.value = emptyForm()
    options.value = []
    optionSource.value = 'static'
    applyRemoteFromConfig()
    return
  }
  loading.value = true
  try {
    form.value = await pmFieldApi.getById(props.fieldId)
    options.value = wrapOptions(
      (await pmFieldApi.options(props.fieldId))
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)),
    )
    if (options.value.length === 0 && form.value.config?.options) {
      const cfg = form.value.config.options as Array<{ label: string; value: string }>
      options.value = wrapOptions(cfg.map((o, i) => ({
        optionKey: o.value,
        optionLabel: o.label,
        sortOrder: i + 1,
      })))
    }
    optionSource.value = form.value.config?.optionSource === 'remote' ? 'remote' : 'static'
    applyRemoteFromConfig(form.value.config?.remoteOptions as FieldRemoteOptionsConfig | undefined)
  } finally {
    loading.value = false
  }
}

function addOption() {
  options.value.push(wrapOption({ optionKey: '', optionLabel: '', sortOrder: options.value.length + 1 }))
}

function normalizeOptionOrder() {
  options.value.forEach((o, i) => {
    o.sortOrder = i + 1
  })
}

function reorderOptions(from: number, to: number) {
  if (from === to) return
  const list = [...options.value]
  const [item] = list.splice(from, 1)
  list.splice(to, 0, item)
  options.value = list
  normalizeOptionOrder()
}

function onOptionDragStart(index: number, event: DragEvent) {
  dragFromIndex.value = index
  dragOverIndex.value = index
  event.dataTransfer?.setData('text/plain', String(index))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onOptionDragOver(index: number, event: DragEvent) {
  event.preventDefault()
  const from = dragFromIndex.value
  if (from == null) return
  if (from !== index) {
    reorderOptions(from, index)
    dragFromIndex.value = index
  }
  dragOverIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onOptionDrop(index: number, event: DragEvent) {
  event.preventDefault()
  onOptionDragEnd()
}

function onOptionDragEnd() {
  dragFromIndex.value = null
  dragOverIndex.value = null
}

function removeOption(index: number) {
  options.value.splice(index, 1)
  normalizeOptionOrder()
}

function addHeaderRow() {
  headerRows.value.push({ key: '', value: '' })
}

function removeHeaderRow(index: number) {
  headerRows.value.splice(index, 1)
  if (headerRows.value.length === 0) {
    headerRows.value = [{ key: '', value: '' }]
  }
}

function buildHeaders(): Record<string, string> | undefined {
  const headers: Record<string, string> = {}
  for (const row of headerRows.value) {
    if (row.key.trim() && row.value.trim()) {
      headers[row.key.trim()] = row.value.trim()
    }
  }
  return Object.keys(headers).length ? headers : undefined
}

function buildRemotePayload(): FieldRemoteOptionsConfig {
  return {
    ...remoteConfig.value,
    url: remoteConfig.value.url?.trim() ?? '',
    method: remoteConfig.value.method ?? 'GET',
    valueField: remoteConfig.value.valueField?.trim() || 'value',
    labelField: remoteConfig.value.labelField?.trim() || 'label',
    dataPath: remoteConfig.value.dataPath?.trim() || undefined,
    headers: buildHeaders(),
    body: remoteConfig.value.body?.trim() || undefined,
    cacheSeconds: remoteConfig.value.cacheSeconds ?? 300,
  }
}

async function testRemoteOptions() {
  const payload = buildRemotePayload()
  if (!payload.url) {
    message.warning('请填写远程接口地址')
    return
  }
  testingRemote.value = true
  try {
    const result = await pmFieldApi.previewRemoteOptions(payload)
    if (!result.success) {
      message.error(result.message || '接口测试失败')
      previewOptions.value = []
      return
    }
    previewOptions.value = (result.options ?? []).map((o) => ({ label: o.label, value: o.value }))
    message.success(`测试成功，共 ${previewOptions.value.length} 个选项`)
  } finally {
    testingRemote.value = false
  }
}

async function save() {
  if (!form.value.fieldKey?.trim() || !form.value.fieldName?.trim()) {
    message.warning('请填写字段编码和名称')
    return
  }
  if (!form.value.applicableTypes?.length) {
    message.warning('请至少选择一个适用事项类型')
    return
  }
  if (form.value.fieldType === 'SELECT' || form.value.fieldType === 'MULTI_SELECT') {
    if (optionSource.value === 'static') {
      const valid = options.value.filter((o) => o.optionKey && o.optionLabel)
      if (!valid.length) {
        message.warning('请至少添加一个静态选项')
        return
      }
    } else if (!buildRemotePayload().url) {
      message.warning('请填写远程接口地址')
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      projectId: props.projectId,
      scope: 'project',
    } as FieldDefinition
    const rawOpts = form.value.fieldType === 'SELECT' || form.value.fieldType === 'MULTI_SELECT'
      ? options.value.filter((o) => o.optionKey && o.optionLabel)
      : undefined
    const opts = rawOpts?.map((o, i) => ({ ...o, sortOrder: i + 1 }))
    if (form.value.fieldType === 'SELECT' || form.value.fieldType === 'MULTI_SELECT') {
      payload.config = {
        optionSource: optionSource.value,
        ...(optionSource.value === 'static' && opts?.length
          ? { options: opts.map((o) => ({ label: o.optionLabel, value: o.optionKey })) }
          : {}),
        ...(optionSource.value === 'remote' ? { remoteOptions: buildRemotePayload() } : {}),
      }
    }
    const saveOpts = optionSource.value === 'static' ? opts : undefined
    const savedId = await pmFieldApi.save(payload, saveOpts)
    if (props.fieldId) invalidateFieldOptionsCache(props.fieldId)
    message.success(isEdit.value ? '字段已更新' : '字段已创建')
    emit('saved', savedId != null ? String(savedId) : undefined)
  } finally {
    saving.value = false
  }
}

watch(() => [props.show, props.fieldId], ([show]) => {
  if (show) loadField()
}, { immediate: true })
</script>

<template>
  <n-drawer v-model:show="visible" :width="600" :title="isEdit ? '编辑字段' : '新建字段'">
    <n-drawer-content :title="isEdit ? '编辑字段' : '新建字段'" closable :native-scrollbar="true">
      <n-spin :show="loading">
        <n-form label-placement="top">
          <n-form-item label="字段名称" required>
            <n-input v-model:value="form.fieldName" placeholder="如：严重程度" />
          </n-form-item>
          <n-form-item label="字段编码" required>
            <n-input
              v-model:value="form.fieldKey"
              placeholder="如：severity"
              :disabled="isEdit"
            />
            <template v-if="!isEdit" #feedback>
              <n-text depth="3">创建后不可修改，建议使用英文 snake_case</n-text>
            </template>
          </n-form-item>
          <n-form-item label="字段类型" required>
            <n-select
              v-model:value="form.fieldType"
              :options="FIELD_TYPE_OPTIONS"
              :disabled="isEdit"
            />
          </n-form-item>
          <n-form-item label="适用事项类型" required>
            <n-select
              v-model:value="form.applicableTypes"
              :options="typeOptions"
              multiple
              :disabled="lockTypeCode"
              placeholder="选择该字段适用的事项类型"
            />
          </n-form-item>
          <n-form-item label="必填">
            <n-switch :value="form.requiredFlag === 1" @update:value="(v) => (form.requiredFlag = v ? 1 : 0)" />
          </n-form-item>

          <template v-if="form.fieldType === 'SELECT' || form.fieldType === 'MULTI_SELECT'">
            <n-divider title-placement="left">选项配置</n-divider>
            <n-tabs v-model:value="optionSource" type="segment" animated>
              <n-tab-pane name="static" tab="静态值">
                <n-form-item label="选项列表">
                  <div class="option-list">
                    <n-text depth="3" class="option-list-hint">拖拽左侧手柄调整选项顺序</n-text>
                    <div
                      v-for="(opt, idx) in options"
                      :key="opt._uid"
                      class="option-row"
                      :class="{
                        'option-row--dragging': dragFromIndex === idx,
                        'option-row--over': dragOverIndex === idx && dragFromIndex !== null && dragFromIndex !== idx,
                      }"
                      @dragover="onOptionDragOver(idx, $event)"
                      @drop="onOptionDrop(idx, $event)"
                    >
                      <span
                        class="option-drag-handle"
                        draggable="true"
                        title="拖拽排序"
                        @dragstart="onOptionDragStart(idx, $event)"
                        @dragend="onOptionDragEnd"
                      >
                        <svg viewBox="0 0 10 16" width="10" height="16" aria-hidden="true">
                          <circle cx="2.5" cy="2.5" r="1.2" fill="currentColor" />
                          <circle cx="7.5" cy="2.5" r="1.2" fill="currentColor" />
                          <circle cx="2.5" cy="8" r="1.2" fill="currentColor" />
                          <circle cx="7.5" cy="8" r="1.2" fill="currentColor" />
                          <circle cx="2.5" cy="13.5" r="1.2" fill="currentColor" />
                          <circle cx="7.5" cy="13.5" r="1.2" fill="currentColor" />
                        </svg>
                      </span>
                      <n-input v-model:value="opt.optionKey" placeholder="值" class="option-input-key" />
                      <n-input v-model:value="opt.optionLabel" placeholder="显示文本" class="option-input-label" />
                      <n-button quaternary type="error" @click="removeOption(idx)">删除</n-button>
                    </div>
                    <n-button dashed block @click="addOption">添加选项</n-button>
                  </div>
                </n-form-item>
              </n-tab-pane>
              <n-tab-pane name="remote" tab="远程接口">
                <n-alert type="info" :bordered="false" style="margin: 12px 0">
                  通过 HTTP 接口动态加载选项，类似 Jira Data Source。请求由服务端代理，支持 GET/POST。
                </n-alert>
                <n-form-item label="接口地址" required>
                  <n-input v-model:value="remoteConfig.url" placeholder="https://api.example.com/options" />
                </n-form-item>
                <n-form-item label="请求方式">
                  <n-select v-model:value="remoteConfig.method" :options="methodOptions" style="width: 120px" />
                </n-form-item>
                <n-form-item label="数据路径">
                  <n-input v-model:value="remoteConfig.dataPath" placeholder="如 data.items，留空表示根节点为数组" />
                </n-form-item>
                <n-grid :cols="2" :x-gap="12">
                  <n-gi>
                    <n-form-item label="值字段">
                      <n-input v-model:value="remoteConfig.valueField" placeholder="value" />
                    </n-form-item>
                  </n-gi>
                  <n-gi>
                    <n-form-item label="显示字段">
                      <n-input v-model:value="remoteConfig.labelField" placeholder="label" />
                    </n-form-item>
                  </n-gi>
                </n-grid>
                <n-form-item label="缓存时间（秒）">
                  <n-input-number v-model:value="remoteConfig.cacheSeconds" :min="0" :max="3600" style="width: 160px" />
                </n-form-item>
                <n-form-item label="请求头">
                  <n-space vertical style="width: 100%">
                    <n-space v-for="(row, idx) in headerRows" :key="idx" align="center">
                      <n-input v-model:value="row.key" placeholder="Header 名" style="width: 140px" />
                      <n-input v-model:value="row.value" placeholder="Header 值" style="width: 220px" />
                      <n-button quaternary type="error" @click="removeHeaderRow(idx)">删除</n-button>
                    </n-space>
                    <n-button dashed block @click="addHeaderRow">添加请求头</n-button>
                  </n-space>
                </n-form-item>
                <n-form-item v-if="remoteConfig.method === 'POST'" label="请求体">
                  <n-input
                    v-model:value="remoteConfig.body"
                    type="textarea"
                    placeholder='{"key":"value"}'
                    :autosize="{ minRows: 3, maxRows: 8 }"
                  />
                </n-form-item>
                <n-form-item label="接口测试">
                  <n-space vertical style="width: 100%">
                    <n-button type="primary" ghost :loading="testingRemote" @click="testRemoteOptions">测试接口</n-button>
                    <n-text v-if="previewOptions.length" depth="3">
                      预览：{{ previewOptions.slice(0, 5).map((o) => o.label).join('、') }}
                      <span v-if="previewOptions.length > 5"> 等 {{ previewOptions.length }} 项</span>
                    </n-text>
                  </n-space>
                </n-form-item>
              </n-tab-pane>
            </n-tabs>
          </template>
        </n-form>
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.option-list-hint {
  font-size: 12px;
  margin-bottom: 4px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--n-border-color, #e8e8ec);
  border-radius: 6px;
  background: var(--n-color-modal, #fff);
  transition: background-color 0.15s ease, border-color 0.15s ease, opacity 0.15s ease, box-shadow 0.15s ease;
}

.option-row:hover {
  background: var(--n-color-hover, #f7f8fa);
}

.option-row--dragging {
  opacity: 0.55;
  background: #f7f8fa;
  border-color: #d0d0d5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.option-row--over {
  background: #eef3ff;
  border-color: #91afff;
}

.option-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  flex-shrink: 0;
  color: #999;
  cursor: grab;
  user-select: none;
  touch-action: none;
}

.option-drag-handle:active {
  cursor: grabbing;
}

.option-drag-handle:hover {
  color: #666;
}

.option-input-key {
  width: 110px;
  flex-shrink: 0;
}

.option-input-label {
  flex: 1;
  min-width: 0;
}
</style>
