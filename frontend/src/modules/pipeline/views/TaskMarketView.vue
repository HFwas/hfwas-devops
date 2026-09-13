<script setup lang="ts">
import { Edit3, EyeOff, Eye, Package, Save, X } from '@lucide/vue'
import { useMessage } from 'naive-ui'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import { jobKindIcon } from '@/modules/pipeline/graph/jobIcons'
import { jobKindTone } from '@/modules/pipeline/status'
import { useAuthStore } from '@/modules/user/stores/auth'
import type { TaskKindParam, TaskKindVO } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import JobParamEditor from '@/modules/pipeline/components/JobParamEditor.vue'
import ShellEditor from '@/modules/pipeline/components/ShellEditor.vue'
import '@/modules/pipeline/styles/pipeline-theme.css'

const message = useMessage()
const auth = useAuthStore()
const loading = ref(false)
const taskKinds = ref<TaskKindVO[]>([])
const activeGroup = ref<string>('全部')
const editingItem = ref<TaskKindVO | null>(null)
const editDrawerShow = ref(false)
const editForm = ref<Partial<TaskKindVO>>({})
const editParams = computed({
  get(): TaskKindParam[] {
    return editForm.value.params ? editForm.value.params : []
  },
  set(value: TaskKindParam[]) {
    editForm.value.params = value
  },
})

// 从数据动态提取分组，保持原有顺序
const GROUP_ORDER = ['代码', '构建', '质量控制', '制品', '部署', '测试', '命令', '流程']
const groups = computed(() => {
  const set = new Set(taskKinds.value.map((t) => t.taskGroup))
  return ['全部', ...GROUP_ORDER.filter((g) => set.has(g))]
})

// 按当前激活的分组过滤
const filteredByGroup = computed(() => {
  if (activeGroup.value === '全部') return taskKinds.value
  return taskKinds.value.filter((t) => t.taskGroup === activeGroup.value)
})

// 按分组+排序聚合 — 返回数组而非 Map，避免 vue-tsc 对 Map 迭代的类型推断问题
const grouped = computed(() => {
  const map = new Map<string, TaskKindVO[]>()
  for (const t of filteredByGroup.value) {
    const list = map.get(t.taskGroup) ?? []
    list.push(t)
    map.set(t.taskGroup, list)
  }
  return Array.from(map, ([group, items]) => {
    items.sort((a, b) => a.sortOrder - b.sortOrder)
    return { group, items }
  })
})

async function load() {
  loading.value = true
  try {
    taskKinds.value = await pipelineTaskKindApi.list()
  } catch (e: unknown) {
    message.error(isApiError(e) ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openEdit(item: TaskKindVO) {
  editingItem.value = item
  editForm.value = {
    label: item.label,
    taskGroup: item.taskGroup,
    description: item.description,
    hint: item.hint,
    defaultCommand: item.defaultCommand,
    toolImage: item.toolImage,
    commandTemplate: item.commandTemplate,
    sortOrder: item.sortOrder,
    cpuRequest: item.cpuRequest || '',
    cpuLimit: item.cpuLimit || '',
    memoryRequest: item.memoryRequest || '',
    memoryLimit: item.memoryLimit || '',
    params: (item.params || []).map((p) => ({
      ...p,
      options: p.options ? [...p.options] : [],
    })),
  }
  editDrawerShow.value = true
}

async function saveEdit() {
  if (!editingItem.value) return
  try {
    await pipelineTaskKindApi.update(editingItem.value.kindValue, {
      ...editForm.value,
      params: editForm.value.params ? editForm.value.params : [],
    })
    message.success('已保存')
    editDrawerShow.value = false
    editingItem.value = null
    await load()
  } catch (e: unknown) {
    message.error(isApiError(e) ? e.message : '保存失败')
  }
}

function cancelEdit() {
  editDrawerShow.value = false
  editingItem.value = null
}

async function toggle(item: TaskKindVO) {
  try {
    await pipelineTaskKindApi.toggle(item.kindValue)
    message.success(item.enabled ? '已禁用' : '已启用')
    await load()
  } catch (e: unknown) {
    message.error(isApiError(e) ? e.message : '操作失败')
  }
}

function defaultTemplateForKind(item: TaskKindVO): string {
  // 各任务类型的内置缺省模板，仅作为"重置默认"的参考值
  if (item.commandTemplate && item.commandTemplate.length > 0) {
    return item.commandTemplate
  }
  return ''
}

const isAdmin = computed(() => auth.isAdmin)

onMounted(load)
</script>

<template>
  <div class="pl-page">
    <header class="pl-hero">
      <div class="pl-hero-main">
        <h1 class="pl-hero-title">任务市场</h1>
        <p class="pl-hero-desc">
          管理平台支持的 {{ taskKinds.length }} 种任务类型，可编辑元数据、预置环境变量，以及启用或禁用
        </p>
      </div>
    </header>

    <!-- 分组筛选标签 -->
    <div class="pl-toolbar">
      <div class="tm-filter">
        <button
          v-for="g in groups"
          :key="g"
          type="button"
          class="tm-filter-btn"
          :class="{ 'is-active': activeGroup === g }"
          @click="activeGroup = g"
        >
          {{ g }}
        </button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="tm-loading">
      <n-spin size="small" />
      <span style="margin-left: 10px">加载中...</span>
    </div>

    <!-- 任务卡片列表 -->
    <div v-else class="tm-content">
      <!-- 全部：扁平展示，不分分类 -->
      <template v-if="activeGroup === '全部'">
        <div class="tm-grid">
          <div
            v-for="item in filteredByGroup"
            :key="item.kindValue"
            class="tm-card"
            :class="{ 'is-disabled': !item.enabled, 'is-clickable': true }"
            @click="openEdit(item)"
          >
            <div class="tm-card-head">
              <span class="tm-icon" :class="`tone-${jobKindTone(item.kindValue)}`">
                <component :is="jobKindIcon(item.kindValue)" :size="18" />
              </span>
              <span class="tm-label">{{ item.label }}</span>
              <span class="tm-group-tag">{{ item.taskGroup }}</span>
              <n-tag v-if="!item.enabled" size="tiny" type="warning" bordered :round="false" style="margin-left: auto">
                已禁用
              </n-tag>
            </div>
            <p class="tm-desc">{{ item.description }}</p>
            <div class="tm-extra">
              <span class="tm-kind">类型: {{ item.kindValue }}</span>
              <span v-if="item.sortOrder" class="tm-sort">排序: {{ item.sortOrder }}</span>
            </div>
            <div v-if="item.defaultImage" class="tm-tool-image">
              <span class="tm-image-label">镜像:</span>
              <code class="tm-image-value">{{ item.defaultImage }}</code>
            </div>
            <div v-if="item.params && item.params.length" class="tm-extra">
              <span class="tm-kind">预置变量: {{ item.params.map((p) => p.paramKey).join('、') }}</span>
            </div>
            <div v-if="isAdmin" class="tm-actions">
              <n-button size="tiny" quaternary @click.stop="openEdit(item)">
                <template #icon><Edit3 :size="14" /></template>
                编辑
              </n-button>
              <n-button
                size="tiny"
                quaternary
                :type="item.enabled ? 'warning' : 'success'"
                @click.stop="toggle(item)"
              >
                <template #icon>
                  <EyeOff v-if="item.enabled" :size="14" />
                  <Eye v-else :size="14" />
                </template>
                {{ item.enabled ? '禁用' : '启用' }}
              </n-button>
            </div>
          </div>
        </div>
      </template>

      <!-- 分类筛选：按分类分组展示 -->
      <template v-else>
        <div v-for="{ group, items } in grouped" :key="group" class="tm-section">
          <h3 class="tm-section-title">{{ group }}</h3>
          <div class="tm-grid">
            <div
              v-for="item in items"
              :key="item.kindValue"
              class="tm-card"
              :class="{ 'is-disabled': !item.enabled, 'is-clickable': true }"
              @click="openEdit(item)"
            >
              <div class="tm-card-head">
                <span class="tm-icon" :class="`tone-${jobKindTone(item.kindValue)}`">
                  <component :is="jobKindIcon(item.kindValue)" :size="18" />
                </span>
                <span class="tm-label">{{ item.label }}</span>
                <n-tag v-if="!item.enabled" size="tiny" type="warning" bordered :round="false" style="margin-left: auto">
                  已禁用
                </n-tag>
              </div>
              <p class="tm-desc">{{ item.description }}</p>
              <div class="tm-extra">
                <span class="tm-kind">类型: {{ item.kindValue }}</span>
                <span v-if="item.sortOrder" class="tm-sort">排序: {{ item.sortOrder }}</span>
              </div>
              <div v-if="item.defaultImage" class="tm-tool-image">
                <span class="tm-image-label">镜像:</span>
                <code class="tm-image-value">{{ item.defaultImage }}</code>
              </div>
              <div v-if="item.params && item.params.length" class="tm-extra">
                <span class="tm-kind">预置变量: {{ item.params.map((p) => p.paramKey).join('、') }}</span>
              </div>
              <div v-if="isAdmin" class="tm-actions">
                <n-button size="tiny" quaternary @click.stop="openEdit(item)">
                  <template #icon><Edit3 :size="14" /></template>
                  编辑
                </n-button>
                <n-button
                  size="tiny"
                  quaternary
                  :type="item.enabled ? 'warning' : 'success'"
                  @click.stop="toggle(item)"
                >
                  <template #icon>
                    <EyeOff v-if="item.enabled" :size="14" />
                    <Eye v-else :size="14" />
                  </template>
                  {{ item.enabled ? '禁用' : '启用' }}
                </n-button>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 空态 -->
      <div v-if="taskKinds.length === 0 && !loading" class="tm-empty">
        <p>暂无可用的任务类型</p>
      </div>
    </div>

    <!-- 编辑抽屉 -->
    <n-drawer v-model:show="editDrawerShow" :width="640" placement="right">
      <n-drawer-content title="编辑任务" closable @close="cancelEdit">
        <template v-if="editingItem">
          <n-form label-placement="top">
            <n-form-item label="类型标识">
              <n-input :value="editingItem.kindValue" disabled />
            </n-form-item>
            <n-form-item label="名称">
              <n-input v-model:value="editForm.label" />
            </n-form-item>
            <n-form-item label="分组">
              <n-select
                v-model:value="editForm.taskGroup"
                :options="[
                  { label: '代码', value: '代码' },
                  { label: '构建', value: '构建' },
                  { label: '质量控制', value: '质量控制' },
                  { label: '制品', value: '制品' },
                  { label: '部署', value: '部署' },
                  { label: '测试', value: '测试' },
                  { label: '命令', value: '命令' },
                  { label: '流程', value: '流程' },
                ]"
              />
            </n-form-item>
            <n-form-item label="描述">
              <n-input v-model:value="editForm.description" type="textarea" :rows="2" />
            </n-form-item>
            <n-form-item label="默认镜像">
              <n-input :value="editingItem.defaultImage" disabled placeholder="系统默认镜像地址，不可修改" />
            </n-form-item>
            <n-form-item label="使用提示">
              <n-input v-model:value="editForm.hint" type="textarea" :rows="2" />
            </n-form-item>
            <n-divider />
            <JobParamEditor v-model:params="editParams" />
            <n-divider />
            <n-form-item label="自定义镜像">
              <n-input v-model:value="editForm.toolImage" placeholder="留空则使用默认镜像" />
              <div style="font-size:12px;color:var(--wb-muted,#888);margin-top:4px">设置后将覆盖默认镜像，Pod 优先使用此镜像地址</div>
            </n-form-item>
            <n-form-item label="默认命令">
              <n-input v-model:value="editForm.defaultCommand" type="textarea" :rows="4" :autosize="{ minRows: 4 }" />
            </n-form-item>
            <n-form-item label="脚本模板">
              <div class="tm-template-editor">
                <ShellEditor
                  v-model="editForm.commandTemplate"
                  :kind-value="editingItem.kindValue"
                  :default-template="defaultTemplateForKind(editingItem)"
                  height="280px"
                />
                <div style="font-size:12px;color:var(--wb-muted,#888);margin-top:6px">
                  使用 <code>${COMMAND}</code> 引用用户填写的默认命令。
                  留空则使用系统缺省模板（仅 <code>cd workspace && ${COMMAND}</code>）。
                </div>
              </div>
            </n-form-item>
            <n-form-item label="排序">
              <n-input-number v-model:value="editForm.sortOrder" :min="0" style="width: 120px" />
            </n-form-item>
            <n-divider />
            <template v-if="isAdmin">
              <n-form-item label="CPU 请求">
                <n-input v-model:value="editForm.cpuRequest" placeholder="例如: 500m, 1, 2000m" clearable />
                <div style="font-size:12px;color:var(--wb-muted,#888);margin-top:4px">
                  留空表示不限制（使用集群默认值）。格式参考 Kubernetes 资源量。
                </div>
              </n-form-item>
              <n-form-item label="CPU 限制">
                <n-input v-model:value="editForm.cpuLimit" placeholder="例如: 1, 2000m, 4" clearable />
                <div style="font-size:12px;color:var(--wb-muted,#888);margin-top:4px">
                  留空表示不限制。
                </div>
              </n-form-item>
              <n-form-item label="内存请求">
                <n-input v-model:value="editForm.memoryRequest" placeholder="例如: 256Mi, 512Mi, 1Gi" clearable />
                <div style="font-size:12px;color:var(--wb-muted,#888);margin-top:4px">
                  留空表示不限制。
                </div>
              </n-form-item>
              <n-form-item label="内存限制">
                <n-input v-model:value="editForm.memoryLimit" placeholder="例如: 512Mi, 1Gi, 2Gi" clearable />
                <div style="font-size:12px;color:var(--wb-muted,#888);margin-top:4px">
                  留空表示不限制。
                </div>
              </n-form-item>
            </template>
          </n-form>
          <div class="tm-edit-footer">
            <n-button @click="cancelEdit">
              <template #icon><X :size="14" /></template>
              取消
            </n-button>
            <n-button type="primary" @click="saveEdit">
              <template #icon><Save :size="14" /></template>
              保存
            </n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.tm-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tm-filter-btn {
  padding: 5px 14px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 16px;
  background: var(--wb-card-bg, #fff);
  font: inherit;
  font-size: 13px;
  color: var(--wb-muted, #646a73);
  cursor: pointer;
  transition: all 0.15s;
}

.tm-filter-btn:hover {
  border-color: var(--wb-primary, #3370ff);
  color: var(--wb-primary, #3370ff);
}

.tm-filter-btn.is-active {
  background: var(--wb-primary, #3370ff);
  border-color: var(--wb-primary, #3370ff);
  color: #fff;
}

.tm-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: var(--wb-muted, #646a73);
  font-size: 14px;
}

.tm-content {
  padding: 0 24px 24px;
}

.tm-section + .tm-section {
  margin-top: 24px;
}

.tm-section-title {
  margin: 0 0 12px;
  padding: 0 4px 8px;
  border-bottom: 1px solid var(--wb-border-soft, #eef0f3);
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
}

.tm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.tm-card {
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  padding: 16px;
  background: var(--wb-card-bg, #fff);
  transition: box-shadow 0.15s;
}

.tm-card:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
}

.tm-card.is-disabled {
  opacity: 0.55;
  background: var(--wb-th, #fafbfc);
}

.tm-card.is-clickable {
  cursor: pointer;
}

.tm-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tm-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tone-blue {
  background: #eff6ff;
  color: #2563eb;
}
.tone-violet {
  background: #f5f3ff;
  color: #7c3aed;
}
.tone-green {
  background: #ecfdf5;
  color: #059669;
}
.tone-amber {
  background: #fffbeb;
  color: #d97706;
}
.tone-cyan {
  background: #ecfeff;
  color: #0891b2;
}
.tone-rose {
  background: #fff1f2;
  color: #e11d48;
}

.tm-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
}

.tm-group-tag {
  font-size: 11px;
  color: var(--wb-muted, #646a73);
  background: var(--wb-th, #f5f7fb);
  padding: 1px 8px;
  border-radius: 4px;
  white-space: nowrap;
}

.tm-desc {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--wb-muted, #646a73);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.tm-extra {
  margin-top: 10px;
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--wb-muted, #646a73);
}

.tm-tool-image {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.tm-image-label {
  color: var(--wb-muted, #646a73);
  flex-shrink: 0;
}

.tm-image-value {
  font-size: 11px;
  color: var(--wb-primary, #3370ff);
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}

.tm-actions {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--wb-border-soft, #eef0f3);
  display: flex;
  gap: 6px;
}

.tm-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--wb-muted, #646a73);
}

.tm-edit-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
</style>