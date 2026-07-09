<script setup lang="ts">
import { h, nextTick } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import PmFieldEditorDrawer from '@/modules/pm/components/PmFieldEditorDrawer/index.vue'
import { pmFieldApi } from '@/modules/pm/api'
import type { FieldDefinition } from '@/modules/pm/types'
import { FIELD_TYPE_LABELS, TYPE_META } from '@/modules/pm/types'

import { routeId } from '@/modules/pm/utils/id'

const route = useRoute()
const projectId = computed(() => routeId(route.params.projectId))
const message = useMessage()

const fields = ref<FieldDefinition[]>([])
const loading = ref(false)
const keyword = ref('')
const showDrawer = ref(false)
const editingId = ref<number | string | null>(null)
const tableKey = ref(0)

const filteredFields = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return fields.value
  return fields.value.filter(
    (f) => f.fieldName.toLowerCase().includes(kw) || f.fieldKey.toLowerCase().includes(kw),
  )
})

function fieldId(row: FieldDefinition) {
  return row.id == null ? '' : String(row.id)
}

const columns = computed(() => [
  {
    title: '字段名称',
    key: 'fieldName',
    render: (row: FieldDefinition) =>
      h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
        h('span', row.fieldName),
        row.systemFlag === 1 ? h(NTag, { size: 'small', bordered: false }, () => '系统') : null,
      ]),
  },
  { title: '字段编码', key: 'fieldKey' },
  {
    title: '字段类型',
    key: 'fieldType',
    render: (row: FieldDefinition) => FIELD_TYPE_LABELS[row.fieldType] ?? row.fieldType,
  },
  {
    title: '适用事项',
    key: 'applicableTypes',
    render: (row: FieldDefinition) => {
      if (row.systemFlag === 1) return '全部'
      const types = row.applicableTypes ?? []
      if (!types.length) return '-'
      return types.map((c) => TYPE_META[c]?.label ?? c).join('、')
    },
  },
  {
    title: '必填',
    key: 'requiredFlag',
    width: 70,
    render: (row: FieldDefinition) => (row.requiredFlag === 1 ? '是' : '否'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row: FieldDefinition) => {
      if (row.systemFlag === 1) {
        return h('span', { style: 'color: var(--n-text-color-3)' }, '不可编辑')
      }
      const id = fieldId(row)
      return h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
        h(NButton, { text: true, type: 'primary', onClick: () => openEdit(id) }, () => '编辑'),
        h(
          NPopconfirm,
          {
            onPositiveClick: () => removeField(row),
          },
          {
            trigger: () => h(NButton, { text: true, type: 'error' }, () => '删除'),
            default: () => `确定删除字段「${row.fieldName || row.fieldKey || '未命名字段'}」吗？`,
          },
        ),
      ])
    },
  },
])

async function load() {
  loading.value = true
  try {
    fields.value = await pmFieldApi.catalog(projectId.value)
    tableKey.value += 1
    await nextTick()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  showDrawer.value = true
}

function openEdit(id: string) {
  editingId.value = id
  showDrawer.value = true
}

async function removeField(row: FieldDefinition) {
  const id = fieldId(row)
  if (!id) {
    message.error('字段 ID 无效')
    return false
  }
  try {
    await pmFieldApi.delete(id)
    fields.value = fields.value.filter((f) => f !== row)
    tableKey.value += 1
    message.success('字段已删除')
    await load()
    return true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
    return false
  }
}

async function onSaved() {
  showDrawer.value = false
  await load()
}

watch(projectId, load, { immediate: true })
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="自定义字段"
      subtitle="管理项目级自定义字段，定义字段类型与适用事项"
    >
      <template #extra>
        <n-button type="primary" @click="openCreate">新建字段</n-button>
      </template>
    </n-page-header>
    <n-card size="small">
      <n-space vertical>
        <n-input
          v-model:value="keyword"
          placeholder="搜索字段名称或编码"
          clearable
          style="max-width: 320px"
        />
        <n-data-table
          :key="tableKey"
          :columns="columns"
          :data="filteredFields"
          :loading="loading"
          :row-key="(r: FieldDefinition) => fieldId(r) || r.fieldKey"
        />
      </n-space>
    </n-card>
    <PmFieldEditorDrawer
      :key="`${showDrawer}-${editingId ?? 'new'}`"
      v-model:show="showDrawer"
      :project-id="projectId"
      :field-id="editingId"
      @saved="onSaved"
    />
  </n-space>
</template>
