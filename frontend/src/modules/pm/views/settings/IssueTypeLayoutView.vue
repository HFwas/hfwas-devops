<script setup lang="ts">
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import PmFieldEditorDrawer from '@/modules/pm/components/PmFieldEditorDrawer/index.vue'
import { pmFieldApi, pmFieldLayoutApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { FieldDefinition, TypeFieldLayoutConfig } from '@/modules/pm/types'
import { FIELD_TYPE_LABELS, TYPE_META } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => routeId(route.params.projectId))
const typeCode = computed(() => String(route.params.typeCode))
const typeLabel = computed(() => TYPE_META[typeCode.value]?.label ?? typeCode.value)

const fields = ref<FieldDefinition[]>([])
const layout = ref<TypeFieldLayoutConfig>({ listFields: [], searchFields: [], createFields: [] })
const loading = ref(false)
const saving = ref(false)
const ready = ref(false)

const showDrawer = ref(false)
const editingId = ref<string | null>(null)
const showPickModal = ref(false)
const availableFields = ref<FieldDefinition[]>([])
const pickingFieldId = ref<string | null>(null)
const loadingAvailable = ref(false)

const configurableFields = computed(() => fields.value.filter((f) => f.fieldKey !== 'type_code'))

function fieldId(row: FieldDefinition) {
  return row.id == null ? '' : String(row.id)
}

function isCustomField(field: FieldDefinition) {
  return field.systemFlag !== 1 && !!field.id
}

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

function openCreateField() {
  editingId.value = null
  showDrawer.value = true
}

function openEditField(field: FieldDefinition) {
  const id = fieldId(field)
  if (!id) return
  editingId.value = id
  showDrawer.value = true
}

async function openPickExisting() {
  loadingAvailable.value = true
  showPickModal.value = true
  pickingFieldId.value = null
  try {
    availableFields.value = await pmFieldApi.listAvailable(projectId.value, typeCode.value)
  } finally {
    loadingAvailable.value = false
  }
}

async function confirmPickExisting() {
  if (!pickingFieldId.value) {
    message.warning('请选择要添加的字段')
    return
  }
  saving.value = true
  try {
    await pmFieldApi.addToType(projectId.value, pickingFieldId.value, typeCode.value)
    message.success('字段已添加到该事项类型')
    showPickModal.value = false
    await load()
    await fieldStore.loadSchema(projectId.value, typeCode.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    saving.value = false
  }
}

async function removeField(field: FieldDefinition) {
  const id = fieldId(field)
  if (!id) return
  saving.value = true
  try {
    await pmFieldApi.removeFromType(projectId.value, id, typeCode.value)
    message.success('字段已移除')
    await load()
    await fieldStore.loadSchema(projectId.value, typeCode.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '移除失败')
  } finally {
    saving.value = false
  }
}

async function onFieldSaved(savedId?: string) {
  showDrawer.value = false
  await load()
  if (savedId) {
    const field = fields.value.find((f) => fieldId(f) === savedId)
    if (field) {
      const key = field.fieldKey
      const next = { ...layout.value }
      let changed = false
      if (!next.createFields.includes(key)) {
        next.createFields = [...next.createFields, key]
        changed = true
      }
      if (!next.listFields.includes(key)) {
        next.listFields = [...next.listFields, key]
        changed = true
      }
      if (changed) {
        layout.value = next
        await persistLayout()
      }
    }
  }
  await fieldStore.loadSchema(projectId.value, typeCode.value)
}

function goBack() {
  router.push(`/pm/projects/${projectId.value}/settings/types`)
}

const availableOptions = computed(() =>
  availableFields.value.map((f) => ({
    label: `${f.fieldName} (${f.fieldKey})`,
    value: fieldId(f),
  })),
)

watch(typeCode, load, { immediate: true })
</script>

<template>
  <n-space vertical size="large">
    <n-page-header :title="`${typeLabel} — 字段方案`" @back="goBack">
      <template #subtitle>
        配置该事项类型下的字段及在列表、搜索、新建中的启用状态
      </template>
      <template #extra>
        <n-space>
          <n-button @click="openPickExisting">从字段库添加</n-button>
          <n-button type="primary" @click="openCreateField">新建字段</n-button>
        </n-space>
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
              <th style="width: 120px">操作</th>
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
              <td>
                <n-space v-if="isCustomField(field)" :size="4">
                  <n-button text type="primary" size="small" @click="openEditField(field)">编辑</n-button>
                  <n-popconfirm @positive-click="removeField(field)">
                    <template #trigger>
                      <n-button text type="error" size="small">移除</n-button>
                    </template>
                    确定从「{{ typeLabel }}」移除此字段吗？若仅适用本类型将一并删除字段定义。
                  </n-popconfirm>
                </n-space>
                <n-text v-else depth="3">—</n-text>
              </td>
            </tr>
          </tbody>
        </n-table>
      </n-card>
    </n-spin>

    <PmFieldEditorDrawer
      :key="`${showDrawer}-${editingId ?? 'new'}`"
      v-model:show="showDrawer"
      :project-id="projectId"
      :field-id="editingId"
      :default-type-code="typeCode"
      lock-type-code
      @saved="onFieldSaved"
    />

    <n-modal v-model:show="showPickModal" preset="card" title="从字段库添加" style="width: 480px">
      <n-spin :show="loadingAvailable">
        <n-empty v-if="!availableFields.length && !loadingAvailable" description="暂无可用字段，请先在「字段」页新建" />
        <n-select
          v-else
          v-model:value="pickingFieldId"
          :options="availableOptions"
          placeholder="选择要添加的自定义字段"
          filterable
        />
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPickModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" :disabled="!pickingFieldId" @click="confirmPickExisting">
            添加
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </n-space>
</template>
