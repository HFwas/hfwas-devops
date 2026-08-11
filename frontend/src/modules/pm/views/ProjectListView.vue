<script setup lang="ts">
import { FolderKanban } from '@lucide/vue'
import { pmProjectApi } from '@/modules/pm/api'
import type { PmProject } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'
import { useAuthStore } from '@/modules/user/stores/auth'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'
import { useDialog, useMessage } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
// 顶栏全局搜索通过 ?keyword= 带入
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const pagination = usePagination({ pageSize: 9, pageSizes: [9, 18, 36] })
const projects = ref<PmProject[]>([])
const showModal = ref(false)
const form = ref<PmProject>({ code: '', name: '', description: '' })

async function load() {
  loading.value = true
  try {
    const page = await pmProjectApi.page({
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
    })
    projects.value = page.records
    pagination.setTotal(page.total)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pagination.resetPage()
  void load()
}

async function save() {
  await pmProjectApi.save(form.value)
  message.success('项目已保存')
  showModal.value = false
  form.value = { code: '', name: '', description: '' }
  pagination.resetPage()
  await load()
}

function openProject(id: number | string) {
  router.push(`/pm/projects/${asId(id)}/items/task`)
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
  pagination.afterDelete(projects.value.length)
  await load()
}

onMounted(load)

watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
    pagination.resetPage()
    void load()
  },
)

watch(
  () => [auth.activeTenantId, auth.tenantVersion] as const,
  () => {
    pagination.resetPage()
    void load()
  },
)
</script>

<template>
  <n-space vertical size="large" style="padding: 20px 24px 28px">
    <n-page-header title="项目" subtitle="选择或创建项目管理事项与配置">
      <template #extra>
        <n-button type="primary" @click="showModal = true">新建项目</n-button>
      </template>
    </n-page-header>
    <n-space>
      <n-input
        v-model:value="keyword"
        placeholder="搜索项目名称或编码"
        clearable
        style="width: 280px"
        @keyup.enter="onSearch"
      />
      <n-button @click="onSearch">查询</n-button>
    </n-space>
    <n-spin :show="loading">
      <n-empty v-if="!loading && projects.length === 0" description="暂无项目" />
      <n-grid v-else :cols="3" :x-gap="16" :y-gap="16">
        <n-gi v-for="p in projects" :key="p.id">
          <div class="project-tile" @click="openProject(p.id!)">
            <div class="project-tile-top">
              <span class="project-tile-icon">
                <FolderKanban :size="18" />
              </span>
              <n-button
                size="small"
                quaternary
                type="error"
                class="project-tile-delete"
                @click.stop="confirmDelete(p)"
              >删除</n-button>
            </div>
            <div class="project-tile-body">
              <div class="project-tile-name">{{ p.name }}</div>
              <div class="project-tile-code">{{ p.code }}</div>
              <div class="project-tile-desc">{{ p.description || '暂无描述' }}</div>
            </div>
          </div>
        </n-gi>
      </n-grid>
    </n-spin>
    <AppPagination :pagination="pagination" :on-change="load" />
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
.project-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 16px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 10px;
  background: var(--wb-card-bg, #fff);
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.project-tile:hover {
  transform: translateY(-2px);
  border-color: #4098fc;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
}

.project-tile-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.project-tile-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  flex-shrink: 0;
}

.project-tile-delete {
  opacity: 0;
  transition: opacity 0.2s;
}

.project-tile:hover .project-tile-delete {
  opacity: 1;
}

.project-tile-body {
  margin-top: 12px;
  min-width: 0;
}

.project-tile-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--pm-text, #1f2329);
}

.project-tile-code {
  margin-top: 4px;
  font-size: 13px;
  color: var(--wb-muted, #6b7280);
  font-variant-numeric: tabular-nums;
}

.project-tile-desc {
  margin-top: 8px;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .project-tile {
    padding: 14px;
  }
}
</style>
