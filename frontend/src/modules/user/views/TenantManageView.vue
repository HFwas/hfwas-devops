<script setup lang="ts">
import { h } from 'vue'
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import { tenantManageApi } from '@/modules/user/api'
import type { Tenant } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'

const message = useMessage()

const keyword = ref('')
const status = ref<'all' | '1' | '0'>('all')
const loading = ref(false)
const tenants = ref<Tenant[]>([])
const showEditor = ref(false)
const editing = ref<Tenant | null>(null)

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
  { title: '用户数', key: 'userCount', width: 80 },
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
    width: 140,
    render: (row: Tenant) =>
      h('div', { style: 'display:flex;gap:8px' }, [
        h(NButton, { text: true, type: 'primary', onClick: () => openEdit(row) }, () => '编辑'),
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

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header title="租户管理" subtitle="管理平台租户、隔离用户与项目数据" />
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
          <n-input
            v-model:value="form.code"
            :disabled="!!editing"
            placeholder="小写字母开头，如 acme"
          />
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
          <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="0" :disabled="editing?.code === 'default'" />
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
  </n-space>
</template>
