<template>
  <div class="param-editor">
    <div class="param-editor__toolbar">
      <n-button size="tiny" @click="addParam('query')">添加 Query 参数</n-button>
      <n-button size="tiny" @click="addParam('header')">添加请求头</n-button>
      <n-button size="tiny" @click="addParam('path')">添加路径参数</n-button>
    </div>

    <n-tabs type="line" default-value="query" @update:value="onTabChange">
      <n-tab-pane v-for="tab in paramTabs" :key="tab.key" :name="tab.key" :tab="tab.label">
        <n-data-table
          :columns="paramColumns"
          :data="filteredParams(tab.key)"
          :bordered="false"
          :single-line="false"
          size="small"
          :max-height="360"
        />
      </n-tab-pane>
    </n-tabs>

    <div v-if="params.length === 0" class="param-editor__empty">
      <n-empty description="暂无参数" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { NButton, NSwitch, NInput, NSelect, NSpace, NIcon } from 'naive-ui'
import type { ApiDefinitionParamDTO, ParamType } from '@/modules/api-test/define/types/definition'
import { PARAM_DATA_TYPE_OPTIONS } from '@/modules/api-test/define/types/definition'

const props = defineProps<{
  params: ApiDefinitionParamDTO[]
}>()

const emit = defineEmits<{
  'update:params': [params: ApiDefinitionParamDTO[]]
}>()

const currentTab = computed(() => 'query')

const paramTabs: { key: ParamType; label: string }[] = [
  { key: 'query', label: 'Query 参数' },
  { key: 'header', label: '请求头' },
  { key: 'path', label: '路径参数' },
  { key: 'body', label: '请求体' },
]

function filteredParams(type: ParamType) {
  return props.params.filter((p) => p.paramType === type)
}

function addParam(type: ParamType) {
  const newParams = [...props.params]
  newParams.push({
    paramType: type,
    name: '',
    dataType: 'string',
    required: false,
    defaultValue: '',
    description: '',
    sortOrder: newParams.length,
  })
  emit('update:params', newParams)
}

function removeParam(index: number, type: ParamType) {
  const typeParams = filteredParams(type)
  const globalIndex = props.params.indexOf(typeParams[index])
  if (globalIndex !== -1) {
    const newParams = [...props.params]
    newParams.splice(globalIndex, 1)
    emit('update:params', newParams)
  }
}

function updateParam(index: number, type: ParamType, key: string, value: any) {
  const typeParams = filteredParams(type)
  const globalIndex = props.params.indexOf(typeParams[index])
  if (globalIndex !== -1) {
    const newParams = [...props.params]
    newParams[globalIndex] = { ...newParams[globalIndex], [key]: value }
    emit('update:params', newParams)
  }
}

function onTabChange(key: string) {
  // 如果切换到的 tab 没有数据，自动添加一个空行
  const type = key as ParamType
  if (filteredParams(type).length === 0) {
    addParam(type)
  }
}

// 重建表格列以响应父组件更新
const paramColumns = [
  {
    title: '参数名称',
    key: 'name',
    width: 180,
    render: (row: ApiDefinitionParamDTO, index: number) => {
      const typeParams = filteredParams(row.paramType)
      const localIndex = typeParams.indexOf(row)
      return h(NInput, {
        value: row.name,
        placeholder: '参数名称',
        size: 'small',
        onUpdateValue: (v: string) => updateParam(localIndex, row.paramType, 'name', v),
      })
    },
  },
  {
    title: '数据类型',
    key: 'dataType',
    width: 120,
    render: (row: ApiDefinitionParamDTO, index: number) => {
      const typeParams = filteredParams(row.paramType)
      const localIndex = typeParams.indexOf(row)
      return h(NSelect, {
        value: row.dataType,
        options: PARAM_DATA_TYPE_OPTIONS.map((o) => ({ label: o.label, value: o.value })),
        size: 'small',
        onUpdateValue: (v: string) => updateParam(localIndex, row.paramType, 'dataType', v),
      })
    },
  },
  {
    title: '必填',
    key: 'required',
    width: 70,
    render: (row: ApiDefinitionParamDTO, index: number) => {
      const typeParams = filteredParams(row.paramType)
      const localIndex = typeParams.indexOf(row)
      return h(NSwitch, {
        value: row.required,
        size: 'small',
        onUpdateValue: (v: boolean) => updateParam(localIndex, row.paramType, 'required', v),
      })
    },
  },
  {
    title: '默认值',
    key: 'defaultValue',
    width: 130,
    render: (row: ApiDefinitionParamDTO, index: number) => {
      const typeParams = filteredParams(row.paramType)
      const localIndex = typeParams.indexOf(row)
      return h(NInput, {
        value: row.defaultValue,
        placeholder: '默认值',
        size: 'small',
        onUpdateValue: (v: string) => updateParam(localIndex, row.paramType, 'defaultValue', v),
      })
    },
  },
  {
    title: '描述',
    key: 'description',
    width: 180,
    render: (row: ApiDefinitionParamDTO, index: number) => {
      const typeParams = filteredParams(row.paramType)
      const localIndex = typeParams.indexOf(row)
      return h(NInput, {
        value: row.description,
        placeholder: '描述',
        size: 'small',
        onUpdateValue: (v: string) => updateParam(localIndex, row.paramType, 'description', v),
      })
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 60,
    render: (row: ApiDefinitionParamDTO, index: number) => {
      const typeParams = filteredParams(row.paramType)
      const localIndex = typeParams.indexOf(row)
      return h(NButton, {
        size: 'tiny',
        text: true,
        type: 'error',
        onClick: () => removeParam(localIndex, row.paramType),
      }, { default: () => '删除' })
    },
  },
]
</script>

<style scoped>
.param-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-editor__toolbar {
  display: flex;
  gap: 8px;
}

.param-editor__empty {
  padding: 24px 0;
}
</style>