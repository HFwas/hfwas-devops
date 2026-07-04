<script setup lang="ts">
import { h, onMounted } from 'vue'
import { NTag, NTooltip } from 'naive-ui'
import { operLogApi } from '@/modules/user/api'
import type { OperLog } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const keyword = ref('')
const module = ref('all')
const action = ref('')
const loading = ref(false)
const logs = ref<OperLog[]>([])
const pagination = usePagination()

const moduleOptions = [
  { label: '全部模块', value: 'all' },
  { label: '用户中心', value: 'user' },
  { label: '项目管理', value: 'pm' },
]

const actionOptions = [
  { label: '全部动作', value: '' },
  { label: '保存', value: 'save' },
  { label: '删除', value: 'delete' },
  { label: '状态流转', value: 'transition' },
  { label: '强制下线', value: 'revoke' },
]

function moduleLabel(value: string) {
  if (value === 'user') return '用户中心'
  if (value === 'pm') return '项目管理'
  return value
}

function actionLabel(value: string) {
  const map: Record<string, string> = {
    save: '保存',
    delete: '删除',
    transition: '状态流转',
    revoke: '强制下线',
  }
  return map[value] ?? value
}

async function load() {
  loading.value = true
  try {
    const page = await operLogApi.page({
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
      module: module.value,
      action: action.value || undefined,
    })
    logs.value = page.records
    pagination.setTotal(page.total)
  } finally {
    loading.value = false
  }
}

const { tablePagination, handlePageChange, handlePageSizeChange } = useDataTablePagination(pagination, load)

function onSearch() {
  pagination.resetPage()
  void load()
}

const columns = [
  {
    title: '操作人',
    key: 'username',
    width: 140,
    render: (row: OperLog) =>
      h('div', [
        h('div', row.displayName || row.username || '-'),
        h('div', { style: 'font-size: 12px; color: #999' }, row.username ?? ''),
      ]),
  },
  {
    title: '模块',
    key: 'module',
    width: 100,
    render: (row: OperLog) =>
      h(NTag, { size: 'small', bordered: false }, { default: () => moduleLabel(row.module) }),
  },
  {
    title: '动作',
    key: 'action',
    width: 100,
    render: (row: OperLog) => actionLabel(row.action),
  },
  {
    title: '摘要',
    key: 'summary',
    ellipsis: { tooltip: true },
  },
  {
    title: '业务对象',
    key: 'bizType',
    width: 120,
    render: (row: OperLog) => {
      const type = row.bizType || '-'
      const id = row.bizId ? `#${row.bizId}` : ''
      return `${type}${id ? ' ' + id : ''}`
    },
  },
  {
    title: 'IP',
    key: 'requestIp',
    width: 130,
  },
  {
    title: '客户端',
    key: 'clientInfo',
    width: 150,
    render: (row: OperLog) =>
      row.userAgent
        ? h(NTooltip, { trigger: 'hover' }, {
            trigger: () => h('span', row.clientInfo),
            default: () => row.userAgent,
          })
        : row.clientInfo,
  },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row: OperLog) =>
      h(
        NTag,
        { size: 'small', type: row.status === 'success' ? 'success' : 'error', bordered: false },
        { default: () => (row.status === 'success' ? '成功' : '失败') },
      ),
  },
  {
    title: '时间',
    key: 'createTime',
    width: 170,
    render: (row: OperLog) => formatDateTime(row.createTime),
  },
]

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="操作日志"
      subtitle="记录用户中心与项目管理的关键写操作，供审计与追溯（参考 RuoYi / GitLab Audit Events）"
    />

    <n-space align="center">
      <n-input v-model:value="keyword" placeholder="搜索操作人/摘要/业务ID" clearable style="width: 240px" />
      <n-select v-model:value="module" :options="moduleOptions" style="width: 130px" />
      <n-select v-model:value="action" :options="actionOptions" style="width: 120px" />
      <n-button type="primary" @click="onSearch">查询</n-button>
      <n-button @click="load">刷新</n-button>
    </n-space>

    <n-data-table
      :columns="columns"
      :data="logs"
      :loading="loading"
      :row-key="(r: OperLog) => String(r.id)"
      :pagination="tablePagination"
      remote
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />
  </n-space>
</template>
