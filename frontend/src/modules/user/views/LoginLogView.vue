<script setup lang="ts">
import { h, onMounted } from 'vue'
import { NTag, NTooltip } from 'naive-ui'
import { loginLogApi } from '@/modules/user/api'
import type { LoginLog } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'

const keyword = ref('')
const action = ref('all')
const loading = ref(false)
const logs = ref<LoginLog[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const actionOptions = [
  { label: '全部', value: 'all' },
  { label: '登录成功', value: 'login_success' },
  { label: '登录失败', value: 'login_fail' },
  { label: '退出登录', value: 'logout' },
]

function actionLabel(value: string) {
  if (value === 'login_success') return '登录成功'
  if (value === 'login_fail') return '登录失败'
  if (value === 'logout') return '退出登录'
  return value
}

function actionTagType(value: string): 'success' | 'error' | 'default' | 'info' {
  if (value === 'login_success') return 'success'
  if (value === 'login_fail') return 'error'
  if (value === 'logout') return 'info'
  return 'default'
}

async function load() {
  loading.value = true
  try {
    const page = await loginLogApi.page({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      action: action.value,
    })
    logs.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number) {
  pageNo.value = page
  void load()
}

function onSearch() {
  pageNo.value = 1
  void load()
}

const columns = [
  {
    title: '用户',
    key: 'username',
    render: (row: LoginLog) =>
      h('div', [
        h('div', row.displayName || row.username),
        h('div', { style: 'font-size: 12px; color: #999' }, row.username),
      ]),
  },
  {
    title: '动作',
    key: 'action',
    width: 110,
    render: (row: LoginLog) =>
      h(NTag, { size: 'small', type: actionTagType(row.action), bordered: false }, {
        default: () => actionLabel(row.action),
      }),
  },
  {
    title: 'IP',
    key: 'loginIp',
    width: 140,
  },
  {
    title: '客户端',
    key: 'clientInfo',
    width: 160,
    render: (row: LoginLog) =>
      row.userAgent
        ? h(NTooltip, { trigger: 'hover' }, {
            trigger: () => h('span', row.clientInfo),
            default: () => row.userAgent,
          })
        : row.clientInfo,
  },
  {
    title: '失败原因',
    key: 'failReason',
    ellipsis: { tooltip: true },
    render: (row: LoginLog) => row.failReason || '-',
  },
  {
    title: '时间',
    key: 'createTime',
    width: 170,
    render: (row: LoginLog) => formatDateTime(row.createTime),
  },
]

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header title="登录日志" subtitle="记录登录成功、失败与退出行为，便于安全审计" />

    <n-space align="center">
      <n-input v-model:value="keyword" placeholder="搜索用户名/姓名" clearable style="width: 220px" />
      <n-select v-model:value="action" :options="actionOptions" style="width: 130px" />
      <n-button type="primary" @click="onSearch">查询</n-button>
      <n-button @click="load">刷新</n-button>
    </n-space>

    <n-data-table
      :columns="columns"
      :data="logs"
      :loading="loading"
      :row-key="(r: LoginLog) => String(r.id)"
      :pagination="{
        page: pageNo,
        pageSize,
        itemCount: total,
        onUpdatePage: onPageChange,
      }"
    />
  </n-space>
</template>
