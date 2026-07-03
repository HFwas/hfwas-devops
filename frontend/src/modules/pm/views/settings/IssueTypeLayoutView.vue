<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmFieldApi, pmFieldLayoutApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { FieldDefinition, TypeFieldLayoutConfig } from '@/modules/pm/types'
import { FIELD_TYPE_LABELS, TYPE_META } from '@/modules/pm/types'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => Number(route.params.projectId))
const typeCode = computed(() => String(route.params.typeCode))
const typeLabel = computed(() => TYPE_META[typeCode.value]?.label ?? typeCode.value)

const fields = ref<FieldDefinition[]>([])
const layout = ref<TypeFieldLayoutConfig>({ listFields: [], searchFields: [], createFields: [] })
const loading = ref(false)
const saving = ref(false)
const ready = ref(false)

const configurableFields = computed(() => fields.value.filter((f) => f.fieldKey !== 'type_code'))

function isChecked(list: string[], key: string) {
  return list.includes(key)
}

async function toggle(listKey: keyof TypeFieldLayoutConfig, fieldKey: string, checked: boolean) {
  const list = [...layout.value[listKey]]
  const idx = list.indexOf(fieldKey)
  if (checked && idx < 0) list.push(fieldKey)
  if (!checked && idx >= 0) list.splice(idx, 1)
  layout.value = { ...layout.value, [listKey]: list }
  await persistLayout()
}

async function load() {
  loading.value = true
  ready.value = false
  try {
    fields.value = await pmFieldApi.list(projectId.value, typeCode.value)
    layout.value = await pmFieldLayoutApi.get(projectId.value, typeCode.value)
  } finally {
    loading.value = false
    ready.value = true
  }
}

async function persistLayout() {
  if (!ready.value) return
  saving.value = true
  try {
    await pmFieldLayoutApi.save(projectId.value, typeCode.value, layout.value)
    await fieldStore.loadSchema(projectId.value, typeCode.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
    await load()
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push(`/pm/projects/${projectId.value}/settings/types`)
}

watch(typeCode, load, { immediate: true })
</script>

<template>
  <n-space vertical size="large">
    <n-page-header :title="`${typeLabel} — 字段方案`" @back="goBack">
      <template #subtitle>
        配置该事项类型下各字段在列表、搜索、新建中的启用状态，修改后自动保存
      </template>
    </n-page-header>

    <n-spin :show="loading || saving">
      <n-card size="small">
        <n-table :bordered="false" size="small" :single-line="false">
          <thead>
            <tr>
              <th>字段名称</th>
              <th>字段编码</th>
              <th>类型</th>
              <th style="width: 90px">列表</th>
              <th style="width: 90px">搜索</th>
              <th style="width: 90px">新建</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="field in configurableFields" :key="field.fieldKey">
              <td>
                <n-space align="center" :size="8">
                  {{ field.fieldName }}
                  <n-tag v-if="field.systemFlag === 1" size="small" :bordered="false">系统</n-tag>
                </n-space>
              </td>
              <td><n-text depth="3">{{ field.fieldKey }}</n-text></td>
              <td>{{ FIELD_TYPE_LABELS[field.fieldType] ?? field.fieldType }}</td>
              <td>
                <n-switch
                  size="small"
                  :disabled="saving"
                  :value="isChecked(layout.listFields, field.fieldKey)"
                  @update:value="(v) => toggle('listFields', field.fieldKey, v)"
                />
              </td>
              <td>
                <n-switch
                  size="small"
                  :disabled="saving"
                  :value="isChecked(layout.searchFields, field.fieldKey)"
                  @update:value="(v) => toggle('searchFields', field.fieldKey, v)"
                />
              </td>
              <td>
                <n-switch
                  size="small"
                  :disabled="saving"
                  :value="isChecked(layout.createFields, field.fieldKey)"
                  @update:value="(v) => toggle('createFields', field.fieldKey, v)"
                />
              </td>
            </tr>
          </tbody>
        </n-table>
      </n-card>
    </n-spin>
  </n-space>
</template>
