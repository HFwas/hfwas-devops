<script setup lang="ts">
import { pmProjectApi } from '@/modules/pm/api'
import type { PmProject } from '@/modules/pm/types'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useDialog, useMessage } from 'naive-ui'

const router = useRouter()
const auth = useAuthStore()
const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(9)
const total = ref(0)
const projects = ref<PmProject[]>([])
const showModal = ref(false)
const form = ref<PmProject>({ code: '', name: '', description: '' })

async function load() {
  loading.value = true
  try {
    const page = await pmProjectApi.page({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value,
    })
    projects.value = page.records
    total.value = Number(page.total) || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNo.value = 1
  void load()
}

function onPageChange(page: number) {
  pageNo.value = page
  void load()
}

function onPageSizeChange(size: number) {
  pageSize.value = size
  pageNo.value = 1
  void load()
}

async function save() {
  await pmProjectApi.save(form.value)
  message.success('项目已保存')
  showModal.value = false
  form.value = { code: '', name: '', description: '' }
  pageNo.value = 1
  await load()
}

function openProject(id: number | string) {
  router.push(`/pm/projects/${id}/items/task`)
}

function confirmDelete(project: PmProject) {
  dialog.warning({
    title: '删除项目',
    content: `确认删除项目「${project.name}」？删除后不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => remove(project.id!),
  })
}

async function remove(id: number | string) {
  await pmProjectApi.delete(id)
  message.success('项目已删除')
  if (projects.value.length <= 1 && pageNo.value > 1) {
    pageNo.value -= 1
  }
  await load()
}

onMounted(load)

watch(
  () => [auth.user?.tenantId, auth.tenantVersion] as const,
  () => {
    pageNo.value = 1
    void load()
  },
)
</script>

<template>
  <n-space vertical size="large">
    <n-space justify="space-between">
      <n-input v-model:value="keyword" placeholder="搜索项目" style="width: 260px" @keyup.enter="onSearch" />
      <n-space>
        <n-button @click="onSearch">查询</n-button>
        <n-button type="primary" @click="showModal = true">新建项目</n-button>
      </n-space>
    </n-space>
    <n-spin :show="loading">
      <n-empty v-if="!loading && projects.length === 0" description="暂无项目" />
      <n-grid v-else :cols="3" :x-gap="16" :y-gap="16">
        <n-gi v-for="p in projects" :key="p.id">
          <n-card hoverable class="project-card">
            <template #header>
              <n-space align="center" justify="space-between" style="width: 100%">
                <n-text strong style="cursor: pointer" @click="openProject(p.id!)">{{ p.name }}</n-text>
                <n-button size="small" quaternary type="error" @click.stop="confirmDelete(p)">删除</n-button>
              </n-space>
            </template>
            <n-space vertical @click="openProject(p.id!)">
              <n-text depth="3">{{ p.code }}</n-text>
              <n-text depth="3">{{ p.description || '暂无描述' }}</n-text>
            </n-space>
          </n-card>
        </n-gi>
      </n-grid>
    </n-spin>
    <n-space v-if="total > 0" justify="end">
      <n-pagination
        :page="pageNo"
        :page-size="pageSize"
        :item-count="total"
        show-size-picker
        :page-sizes="[9, 18, 36]"
        @update:page="onPageChange"
        @update:page-size="onPageSizeChange"
      />
    </n-space>
    <n-modal v-model:show="showModal" preset="card" title="新建项目" style="width: 480px">
      <n-form label-placement="top">
        <n-form-item label="项目编码"><n-input v-model:value="form.code" /></n-form-item>
        <n-form-item label="项目名称"><n-input v-model:value="form.name" /></n-form-item>
        <n-form-item label="描述"><n-input v-model:value="form.description" type="textarea" /></n-form-item>
        <n-button type="primary" @click="save">保存</n-button>
      </n-form>
    </n-modal>
  </n-space>
</template>

<style scoped>
.project-card :deep(.n-card-header) {
  padding-bottom: 8px;
}
.project-card :deep(.n-card__content) {
  cursor: pointer;
}
</style>
