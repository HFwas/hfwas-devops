<script setup lang="ts">
import { h } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import { tenantManageApi, tenantMemberApi } from '@/modules/user/api'
import type { PlatformUserOption, Tenant, TenantMember } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'
import { invalidateUserOptionsCache } from '@/modules/pm/composables/useUserOptions'

const message = useMessage()

const keyword = ref('')
const status = ref<'all' | '1' | '0'>('all')
const loading = ref(false)
const tenants = ref<Tenant[]>([])
const showEditor = ref(false)
const editing = ref<Tenant | null>(null)

const showMembers = ref(false)
const memberTenant = ref<Tenant | null>(null)
const memberLoading = ref(false)
const members = ref<TenantMember[]>([])
const memberKeyword = ref('')
const showAddMember = ref(false)
const availableUsers = ref<PlatformUserOption[]>([])
const selectedUserIds = ref<(number | string)[]>([])
const addRole = ref('member')

const form = ref({
  code: '',
  name: '',
  contactName: '',
  contactPhone: '',
  status: 1,
  remark: '',
})

async function load() {
  loading.value = true
  try {
    const page = await tenantManageApi.page({
      pageNo: 1,
      pageSize: 100,
      keyword: keyword.value.trim() || undefined,
      status: status.value,
    })
    tenants.value = page.records
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { code: '', name: '', contactName: '', contactPhone: '', status: 1, remark: '' }
  showEditor.value = true
}

function openEdit(row: Tenant) {
  editing.value = row
  form.value = {
    code: row.code,
    name: row.name,
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    status: row.status ?? 1,
    remark: row.remark ?? '',
  }
  showEditor.value = true
}

async function saveTenant() {
  try {
    await tenantManageApi.save({
      id: editing.value?.id,
      code: form.value.code.trim(),
      name: form.value.name.trim(),
      contactName: form.value.contactName.trim() || undefined,
      contactPhone: form.value.contactPhone.trim() || undefined,
      status: form.value.status,
      remark: form.value.remark.trim() || undefined,
    })
    message.success(editing.value ? '已更新' : '已创建')
    showEditor.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function removeTenant(row: Tenant) {
  if (row.id == null) return
  try {
    await tenantManageApi.delete(row.id)
    message.success('已删除')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function openMembers(row: Tenant) {
  memberTenant.value = row
  memberKeyword.value = ''
  showMembers.value = true
  await loadMembers()
}

async function loadMembers() {
  if (!memberTenant.value?.id) return
  memberLoading.value = true
  try {
    const page = await tenantMemberApi.page(memberTenant.value.id, {
      pageNo: 1,
      pageSize: 200,
      keyword: memberKeyword.value.trim() || undefined,
    })
    members.value = page.records
  } finally {
    memberLoading.value = false
  }
}

async function openAddMember() {
  if (!memberTenant.value?.id) return
  selectedUserIds.value = []
  addRole.value = 'member'
  availableUsers.value = await tenantMemberApi.available(memberTenant.value.id)
  showAddMember.value = true
}

async function confirmAddMembers() {
  if (!memberTenant.value?.id || selectedUserIds.value.length === 0) {
    message.warning('请选择要加入的用户')
    return
  }
  try {
    await tenantMemberApi.add(memberTenant.value.id, {
      userIds: selectedUserIds.value,
      tenantRole: addRole.value,
    })
    message.success('已加入租户')
    showAddMember.value = false
    invalidateUserOptionsCache()
    await loadMembers()
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '添加失败')
  }
}

async function updateMemberRole(row: TenantMember, tenantRole: string) {
  if (!memberTenant.value?.id || row.userId == null) return
  try {
    await tenantMemberApi.save(memberTenant.value.id, { userId: row.userId, tenantRole })
    message.success('已更新')
    await loadMembers()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新失败')
  }
}

async function toggleMemberStatus(row: TenantMember) {
  if (!memberTenant.value?.id || row.userId == null) return
  const next = row.status === 1 ? 0 : 1
  try {
    await tenantMemberApi.save(memberTenant.value.id, { userId: row.userId, status: next })
    message.success(next === 1 ? '已启用' : '已停用')
    invalidateUserOptionsCache()
    await loadMembers()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新失败')
  }
}

async function removeMember(row: TenantMember) {
  if (!memberTenant.value?.id || row.userId == null) return
  try {
    await tenantMemberApi.remove(memberTenant.value.id, row.userId)
    message.success('已移除')
    invalidateUserOptionsCache()
    await loadMembers()
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '移除失败')
  }
}

const columns = [
  { title: '编码', key: 'code', width: 120 },
  { title: '名称', key: 'name' },
  { title: '联系人', key: 'contactName', render: (row: Tenant) => row.contactName ?? '-' },
  { title: '联系电话', key: 'contactPhone', render: (row: Tenant) => row.contactPhone ?? '-' },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row: Tenant) =>
      h(NTag, { size: 'small', type: row.status === 1 ? 'success' : 'default' }, () =>
        row.status === 1 ? '启用' : '停用',
      ),
  },
  { title: '成员数', key: 'userCount', width: 80 },
  { title: '项目数', key: 'projectCount', width: 80 },
  {
    title: '创建时间',
    key: 'createTime',
    width: 170,
    render: (row: Tenant) => (row.createTime ? formatDateTime(row.createTime) : '-'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: (row: Tenant) =>
      h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' }, [
        h(NButton, { text: true, type: 'primary', onClick: () => openMembers(row) }, () => '成员'),
        h(NButton, { text: true, onClick: () => openEdit(row) }, () => '编辑'),
        row.code !== 'default'
          ? h(
              NPopconfirm,
              { onPositiveClick: () => removeTenant(row) },
              {
                trigger: () => h(NButton, { text: true, type: 'error' }, () => '删除'),
                default: () => '确定删除该租户吗？',
              },
            )
          : null,
      ]),
  },
]

const memberColumns = [
  { title: '用户名', key: 'username' },
  { title: '显示名称', key: 'displayName' },
  {
    title: '租户角色',
    key: 'tenantRole',
    render: (row: TenantMember) =>
      h(
        NTag,
        { size: 'small', type: row.tenantRole === 'tenant_admin' ? 'warning' : 'default' },
        () => (row.tenantRole === 'tenant_admin' ? '租户管理员' : '成员'),
      ),
  },
  {
    title: '状态',
    key: 'status',
    render: (row: TenantMember) => (row.status === 1 ? '启用' : '停用'),
  },
  {
    title: '加入时间',
    key: 'joinTime',
    render: (row: TenantMember) => (row.joinTime ? formatDateTime(row.joinTime) : '-'),
  },
  {
    title: '操作',
    key: 'actions',
    render: (row: TenantMember) =>
      h('div', { style: 'display:flex;gap:8px' }, [
        row.tenantRole === 'member'
          ? h(NButton, { text: true, onClick: () => updateMemberRole(row, 'tenant_admin') }, () => '设为管理员')
          : h(NButton, { text: true, onClick: () => updateMemberRole(row, 'member') }, () => '设为成员'),
        h(
          NButton,
          { text: true, onClick: () => toggleMemberStatus(row) },
          () => (row.status === 1 ? '停用' : '启用'),
        ),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeMember(row) },
          {
            trigger: () => h(NButton, { text: true, type: 'error' }, () => '移除'),
            default: () => '确定将该用户移出租户吗？',
          },
        ),
      ]),
  },
]

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="租户管理"
      subtitle="管理租户并将平台用户拉入租户；仅租户成员可在该租户项目中分配"
    />
    <n-space>
      <n-input v-model:value="keyword" placeholder="搜索编码/名称/联系人" clearable style="width: 260px" />
      <n-select
        v-model:value="status"
        :options="[
          { label: '全部状态', value: 'all' },
          { label: '启用', value: '1' },
          { label: '停用', value: '0' },
        ]"
        style="width: 120px"
      />
      <n-button @click="load">查询</n-button>
      <n-button type="primary" @click="openCreate">新建租户</n-button>
    </n-space>
    <n-data-table :columns="columns" :data="tenants" :loading="loading" :row-key="(r: Tenant) => String(r.id)" />

    <n-modal v-model:show="showEditor" preset="card" :title="editing ? '编辑租户' : '新建租户'" style="width: 520px">
      <n-form label-placement="top">
        <n-form-item label="租户编码" required>
          <n-input v-model:value="form.code" :disabled="!!editing" placeholder="小写字母开头，如 acme" />
        </n-form-item>
        <n-form-item label="租户名称" required>
          <n-input v-model:value="form.name" placeholder="企业或组织名称" />
        </n-form-item>
        <n-form-item label="联系人">
          <n-input v-model:value="form.contactName" />
        </n-form-item>
        <n-form-item label="联系电话">
          <n-input v-model:value="form.contactPhone" />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch
            v-model:value="form.status"
            :checked-value="1"
            :unchecked-value="0"
            :disabled="editing?.code === 'default'"
          />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.remark" type="textarea" :rows="2" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" @click="saveTenant">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-drawer v-model:show="showMembers" :width="720" placement="right">
      <n-drawer-content :title="`租户成员 · ${memberTenant?.name ?? ''}`" closable>
        <n-space vertical size="large" style="width: 100%">
          <n-alert type="info" :bordered="false">
            从平台用户池拉入成员；只有已加入且启用的成员，才会出现在该租户项目的用户选择器中。
          </n-alert>
          <n-space>
            <n-input
              v-model:value="memberKeyword"
              placeholder="搜索成员"
              clearable
              style="width: 220px"
              @keyup.enter="loadMembers"
            />
            <n-button @click="loadMembers">查询</n-button>
            <n-button type="primary" @click="openAddMember">拉入用户</n-button>
          </n-space>
          <n-data-table
            :columns="memberColumns"
            :data="members"
            :loading="memberLoading"
            :row-key="(r: TenantMember) => String(r.userId)"
          />
        </n-space>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="showAddMember" preset="card" title="拉入平台用户" style="width: 520px">
      <n-form label-placement="top">
        <n-form-item label="选择用户" required>
          <n-select
            v-model:value="selectedUserIds"
            multiple
            filterable
            placeholder="选择尚未加入该租户的平台用户"
            :options="
              availableUsers.map((u) => ({
                label: `${u.displayName} (${u.username})`,
                value: u.id,
              }))
            "
          />
        </n-form-item>
        <n-form-item label="租户角色">
          <n-select
            v-model:value="addRole"
            :options="[
              { label: '成员', value: 'member' },
              { label: '租户管理员', value: 'tenant_admin' },
            ]"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddMember = false">取消</n-button>
          <n-button type="primary" @click="confirmAddMembers">确认加入</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-space>
</template>
