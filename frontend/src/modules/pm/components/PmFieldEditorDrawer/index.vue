<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmFieldApi } from '@/modules/pm/api'
import type { FieldDefinition, FieldOption } from '@/modules/pm/types'
import { FIELD_TYPE_OPTIONS, TYPE_META, WORK_ITEM_TYPE_CODES } from '@/modules/pm/types'

const props = defineProps<{
  show: boolean
  projectId: number
  fieldId?: number | string | null
}>()

const emit = defineEmits<{ 'update:show': [boolean]; saved: [] }>()

const message = useMessage()
const loading = ref(false)
const saving = ref(false)

const form = ref<Partial<FieldDefinition>>({})
const options = ref<FieldOption[]>([])
const isEdit = computed(() => !!props.fieldId)

const typeOptions = WORK_ITEM_TYPE_CODES.map((code) => ({
  label: TYPE_META[code].label,
  value: code,
}))

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
})

function emptyForm(): Partial<FieldDefinition> {
  return {
    fieldKey: '',
    fieldName: '',
    fieldType: 'TEXT',
    requiredFlag: 0,
    sortOrder: 100,
    projectId: props.projectId,
    scope: 'project',
    applicableTypes: ['task'],
  }
}

async function loadField() {
  if (!props.fieldId) {
    form.value = emptyForm()
    options.value = []
    return
  }
  loading.value = true
  try {
    form.value = await pmFieldApi.getById(props.fieldId)
    options.value = await pmFieldApi.options(props.fieldId)
    if (options.value.length === 0 && form.value.config?.options) {
      const cfg = form.value.config.options as Array<{ label: string; value: string }>
      options.value = cfg.map((o, i) => ({
        optionKey: o.value,
        optionLabel: o.label,
        sortOrder: i + 1,
      }))
    }
  } finally {
    loading.value = false
  }
}

function addOption() {
  options.value.push({ optionKey: '', optionLabel: '', sortOrder: options.value.length + 1 })
}

function removeOption(index: number) {
  options.value.splice(index, 1)
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
  saving.value = true
  try {
    const payload = {
      ...form.value,
      projectId: props.projectId,
      scope: 'project',
    } as FieldDefinition
    const opts = form.value.fieldType === 'SELECT' || form.value.fieldType === 'MULTI_SELECT'
      ? options.value.filter((o) => o.optionKey && o.optionLabel)
      : undefined
    if (opts?.length) {
      payload.config = { options: opts.map((o) => ({ label: o.optionLabel, value: o.optionKey })) }
    }
    await pmFieldApi.save(payload, opts)
    message.success(isEdit.value ? '字段已更新' : '字段已创建')
    emit('saved')
  } finally {
    saving.value = false
  }
}

watch(() => [props.show, props.fieldId], ([show]) => {
  if (show) loadField()
}, { immediate: true })
</script>

<template>
  <n-drawer v-model:show="visible" :width="520" :title="isEdit ? '编辑字段' : '新建字段'">
    <n-drawer-content :title="isEdit ? '编辑字段' : '新建字段'" closable>
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
              placeholder="选择该字段适用的事项类型"
            />
          </n-form-item>
          <n-form-item label="必填">
            <n-switch :value="form.requiredFlag === 1" @update:value="(v) => (form.requiredFlag = v ? 1 : 0)" />
          </n-form-item>
          <n-form-item v-if="form.fieldType === 'SELECT' || form.fieldType === 'MULTI_SELECT'" label="选项列表">
            <n-space vertical style="width: 100%">
              <n-space v-for="(opt, idx) in options" :key="idx" align="center">
                <n-input v-model:value="opt.optionKey" placeholder="值" style="width: 120px" />
                <n-input v-model:value="opt.optionLabel" placeholder="显示文本" style="width: 160px" />
                <n-button quaternary type="error" @click="removeOption(idx)">删除</n-button>
              </n-space>
              <n-button dashed block @click="addOption">添加选项</n-button>
            </n-space>
          </n-form-item>
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
