<script setup lang="ts">
import { useMessage } from 'naive-ui'
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import type { FieldDefinition, FieldOption } from '@/modules/pm/types'
import { pmFieldApi } from '@/modules/pm/api'

const props = defineProps<{
  projectId: number
  typeCode: string
}>()

const message = useMessage()
const fields = ref<FieldDefinition[]>([])
const newField = ref<Partial<FieldDefinition>>({
  fieldKey: '',
  fieldName: '',
  fieldType: 'TEXT',
  requiredFlag: 0,
  sortOrder: 100,
  projectId: props.projectId,
  scope: 'project',
  applicableTypes: [props.typeCode],
})
const newOptions = ref<FieldOption[]>([])

const typeOptions = [
  { label: '文本', value: 'TEXT' },
  { label: '多行文本', value: 'TEXTAREA' },
  { label: '数字', value: 'NUMBER' },
  { label: '下拉', value: 'SELECT' },
  { label: '多选', value: 'MULTI_SELECT' },
  { label: '日期', value: 'DATE' },
  { label: '用户', value: 'USER' },
  { label: '布尔', value: 'BOOLEAN' },
]

async function load() {
  fields.value = await pmFieldApi.list(props.projectId, props.typeCode)
}

async function saveField() {
  await pmFieldApi.save(newField.value as FieldDefinition, newOptions.value)
  message.success('字段已保存')
  newField.value = {
    fieldKey: '',
    fieldName: '',
    fieldType: 'TEXT',
    requiredFlag: 0,
    sortOrder: 100,
    projectId: props.projectId,
    scope: 'project',
    applicableTypes: [props.typeCode],
  }
  newOptions.value = []
  await load()
}

onMounted(load)
</script>

<template>
  <n-space vertical size="large" style="width: 100%">
    <n-card title="已有字段" size="small">
      <n-list bordered>
        <n-list-item v-for="f in fields.filter((x) => x.systemFlag !== 1)" :key="f.fieldKey">
          {{ f.fieldName }} ({{ f.fieldKey }}) — {{ f.fieldType }}
        </n-list-item>
      </n-list>
    </n-card>
    <n-card title="新增自定义字段" size="small">
      <n-form label-placement="left" label-width="90">
        <n-form-item label="字段键">
          <n-input v-model:value="newField.fieldKey" placeholder="severity" />
        </n-form-item>
        <n-form-item label="显示名">
          <n-input v-model:value="newField.fieldName" placeholder="严重程度" />
        </n-form-item>
        <n-form-item label="类型">
          <n-select v-model:value="newField.fieldType" :options="typeOptions" />
        </n-form-item>
        <n-form-item label="必填">
          <n-switch :value="newField.requiredFlag === 1" @update:value="(v) => (newField.requiredFlag = v ? 1 : 0)" />
        </n-form-item>
        <n-form-item v-if="newField.fieldType === 'SELECT'" label="预览">
          <PmFieldRenderer :field="newField as FieldDefinition" :model-value="null" />
        </n-form-item>
        <n-button type="primary" @click="saveField">保存字段</n-button>
      </n-form>
    </n-card>
  </n-space>
</template>
