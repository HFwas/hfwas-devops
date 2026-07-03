<script setup lang="ts">
import { h } from 'vue'
import { NButton, NPopconfirm, useMessage, type TreeOption } from 'naive-ui'
import { pmModuleApi } from '@/modules/pm/api'
import { invalidateProjectModules } from '@/modules/pm/composables/useProjectModules'
import type { PmProjectModule } from '@/modules/pm/types'

const route = useRoute()
const message = useMessage()

const projectId = computed(() => Number(route.params.projectId))
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

const parentOptions = computed(() => {
  const options: Array<{ label: string; value: number | null }> = [{ label: '无（顶级模块）', value: null }]
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
  <n-card title="功能模块" :bordered="false">
    <template #header-extra>
      <n-button type="primary" @click="openCreate(null)">新建模块</n-button>
    </template>

    <n-alert type="info" :bordered="false" style="margin-bottom: 16px">
      功能模块用于按业务域划分项目工作（类似 Jira Component）。需求、缺陷、任务可归属到单一模块，便于筛选与统计。
    </n-alert>

    <div style="display: flex; gap: 16px; min-height: 480px">
      <n-card size="small" style="width: 320px; flex-shrink: 0" :bordered="true">
        <template #header>
          <div style="display: flex; align-items: center; justify-content: space-between">
            <span>模块</span>
            <n-button quaternary circle size="small" @click="openCreate(null)">
              <template #icon>
                <span style="font-size: 18px; line-height: 1">+</span>
              </template>
            </n-button>
          </div>
        </template>
        <n-input v-model:value="keyword" placeholder="请输入..." clearable style="margin-bottom: 12px">
          <template #prefix>
            <span style="opacity: 0.45">⌕</span>
          </template>
        </n-input>
        <n-spin :show="loading">
          <n-tree
            v-model:expanded-keys="expandedKeys"
            :selected-keys="[selectedKey]"
            block-line
            :data="treeOptions"
            :render-suffix="renderSuffix"
            @update:selected-keys="(keys) => { if (keys[0] != null) selectedKey = keys[0] }"
          />
          <n-empty v-if="!loading && !filteredTree.length" description="暂无模块，点击 + 创建" size="small" style="margin-top: 24px" />
        </n-spin>
      </n-card>

      <n-card size="small" style="flex: 1" :bordered="true" title="说明">
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
  </n-card>
</template>
