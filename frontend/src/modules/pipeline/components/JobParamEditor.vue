<script setup lang="ts">
import { Plus, Trash2, Eye } from '@lucide/vue'
import { useMessage } from 'naive-ui'
import { pipelineJobParamApi } from '@/modules/pipeline/api/pipeline'
import type { JobParamType, TaskKindParam } from '@/modules/pipeline/types/pipeline'

const props = defineProps<{
  params: TaskKindParam[]
}>()

const emit = defineEmits<{
  'update:params': [value: TaskKindParam[]]
}>()

const message = useMessage()
const showEditor = ref(false)
const editingIndex = ref<number | null>(null)
const newOption = ref('')
const previewResult = ref<string[] | null>(null)
const previewLoading = ref(false)

const form = ref<TaskKindParam & { apiHeadersText: string }>({
  paramKey: '',
  paramLabel: '',
  paramType: 'input',
  defaultValue: '',
  required: false,
  sortOrder: 0,
  options: [],
  apiUrl: '',
  apiMethod: 'GET',
  apiHeaders: {},
  apiHeadersText: '{}',
  apiResponsePath: '',
  placeholder: '',
})

const isEditing = computed(() => editingIndex.value != null)

function emptyForm(sortOrder: number): TaskKindParam & { apiHeadersText: string } {
  return {
    paramKey: '',
    paramLabel: '',
    paramType: 'input',
    defaultValue: '',
    required: false,
    sortOrder,
    options: [],
    apiUrl: '',
    apiMethod: 'GET',
    apiHeaders: {},
    apiHeadersText: '{}',
    apiResponsePath: '',
    placeholder: '',
  }
}

function openNew() {
  editingIndex.value = null
  form.value = emptyForm(props.params.length)
  previewResult.value = null
  showEditor.value = true
}

function openEdit(index: number) {
  const p = props.params[index]
  editingIndex.value = index
  form.value = {
    ...p,
    options: p.options ? [...p.options] : [],
    apiUrl: p.apiUrl || '',
    apiMethod: p.apiMethod || 'GET',
    apiHeaders: p.apiHeaders ? { ...p.apiHeaders } : {},
    apiHeadersText: JSON.stringify(p.apiHeaders || {}, null, 2),
    apiResponsePath: p.apiResponsePath || '',
    placeholder: p.placeholder || '',
  }
  previewResult.value = null
  showEditor.value = true
}

function save() {
  if (!form.value.paramKey.trim()) {
    message.warning('环境变量名不能为空')
    return
  }
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(form.value.paramKey.trim())) {
    message.warning('环境变量名需为字母、数字或下划线，且不能以数字开头')
    return
  }
  if (!form.value.paramLabel.trim()) {
    message.warning('显示标签不能为空')
    return
  }
  if (form.value.apiHeadersText) {
    try {
      form.value.apiHeaders = JSON.parse(form.value.apiHeadersText)
    } catch {
      message.warning('请求头格式错误，请输入合法 JSON')
      return
    }
  }
  const next: TaskKindParam = {
    id: form.value.id,
    paramKey: form.value.paramKey.trim(),
    paramLabel: form.value.paramLabel.trim(),
    paramType: form.value.paramType,
    defaultValue: form.value.defaultValue || '',
    required: !!form.value.required,
    sortOrder: form.value.sortOrder,
    options: form.value.options,
    apiUrl: form.value.apiUrl,
    apiMethod: form.value.apiMethod,
    apiHeaders: form.value.apiHeaders,
    apiResponsePath: form.value.apiResponsePath,
    placeholder: form.value.placeholder,
  }
  const list = [...props.params]
  if (isEditing.value) {
    list[editingIndex.value!] = next
  } else {
    if (list.some((item) => item.paramKey === next.paramKey)) {
      message.warning('环境变量名已存在')
      return
    }
    list.push(next)
  }
  emit('update:params', list)
  showEditor.value = false
}

function remove(index: number) {
  const list = props.params.filter((_, i) => i !== index)
  emit('update:params', list)
}

function addOption() {
  const val = newOption.value.trim()
  if (!val) return
  if (!form.value.options) form.value.options = []
  form.value.options.push(val)
  newOption.value = ''
}

function removeOption(i: number) {
  form.value.options?.splice(i, 1)
}

function paramTypeLabel(type: JobParamType | string) {
  if (type === 'select') return '枚举'
  if (type === 'api_select') return '远程接口'
  return '静态值'
}

async function previewApi() {
  if (!form.value.apiUrl) {
    message.warning('请输入 API URL')
    return
  }
  previewLoading.value = true
  previewResult.value = null
  let headers: Record<string, string> | undefined
  if (form.value.apiHeadersText) {
    try {
      headers = JSON.parse(form.value.apiHeadersText)
    } catch {
      message.warning('请求头 JSON 格式错误')
      previewLoading.value = false
      return
    }
  }
  try {
    const result = await pipelineJobParamApi.previewApi({
      apiUrl: form.value.apiUrl,
      apiMethod: form.value.apiMethod,
      apiHeaders: headers,
      apiResponsePath: form.value.apiResponsePath,
    })
    if (result.success) {
      previewResult.value = result.options
      message.success(`获取到 ${result.options.length} 个选项`)
    } else {
      message.error(result.errorMessage || '预览失败')
    }
  } catch {
    message.error('请求失败')
  } finally {
    previewLoading.value = false
  }
}
</script>

<template>
  <div class="job-param-list">
    <div class="job-param-header">
      <span class="job-param-title">预置环境变量</span>
      <n-button size="tiny" type="primary" ghost @click="openNew">
        <template #icon><Plus :size="14" /></template>
        添加变量
      </n-button>
    </div>
    <p class="job-param-hint">
      在此定义本任务可用的环境变量。流水线编辑时只能选择「写死」或「设为变量」，选项来自这里的静态值 / 枚举 / 远程接口。
    </p>

    <div v-if="params.length === 0" class="job-param-empty">暂无预置变量</div>
    <div v-else class="job-param-items">
      <div v-for="(p, i) in params" :key="p.paramKey || i" class="job-param-item">
        <div class="job-param-info">
          <span class="job-param-key">{{ p.paramKey }}</span>
          <span class="job-param-label">{{ p.paramLabel }}</span>
          <n-tag size="tiny" :bordered="false">{{ paramTypeLabel(p.paramType) }}</n-tag>
          <n-tag v-if="p.required" size="tiny" type="error" :bordered="false">必填</n-tag>
        </div>
        <div class="job-param-actions">
          <n-button size="tiny" quaternary @click="openEdit(i)">编辑</n-button>
          <n-button size="tiny" quaternary type="error" @click="remove(i)">
            <template #icon><Trash2 :size="12" /></template>
          </n-button>
        </div>
      </div>
    </div>

    <n-modal v-model:show="showEditor" title="预置环境变量" preset="card" style="width: 520px">
      <n-form label-placement="top">
        <n-form-item label="环境变量名">
          <n-input v-model:value="form.paramKey" placeholder="如 GIT_REF、IMAGE_TAG、DEPLOY_ENV" />
        </n-form-item>
        <n-form-item label="显示标签">
          <n-input v-model:value="form.paramLabel" placeholder="如 代码分支、镜像标签" />
        </n-form-item>
        <n-form-item label="取值来源">
          <n-select
            v-model:value="form.paramType"
            :options="[
              { label: '静态值（文本）', value: 'input' },
              { label: '枚举（写死选项）', value: 'select' },
              { label: '远程接口', value: 'api_select' },
            ]"
          />
        </n-form-item>
        <n-form-item label="默认值">
          <n-input v-model:value="form.defaultValue" placeholder="默认值" />
        </n-form-item>
        <n-form-item label="运行时必填">
          <n-switch v-model:value="form.required" />
        </n-form-item>

        <template v-if="form.paramType === 'input'">
          <n-form-item label="占位提示">
            <n-input v-model:value="form.placeholder" placeholder="输入提示文字" />
          </n-form-item>
        </template>

        <template v-if="form.paramType === 'select'">
          <n-form-item label="枚举选项">
            <div class="job-param-options">
              <div v-for="(opt, oi) in form.options" :key="oi" class="job-param-option">
                <span>{{ opt }}</span>
                <n-button size="tiny" quaternary type="error" @click="removeOption(oi)">
                  <template #icon><Trash2 :size="12" /></template>
                </n-button>
              </div>
              <div class="job-param-add-option">
                <n-input v-model:value="newOption" placeholder="输入选项" size="small" @keyup.enter="addOption" />
                <n-button size="small" @click="addOption">添加</n-button>
              </div>
            </div>
          </n-form-item>
        </template>

        <template v-if="form.paramType === 'api_select'">
          <n-form-item label="API URL">
            <n-input v-model:value="form.apiUrl" placeholder="https://gitlab.com/api/v4/projects/1/repository/branches" />
          </n-form-item>
          <n-form-item label="请求方法">
            <n-select
              v-model:value="form.apiMethod"
              :options="[
                { label: 'GET', value: 'GET' },
                { label: 'POST', value: 'POST' },
              ]"
            />
          </n-form-item>
          <n-form-item label="请求头 (JSON)">
            <n-input
              v-model:value="form.apiHeadersText"
              type="textarea"
              :rows="3"
              placeholder='{"PRIVATE-TOKEN": "glpat-xxx"}'
            />
          </n-form-item>
          <n-form-item label="JSONPath">
            <n-input v-model:value="form.apiResponsePath" placeholder="$[].name" />
          </n-form-item>
          <n-form-item label="预览">
            <n-button :loading="previewLoading" @click="previewApi">
              <template #icon><Eye :size="14" /></template>
              获取选项
            </n-button>
            <div v-if="previewResult" class="job-param-preview">
              <n-tag v-for="(opt, oi) in previewResult" :key="oi" size="tiny" style="margin: 2px">
                {{ opt }}
              </n-tag>
              <span v-if="previewResult.length === 0" class="job-param-preview-empty">无结果</span>
            </div>
          </n-form-item>
        </template>
      </n-form>
      <template #footer>
        <n-button @click="showEditor = false">取消</n-button>
        <n-button type="primary" @click="save">确定</n-button>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.job-param-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.job-param-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
}

.job-param-hint {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--wb-muted, #888);
}

.job-param-empty {
  font-size: 12px;
  color: var(--wb-muted, #888);
  padding: 8px 0;
}

.job-param-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.job-param-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 4px;
  background: var(--wb-card-bg, #fff);
}

.job-param-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.job-param-key {
  font-size: 12px;
  font-weight: 600;
  font-family: monospace;
  color: var(--wb-primary, #3370ff);
}

.job-param-label {
  font-size: 12px;
  color: var(--wb-ink, #1f2329);
}

.job-param-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.job-param-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.job-param-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 4px;
  font-size: 12px;
}

.job-param-add-option {
  display: flex;
  gap: 8px;
}

.job-param-preview {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.job-param-preview-empty {
  font-size: 12px;
  color: var(--wb-muted, #888);
}
</style>
