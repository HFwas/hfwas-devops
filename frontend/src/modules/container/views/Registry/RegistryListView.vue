<template>
  <div class="registry-list-page">
    <n-page-header>
      <template #title>镜像仓库</template>
      <template #extra>
        <n-button type="primary" @click="showCreateDialog = true">
          <template #icon><n-icon><Plus /></n-icon></template>
          注册仓库
        </n-button>
      </template>
    </n-page-header>

    <n-data-table
      :columns="columns"
      :data="registries"
      :loading="loading"
      :bordered="false"
      :single-line="false"
      size="small"
      style="margin-top: 16px"
    />

    <n-pagination
      v-if="total > pageSize"
      :page="pageNo"
      :page-size="pageSize"
      :item-count="total"
      :on-update:page="(p: number) => { pageNo = p; loadData() }"
      style="margin-top: 16px; justify-content: flex-end"
    />

    <RegistryFormDialog
      v-model:show="showCreateDialog"
      @created="loadData"
      @updated="loadData"
    />
    <RegistryFormDialog
      v-model:show="showEditDialog"
      :registry="editingRegistry"
      @updated="loadData"
      @update:show="showEditDialog = false"
    />
  </div>
</template>

<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@lucide/vue'
import { NButton, NIcon, NTag, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import type { RegistryVO } from '../../types/registry'
import { registryApi } from '../../api/registry'
import RegistryStatusBadge from '../../components/Registry/RegistryStatusBadge.vue'
import RegistryFormDialog from '../../components/Registry/RegistryFormDialog.vue'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const registries = ref<RegistryVO[]>([])
const loading = ref(false)
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const editingRegistry = ref<RegistryVO | null>(null)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const columns: DataTableColumn[] = [
  {
    title: '名称',
    key: 'name',
    width: 180,
    render: (row) => (row as unknown as RegistryVO).alias || (row as unknown as RegistryVO).name,
  },
  {
    title: '地址',
    key: 'url',
    width: 260,
    ellipsis: { tooltip: true },
  },
  {
    title: '类型',
    key: 'type',
    width: 100,
    render: (row) => (row as unknown as RegistryVO).type === 'harbor' ? 'Harbor' : 'Registry V2',
  },
  {
    title: '来源',
    key: 'source',
    width: 80,
    render: (row) =>
      (row as unknown as RegistryVO).source === 'builtin'
        ? h(NTag, { size: 'small', type: 'info', bordered: false }, () => '内置')
        : h(NTag, { size: 'small', type: 'default', bordered: false }, () => '手动'),
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => h(RegistryStatusBadge, { status: (row as unknown as RegistryVO).status }),
  },
  {
    title: '创建时间',
    key: 'createdAt',
    width: 180,
  },
  {
    title: '操作',
    key: 'actions',
    width: 280,
    render: (row) => {
      const r = row as unknown as RegistryVO
      return h('div', { style: 'display: flex; gap: 8px' }, [
        h(NButton, {
          size: 'small',
          onClick: () => router.push(`/container/registries/${r.id}`),
        }, () => '进入'),
        h(NButton, {
          size: 'small',
          onClick: () => {
            editingRegistry.value = r
            showEditDialog.value = true
          },
        }, () => '编辑'),
        h(NButton, {
          size: 'small',
          onClick: () => testConnection(r.id),
        }, () => '测试'),
        h(NButton, {
          size: 'small',
          disabled: r.source === 'builtin',
          onClick: () => confirmDelete(r),
        }, () => '删除'),
      ])
    },
  },
]

async function loadData() {
  loading.value = true
  try {
    const result = await registryApi.page({ pageNo: pageNo.value, pageSize: pageSize.value })
    registries.value = result.records ?? []
    total.value = Number(result.total)
  } catch (e: any) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function testConnection(id: string) {
  try {
    const ok = await registryApi.test(id)
    message.success(ok ? '连接成功' : '连接失败')
    await loadData()
  } catch (e: any) {
    message.error(e.message || '测试失败')
  }
}

function confirmDelete(row: RegistryVO) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除镜像仓库「${row.alias || row.name}」？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await registryApi.delete(row.id)
        message.success('删除成功')
        await loadData()
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

onMounted(loadData)
</script>