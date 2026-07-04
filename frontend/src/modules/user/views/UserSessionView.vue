<script setup lang="ts">
import { h, onMounted, onUnmounted } from 'vue'
import { NButton, NPopconfirm, NTag, NTooltip, useMessage } from 'naive-ui'
import { userSessionApi } from '@/modules/user/api'
import type { UserSession, UserSessionStats } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useRouter } from 'vue-router'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const message = useMessage()
const auth = useAuthStore()
const router = useRouter()

const keyword = ref('')
const status = ref<'all' | 'online' | 'idle'>('all')
const loading = ref(false)
const sessions = ref<UserSession[]>([])
const pagination = usePagination()
const stats = ref<UserSessionStats>({ onlineCount: 0, idleCount: 0, totalActive: 0 })

let refreshTimer: ReturnType<typeof setInterval> | null = null

async function loadStats() {
  stats.value = await userSessionApi.stats()
}

async function loadSessions() {
  loading.value = true
  try {
    const page = await userSessionApi.page({
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value,
    })
    sessions.value = page.records
    pagination.setTotal(page.total)
  } finally {
    loading.value = false
  }
}

const { tablePagination, handlePageChange, handlePageSizeChange } = useDataTablePagination(pagination, loadSessions)

function onSearch() {
  pagination.resetPage()
  void loadAll()
}

async function loadAll() {
  await Promise.all([loadStats(), loadSessions()])
}

async function revokeSession(row: UserSession) {
  await userSessionApi.revoke(row.id)
  message.success('已强制下线')
  if (row.current) {
    auth.logout()
    await router.push('/user/login')
    return
  }
  await loadAll()
}

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '在线', value: 'online' },
  { label: '空闲', value: 'idle' },
]

const columns = [
  {
    title: '用户',
    key: 'username',
    render: (row: UserSession) =>
      h('div', [
        h('div', row.displayName || row.username),
        h('div', { style: 'font-size: 12px; color: #999' }, row.username),
      ]),
  },
  {
    title: '角色',
    key: 'role',
    width: 100,
    render: (row: UserSession) =>
      h(
        NTag,
        { size: 'small', type: row.role === 'admin' ? 'warning' : 'default', bordered: false },
        { default: () => (row.role === 'admin' ? '管理员' : '普通用户') },
      ),
  },
  {
    title: '登录 IP',
    key: 'loginIp',
    width: 140,
  },
  {
    title: '客户端',
    key: 'clientInfo',
    width: 160,
    render: (row: UserSession) =>
      row.userAgent
        ? h(NTooltip, { trigger: 'hover' }, {
            trigger: () => h('span', row.clientInfo),
            default: () => row.userAgent,
          })
        : row.clientInfo,
  },
  {
    title: '登录时间',
    key: 'loginTime',
    width: 170,
    render: (row: UserSession) => formatDateTime(row.loginTime),
  },
  {
    title: '最后活跃',
    key: 'lastActiveTime',
    width: 170,
    render: (row: UserSession) => formatDateTime(row.lastActiveTime),
  },
  {
    title: '状态',
    key: 'onlineStatus',
    width: 100,
    render: (row: UserSession) => {
      const online = row.onlineStatus === 'online'
      return h(
        NTag,
        { size: 'small', type: online ? 'success' : 'default', bordered: false },
        { default: () => (online ? '在线' : '空闲') },
      )
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row: UserSession) =>
      h('div', { style: 'display: flex; align-items: center; gap: 8px' }, [
        row.current ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => '当前会话' }) : null,
        h(
          NPopconfirm,
          { onPositiveClick: () => revokeSession(row) },
          {
            trigger: () => h(NButton, { text: true, type: 'error', size: 'small' }, { default: () => '强制下线' }),
            default: () => `确认强制下线「${row.displayName || row.username}」？`,
          },
        ),
      ]),
  },
]

onMounted(() => {
  void loadAll()
  refreshTimer = setInterval(() => {
    void loadAll()
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<template>
  <n-space vertical size="large">
    <n-page-header title="在线会话" subtitle="查看当前有效登录会话，支持强制下线（参考 Keycloak / GitLab 会话管理）" />

    <n-grid cols="1 s:3" responsive="screen" :x-gap="16" :y-gap="16">
      <n-gi>
        <n-card size="small">
          <n-statistic label="在线用户" :value="stats.onlineCount">
            <template #suffix>人</template>
          </n-statistic>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card size="small">
          <n-statistic label="空闲会话" :value="stats.idleCount">
            <template #suffix>个</template>
          </n-statistic>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card size="small">
          <n-statistic label="有效会话总数" :value="stats.totalActive">
            <template #suffix>个</template>
          </n-statistic>
        </n-card>
      </n-gi>
    </n-grid>

    <n-space align="center">
      <n-input v-model:value="keyword" placeholder="搜索用户名/姓名" clearable style="width: 220px" />
      <n-select v-model:value="status" :options="statusOptions" style="width: 120px" />
      <n-button @click="onSearch">查询</n-button>
      <n-button @click="loadAll">刷新</n-button>
      <n-text depth="3">每 30 秒自动刷新 · 5 分钟无活动视为空闲</n-text>
    </n-space>

    <n-data-table
      :columns="columns"
      :data="sessions"
      :loading="loading"
      :row-key="(r: UserSession) => String(r.id)"
      :pagination="tablePagination"
      remote
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />
  </n-space>
</template>
