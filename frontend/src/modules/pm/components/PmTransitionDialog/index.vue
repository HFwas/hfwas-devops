<script setup lang="ts">
import { useMessage } from 'naive-ui'
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import { pmStatusApi, pmWorkItemApi } from '@/modules/pm/api'
import type { FieldDefinition, PmWorkItem, TransitionFieldMeta } from '@/modules/pm/types'
import { systemFieldProp } from '@/modules/pm/types'
import type { EntityId } from '@/modules/pm/utils/id'

const props = defineProps<{
  show: boolean
  projectId: EntityId
  typeCode: string
  item: PmWorkItem | null
  transitionId: string
  transitionName: string
  fromStatus: string
  fromStatusName: string
  toStatusName: string
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  success: []
}>()

const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const requiredFields = ref<TransitionFieldMeta[]>([])
const formValues = ref<Record<string, unknown>>({})

const visible = computed({
  get: () => props.show,
  set: (v) => emit('update:show', v),
})

const displayName = computed(() => props.transitionName || props.toStatusName)

function toFieldDefinition(meta: TransitionFieldMeta): FieldDefinition {
  return {
    fieldKey: meta.fieldKey,
    fieldName: meta.fieldName,
    fieldType: meta.fieldType as FieldDefinition['fieldType'],
    systemFlag: meta.systemFlag ?? 0,
    requiredFlag: 1,
  }
}

function readCurrentValue(fieldKey: string): unknown {
  if (!props.item) return undefined
  const prop = systemFieldProp(fieldKey)
  const fromItem = (props.item as unknown as Record<string, unknown>)[prop]
  if (fromItem !== undefined) return fromItem
  return props.item.customFields?.[fieldKey]
}

function isEmpty(value: unknown) {
  if (value == null) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

watch(
  () => props.show,
  async (show) => {
    if (!show || !props.item || !props.transitionId) return
    loading.value = true
    formValues.value = {}
    try {
      const meta = await pmStatusApi.transitionMeta(
        props.projectId,
        props.typeCode,
        props.transitionId,
        props.fromStatus,
      )
      requiredFields.value = meta.requiredFields ?? []
      const values: Record<string, unknown> = {}
      for (const field of requiredFields.value) {
        values[field.fieldKey] = readCurrentValue(field.fieldKey)
      }
      formValues.value = values
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载流转校验失败')
      visible.value = false
    } finally {
      loading.value = false
    }
  },
)

async function submit() {
  if (!props.item?.id || !props.transitionId) return
  for (const field of requiredFields.value) {
    if (isEmpty(formValues.value[field.fieldKey])) {
      message.warning(`请填写「${field.fieldName}」`)
      return
    }
  }
  submitting.value = true
  try {
    const fields: Record<string, unknown> = {}
    for (const field of requiredFields.value) {
      fields[field.fieldKey] = formValues.value[field.fieldKey]
    }
    await pmWorkItemApi.transition(props.item.id, {
      transitionId: props.transitionId,
      fields,
    })
    message.success(`已执行「${displayName.value}」`)
    visible.value = false
    emit('success')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '状态流转失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="确认流转"
    style="width: 480px"
    :mask-closable="false"
  >
    <n-spin :show="loading">
      <n-space vertical size="large">
        <n-space vertical :size="4">
          <n-text v-if="transitionName" strong>{{ transitionName }}</n-text>
          <n-space align="center" :size="8">
            <n-tag size="small" :bordered="false">{{ fromStatusName }}</n-tag>
            <n-text depth="3">→</n-text>
            <n-tag size="small" type="success" :bordered="false">{{ toStatusName }}</n-tag>
          </n-space>
        </n-space>
        <n-alert type="info" :bordered="false">
          执行「{{ displayName }}」前，请填写以下必填字段。
        </n-alert>
        <n-form label-placement="top">
          <n-form-item
            v-for="field in requiredFields"
            :key="field.fieldKey"
            :label="field.fieldName"
            required
          >
            <PmFieldRenderer
              :field="toFieldDefinition(field)"
              mode="edit"
              :project-id="projectId"
              :type-code="typeCode"
              :model-value="formValues[field.fieldKey]"
              @update:model-value="(v) => (formValues[field.fieldKey] = v)"
            />
          </n-form-item>
        </n-form>
        <n-empty v-if="!loading && !requiredFields.length" description="无需额外填写，可直接确认" />
      </n-space>
    </n-spin>
    <template #footer>
      <n-space justify="end">
        <n-button @click="visible = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="submit">确认流转</n-button>
      </n-space>
    </template>
  </n-modal>
</template>
