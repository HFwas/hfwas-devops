<script setup lang="ts">
import { h, onMounted } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import { messageApi } from '@/modules/user/api'
import type { UserMessage } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const router = useRouter()
const message = useMessage()
const readFlag = ref<'all' | 'unread' | 'read'>('all')
const category = ref('all')
const keyword = ref('')
const loading = ref(false)
const messages = ref<UserMessage[]>([])
const pagination = usePagination()
const selected = ref<UserMessage | null>(null)
const showDetail = ref(false)

const categoryOptions = [
  { label: '全部分类', value: 'all' },
  { label: '系统通知', value: 'system' },
  { label: '操作通知', value: 'operation' },
  { label: '公告', value: 'announcement' },
]

async function load() {
  loading.value = true
  try {
    const page = await messageApi.page({
      ...pagination.query.value,
      readFlag: readFlag.value,
      category: category.value,
      keyword: keyword.value.trim() || undefined,
    })
    messages.value = page.records
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

async function openDetail(row: UserMessage) {
  if (row.id == null) return
  selected.value = await messageApi.detail(row.id)
  if (selected.value.readFlag === 0) {
    await messageApi.markRead(row.id)
    row.readFlag = 1
  }
  showDetail.value = true
}

async function markAllRead() {
  await messageApi.markAllRead()
  message.success('已全部标为已读')
  await load()
}

async function removeMessage(row: UserMessage) {
  if (row.id == null) return
  await messageApi.delete(row.id)
  message.success('已删除')
  await load()
}

function followLink() {
  if (selected.value?.linkUrl) {
    showDetail.value = false
    void router.push(selected.value.linkUrl)
  }
}

const columns = [
  {
    title: '标题',
    key: 'title',
    render: (row: UserMessage) =>
      h('span', { style: row.readFlag === 0 ? 'font-weight:600' : '' }, row.title),
  },
  {
    title: '分类',
    key: 'category',
    width: 110,
    render: (row: UserMessage) =>
      h(NTag, { size: 'small', bordered: false }, () => row.categoryLabel || row.category),
  },
  {
    title: '状态',
    key: 'readFlag',
    width: 80,
    render: (row: UserMessage) => (row.readFlag === 1 ? '已读' : '未读'),
  },
  {
    title: '时间',
    key: 'createTime',
    width: 170,
    render: (row: UserMessage) => formatDateTime(row.createTime),
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row: UserMessage) =>
      h('div', { style: 'display:flex;gap:8px' }, [
        h(NButton, { text: true, type: 'primary', onClick: () => openDetail(row) }, () => '查看'),
        h(NButton, { text: true, type: 'error', onClick: () => removeMessage(row) }, () => '删除'),
      ]),
  },
]

onMounted(load)
</script>

<template>
  <n-space vertical size="large" style="padding: 20px; max-width: 1100px; margin: 0 auto">
    <n-page-header title="我的消息" subtitle="查看系统通知、操作提醒与公告" />

    <n-space>
      <n-select
        v-model:value="readFlag"
        :options="[
          { label: '全部', value: 'all' },
          { label: '未读', value: 'unread' },
          { label: '已读', value: 'read' },
        ]"
        style="width: 120px"
      />
      <n-select v-model:value="category" :options="categoryOptions" style="width: 130px" />
      <n-input v-model:value="keyword" placeholder="搜索标题/内容" clearable style="width: 220px" />
      <n-button type="primary" @click="onSearch">查询</n-button>
      <n-button @click="markAllRead">全部标为已读</n-button>
    </n-space>

    <n-data-table
      :columns="columns"
      :data="messages"
      :loading="loading"
      :row-key="(r: UserMessage) => String(r.id)"
      :pagination="tablePagination"
      remote
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />

    <n-drawer v-model:show="showDetail" :width="480" placement="right">
      <n-drawer-content :title="selected?.title ?? '消息详情'" closable>
        <n-space v-if="selected" vertical size="medium">
          <n-descriptions :column="1" label-placement="left" bordered size="small">
            <n-descriptions-item label="分类">{{ selected.categoryLabel }}</n-descriptions-item>
            <n-descriptions-item label="发送方">{{ selected.senderName || '系统' }}</n-descriptions-item>
            <n-descriptions-item label="时间">{{ formatDateTime(selected.createTime) }}</n-descriptions-item>
          </n-descriptions>
          <n-text style="white-space: pre-wrap">{{ selected.content }}</n-text>
          <n-button v-if="selected.linkUrl" type="primary" @click="followLink">查看相关页面</n-button>
        </n-space>
      </n-drawer-content>
    </n-drawer>
  </n-space>
</template>
