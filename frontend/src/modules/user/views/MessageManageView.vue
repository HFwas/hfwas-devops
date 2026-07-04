<script setup lang="ts">
import { h, onMounted } from 'vue'
import { NTag, useMessage } from 'naive-ui'
import { messageApi, tenantManageApi, userManageApi } from '@/modules/user/api'
import type { Tenant, UserMessage, UserProfile } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const message = useMessage()
const keyword = ref('')
const category = ref('all')
const userFilter = ref<number | string | null>(null)
const loading = ref(false)
const messages = ref<UserMessage[]>([])
const pagination = usePagination()
const tenantOptions = ref<Tenant[]>([])
const userOptions = ref<UserProfile[]>([])
const showSend = ref(false)

const sendForm = ref({
  targetType: 'all' as 'all' | 'tenant' | 'users',
  tenantId: null as number | string | null,
  userIds: [] as (number | string)[],
  category: 'announcement',
  title: '',
  content: '',
  linkUrl: '',
})

async function load() {
  loading.value = true
  try {
    const page = await messageApi.adminPage({
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
      category: category.value,
      userId: userFilter.value ?? undefined,
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

async function sendMessage() {
  try {
    await messageApi.adminSend({
      targetType: sendForm.value.targetType,
      tenantId: sendForm.value.tenantId ?? undefined,
      userIds: sendForm.value.userIds,
      category: sendForm.value.category,
      title: sendForm.value.title.trim(),
      content: sendForm.value.content.trim(),
      linkUrl: sendForm.value.linkUrl.trim() || undefined,
    })
    message.success('发送成功')
    showSend.value = false
    sendForm.value = {
      targetType: 'all',
      tenantId: null,
      userIds: [],
      category: 'announcement',
      title: '',
      content: '',
      linkUrl: '',
    }
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发送失败')
  }
}

const columns = [
  { title: '接收用户', key: 'displayName', render: (row: UserMessage) => row.displayName || row.username || '-' },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
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
]

onMounted(async () => {
  tenantOptions.value = await tenantManageApi.options()
  const userPage = await userManageApi.page({ pageNo: 1, pageSize: 200 })
  userOptions.value = userPage.records
  await load()
})
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="消息管理"
      subtitle="统一查看全平台消息记录，并向用户/租户/全员发送消息"
    />

    <n-space>
      <n-select
        v-model:value="userFilter"
        clearable
        placeholder="筛选接收用户"
        :options="userOptions.map((u) => ({ label: `${u.displayName} (${u.username})`, value: u.id! }))"
        style="width: 220px"
      />
      <n-select
        v-model:value="category"
        :options="[
          { label: '全部分类', value: 'all' },
          { label: '系统通知', value: 'system' },
          { label: '操作通知', value: 'operation' },
          { label: '公告', value: 'announcement' },
        ]"
        style="width: 130px"
      />
      <n-input v-model:value="keyword" placeholder="搜索标题/内容" clearable style="width: 220px" />
      <n-button @click="onSearch">查询</n-button>
      <n-button type="primary" @click="showSend = true">发送消息</n-button>
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

    <n-modal v-model:show="showSend" preset="card" title="发送消息" style="width: 560px">
      <n-form label-placement="top">
        <n-form-item label="发送目标" required>
          <n-radio-group v-model:value="sendForm.targetType">
            <n-space>
              <n-radio value="all">全部用户</n-radio>
              <n-radio value="tenant">指定租户</n-radio>
              <n-radio value="users">指定用户</n-radio>
            </n-space>
          </n-radio-group>
        </n-form-item>
        <n-form-item v-if="sendForm.targetType === 'tenant'" label="目标租户" required>
          <n-select
            v-model:value="sendForm.tenantId"
            :options="tenantOptions.map((t) => ({ label: t.name, value: t.id! }))"
          />
        </n-form-item>
        <n-form-item v-if="sendForm.targetType === 'users'" label="目标用户" required>
          <n-select
            v-model:value="sendForm.userIds"
            multiple
            filterable
            :options="userOptions.map((u) => ({ label: `${u.displayName} (${u.username})`, value: u.id! }))"
          />
        </n-form-item>
        <n-form-item label="消息分类">
          <n-select
            v-model:value="sendForm.category"
            :options="[
              { label: '公告', value: 'announcement' },
              { label: '系统通知', value: 'system' },
            ]"
          />
        </n-form-item>
        <n-form-item label="标题" required>
          <n-input v-model:value="sendForm.title" />
        </n-form-item>
        <n-form-item label="内容">
          <n-input v-model:value="sendForm.content" type="textarea" :rows="4" />
        </n-form-item>
        <n-form-item label="跳转链接（可选）">
          <n-input v-model:value="sendForm.linkUrl" placeholder="/pm/projects" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showSend = false">取消</n-button>
          <n-button type="primary" @click="sendMessage">发送</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-space>
</template>
