<script setup lang="ts">
import { h } from 'vue'
import { NButton, NPopconfirm, useMessage, type SelectOption, type TreeOption } from 'naive-ui'
import { pmModuleApi } from '@/modules/pm/api'
import { invalidateProjectModules } from '@/modules/pm/composables/useProjectModules'
import type { PmProjectModule } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'

const route = useRoute()
const message = useMessage()

const projectId = computed(() => routeId(route.params.projectId))
const keyword = ref('')
const treeData = ref<PmProjectModule[]>([])
const loading = ref(false)
const expandedKeys = ref<Array<string | number>>(['all'])
const selectedKey = ref<string | number>('all')

const showEditor = ref(false)
const editing = ref<PmProjectModule | null>(null)
const parentForCreate = ref<number | null>(null)

const form = ref({
  name: '',
  description: '',
  parentId: null as number | null,
})

async function loadTree() {
  if (!projectId.value) return
  loading.value = true
  try {
    treeData.value = await pmModuleApi.tree(projectId.value)
    invalidateProjectModules(projectId.value)
  } finally {
    loading.value = false
  }
}

function moduleMatches(node: PmProjectModule, kw: string): boolean {
  if (node.name.toLowerCase().includes(kw)) return true
  return (node.children ?? []).some((child) => moduleMatches(child, kw))
}

function filterTree(nodes: PmProjectModule[], kw: string): PmProjectModule[] {
  if (!kw) return nodes
  return nodes
    .filter((node) => moduleMatches(node, kw))
    .map((node) => ({
      ...node,
      children: filterTree(node.children ?? [], kw),
    }))
}

const filteredTree = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return filterTree(treeData.value, kw)
})

function toTreeOptions(nodes: PmProjectModule[]): TreeOption[] {
  return nodes.map((node) => ({
    key: node.id as number,
    label: node.name,
    raw: node,
    children: node.children?.length ? toTreeOptions(node.children) : undefined,
  }))
}

const treeOptions = computed<TreeOption[]>(() => [
  { key: 'all', label: '全部' },
  ...toTreeOptions(filteredTree.value),
])

const parentOptions = computed<SelectOption[]>(() => {
  const options: SelectOption[] = [{ label: '无（顶级模块）', value: null as unknown as number }]
  const walk = (nodes: PmProjectModule[], prefix = '') => {
    for (const node of nodes) {
      if (node.id == null) continue
      if (editing.value?.id === node.id) continue
      options.push({ label: prefix + node.name, value: node.id })
      if (node.children?.length) walk(node.children, `${prefix}${node.name} / `)
    }
  }
  walk(treeData.value)
  return options
})

function openCreate(parentId: number | null = null) {
  editing.value = null
  parentForCreate.value = parentId
  form.value = { name: '', description: '', parentId }
  showEditor.value = true
}

function openEdit(node: PmProjectModule) {
  editing.value = node
  parentForCreate.value = node.parentId ?? null
  form.value = {
    name: node.name,
    description: node.description ?? '',
    parentId: node.parentId ?? null,
  }
  showEditor.value = true
}

async function saveModule() {
  if (!form.value.name.trim()) {
    message.warning('请输入模块名称')
    return
  }
  try {
    await pmModuleApi.save({
      id: editing.value?.id,
      projectId: projectId.value,
      parentId: form.value.parentId,
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
    })
    message.success(editing.value ? '已更新' : '已创建')
    showEditor.value = false
    await loadTree()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function removeModule(id: number) {
  try {
    await pmModuleApi.delete(id)
    message.success('已删除')
    await loadTree()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

function renderSuffix({ option }: { option: TreeOption }) {
  if (option.key === 'all') return null
  const node = option.raw as PmProjectModule
  return h('div', { style: 'display:flex;gap:4px;margin-left:8px' }, [
    h(NButton, { text: true, size: 'tiny', onClick: (e: Event) => { e.stopPropagation(); openCreate(node.id ?? null) } }, () => '子模块'),
    h(NButton, { text: true, size: 'tiny', type: 'primary', onClick: (e: Event) => { e.stopPropagation(); openEdit(node) } }, () => '编辑'),
    h(
      NPopconfirm,
      { onPositiveClick: () => node.id != null && removeModule(node.id) },
      {
        trigger: () =>
          h(NButton, { text: true, size: 'tiny', type: 'error', onClick: (e: Event) => e.stopPropagation() }, () => '删除'),
        default: () => '确定删除该模块吗？',
      },
    ),
  ])
}

onMounted(loadTree)
</script>

<template>
  <n-space vertical size="large" style="padding: 20px 24px 28px">
    <n-page-header
      title="功能模块"
      subtitle="按业务域划分项目工作，事项可归属到单一模块以便筛选与统计"
    >
      <template #extra>
        <n-button type="primary" @click="openCreate(null)">新建模块</n-button>
      </template>
    </n-page-header>

    <n-alert type="info" :bordered="false">
      功能模块用于按业务域划分工作。事项可归属到单一模块；建议按产品域划分，层级 2～3 层为宜。
    </n-alert>

    <div class="module-layout">
      <n-card size="small" class="module-tree-card">
        <template #header>
          <n-space align="center" justify="space-between" style="width: 100%">
            <span>模块树</span>
            <n-button size="tiny" quaternary type="primary" @click="openCreate(null)">添加</n-button>
          </n-space>
        </template>
        <n-input v-model:value="keyword" placeholder="搜索模块…" clearable style="margin-bottom: 12px" />
        <n-spin :show="loading">
          <n-tree
            v-model:expanded-keys="expandedKeys"
            :selected-keys="[selectedKey]"
            block-line
            :data="treeOptions"
            :render-suffix="renderSuffix"
            @update:selected-keys="(keys) => { if (keys[0] != null) selectedKey = keys[0] }"
          />
          <n-empty v-if="!loading && !filteredTree.length" description="暂无模块，点击「新建模块」创建" size="small" style="margin-top: 24px" />
        </n-spin>
      </n-card>

      <n-card size="small" title="说明" style="flex: 1">
        <n-ul>
          <n-li>每个项目独立维护模块树，名称在同级下唯一。</n-li>
          <n-li>事项仅归属一个模块；删除模块前需先调整或清空关联事项。</n-li>
          <n-li>可在「事项配置」中将「功能模块」加入列表、筛选或创建表单。</n-li>
          <n-li>建议按产品域或子系统划分，层级不宜过深（2～3 层为宜）。</n-li>
        </n-ul>
      </n-card>
    </div>

    <n-modal v-model:show="showEditor" preset="card" :title="editing ? '编辑模块' : '新建模块'" style="width: 480px">
      <n-form label-placement="top">
        <n-form-item label="模块名称" required>
          <n-input v-model:value="form.name" placeholder="如：用户中心、订单服务" maxlength="64" show-count />
        </n-form-item>
        <n-form-item label="上级模块">
          <n-select v-model:value="form.parentId" :options="parentOptions" clearable />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" placeholder="可选，说明模块职责范围" :autosize="{ minRows: 3 }" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" @click="saveModule">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </n-space>
</template>

<style scoped>
.module-layout {
  display: flex;
  gap: 16px;
  min-height: 480px;
}

.module-tree-card {
  width: 320px;
  flex-shrink: 0;
}
</style>
