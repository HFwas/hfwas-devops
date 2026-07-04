<script setup lang="ts">
import { h } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import { tenantManageApi, userManageApi } from '@/modules/user/api'
import type { Tenant, UserProfile } from '@/modules/user/types'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const message = useMessage()

const keyword = ref('')
const tenantFilter = ref<number | string | null>(null)
const tenantOptions = ref<Tenant[]>([])
const loading = ref(false)
const users = ref<UserProfile[]>([])
const pagination = usePagination()
const showEditor = ref(false)
const editing = ref<UserProfile | null>(null)

const form = ref({
  username: '',
  displayName: '',
  email: '',
  phone: '',
  role: 'user',
  enabled: 1,
  password: '',
})

async function loadTenants() {
  tenantOptions.value = await tenantManageApi.options()
}

async function load() {
  loading.value = true
  try {
    const page = await userManageApi.page({
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
      tenantId: tenantFilter.value ?? undefined,
    })
    users.value = page.records
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

function openCreate() {
  editing.value = null
  form.value = { username: '', displayName: '', email: '', phone: '', role: 'user', enabled: 1, password: '' }
  showEditor.value = true
}

function openEdit(row: UserProfile) {
  editing.value = row
  form.value = {
    username: row.username,
    displayName: row.displayName,
    email: row.email ?? '',
    phone: row.phone ?? '',
    role: row.role,
    enabled: row.enabled ?? 1,
    password: '',
  }
  showEditor.value = true
}

async function saveUser() {
  try {
    await userManageApi.save({
      id: editing.value?.id,
      username: form.value.username.trim(),
      displayName: form.value.displayName.trim(),
      email: form.value.email.trim() || undefined,
      phone: form.value.phone.trim() || undefined,
      role: form.value.role,
      enabled: form.value.enabled,
      password: form.value.password || undefined,
    })
    message.success(editing.value ? '已更新' : '已创建')
    showEditor.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function removeUser(row: UserProfile) {
  if (row.id == null) return
  try {
    await userManageApi.delete(row.id)
    message.success('已删除')
    pagination.afterDelete(users.value.length)
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

const columns = [
  { title: '用户名', key: 'username' },
  { title: '显示名称', key: 'displayName' },
  {
    title: '已加入租户',
    key: 'tenantNames',
    render: (row: UserProfile) =>
      row.tenantNames?.length ? row.tenantNames.join('、') : h(NTag, { size: 'small' }, () => '未加入租户'),
  },
  { title: '邮箱', key: 'email', render: (row: UserProfile) => row.email ?? '-' },
  {
    title: '平台角色',
    key: 'role',
    render: (row: UserProfile) =>
      h(NTag, { size: 'small', type: row.role === 'admin' ? 'warning' : 'default' }, () =>
        row.role === 'admin' ? '平台管理员' : '普通用户',
      ),
  },
  {
    title: '状态',
    key: 'enabled',
    render: (row: UserProfile) => (row.enabled === 1 ? '启用' : '禁用'),
  },
  {
    title: '操作',
    key: 'actions',
    render: (row: UserProfile) =>
      h('div', { style: 'display:flex;gap:8px' }, [
        h(NButton, { text: true, type: 'primary', onClick: () => openEdit(row) }, () => '编辑'),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeUser(row) },
          {
            trigger: () => h(NButton, { text: true, type: 'error' }, () => '删除'),
            default: () => '确定删除该用户吗？',
          },
        ),
      ]),
  },
]

onMounted(async () => {
  await loadTenants()
  await load()
})
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="账号管理"
      subtitle="管理平台级用户账号；用户需加入租户后，方可在该租户项目中使用"
    />
    <n-space>
      <n-select
        v-model:value="tenantFilter"
        clearable
        placeholder="筛选已加入某租户的用户"
        :options="tenantOptions.map((t) => ({ label: t.name, value: t.id! }))"
        style="width: 200px"
      />
      <n-input v-model:value="keyword" placeholder="搜索用户名/姓名/邮箱" clearable style="width: 260px" />
      <n-button @click="onSearch">查询</n-button>
      <n-button type="primary" @click="openCreate">新建平台用户</n-button>
    </n-space>
    <n-data-table
      :columns="columns"
      :data="users"
      :loading="loading"
      :row-key="(r: UserProfile) => String(r.id)"
      :pagination="tablePagination"
      remote
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />

    <n-modal v-model:show="showEditor" preset="card" :title="editing ? '编辑用户' : '新建平台用户'" style="width: 480px">
      <n-form label-placement="top">
        <n-form-item label="用户名" required>
          <n-input v-model:value="form.username" :disabled="!!editing" placeholder="全局唯一登录名" />
        </n-form-item>
        <n-form-item label="显示名称" required>
          <n-input v-model:value="form.displayName" placeholder="界面展示名称" />
        </n-form-item>
        <n-form-item :label="editing ? '新密码（留空不改）' : '密码'" :required="!editing">
          <n-input v-model:value="form.password" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="邮箱">
          <n-input v-model:value="form.email" />
        </n-form-item>
        <n-form-item label="手机">
          <n-input v-model:value="form.phone" />
        </n-form-item>
        <n-form-item label="平台角色">
          <n-select
            v-model:value="form.role"
            :options="[
              { label: '普通用户', value: 'user' },
              { label: '平台管理员', value: 'admin' },
            ]"
          />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="form.enabled" :checked-value="1" :unchecked-value="0" />
        </n-form-item>
        <n-alert v-if="!editing" type="info" :bordered="false">
          新建用户为平台级账号，请在「租户管理 → 成员」中将用户加入租户。
        </n-alert>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" @click="saveUser">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-space>
</template>
