<script setup lang="ts">
import { NButton, NPopconfirm, NTag, useMessage } from 'naive-ui'
import PmFieldEditorDrawer from '@/modules/pm/components/PmFieldEditorDrawer/index.vue'
import StatusWorkflowView from '@/modules/pm/views/settings/StatusWorkflowView.vue'
import { pmFieldApi, pmFieldLayoutApi, pmMetaApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type {
  DetailTabDefinition,
  FeatureDefinition,
  FieldDefinition,
  TypeFieldLayoutConfig,
  TypeFeaturesConfig,
  WorkItemIoFeatureConfig,
} from '@/modules/pm/types'
import { FIELD_TYPE_LABELS, typeLabel as resolveTypeLabel } from '@/modules/pm/types'
import { useProjectIssueTypes } from '@/modules/pm/composables/useIssueTypes'
import { routeId } from '@/modules/pm/utils/id'

type DetailTabKey = 'fields' | 'detailTabs' | 'features' | 'status' | 'workflow'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => routeId(route.params.projectId))
const typeCode = computed(() => String(route.params.typeCode))
const { types: projectTypes } = useProjectIssueTypes(projectId)
const typeLabel = computed(() => resolveTypeLabel(typeCode.value, projectTypes.value))

const activeTab = ref<DetailTabKey>('fields')

const fields = ref<FieldDefinition[]>([])
const layout = ref<TypeFieldLayoutConfig>({
  listFields: [],
  searchFields: [],
  createFields: [],
  detailTabs: [],
  features: { work_item_io: { enabled: true, exportFieldKeys: [], importFieldKeys: [] } },
})
const tabCatalog = ref<DetailTabDefinition[]>([])
const featureCatalog = ref<FeatureDefinition[]>([])
const loading = ref(false)
const saving = ref(false)
const ready = ref(false)

const showDrawer = ref(false)
const editingId = ref<string | null>(null)
const showPickModal = ref(false)
const availableFields = ref<FieldDefinition[]>([])
const pickingFieldId = ref<string | null>(null)
const loadingAvailable = ref(false)

const configurableFields = computed(() => fields.value.filter((f) => f.fieldKey !== 'type_code'))

const featureFieldOptions = computed(() =>
  configurableFields.value.map((f) => ({ label: `${f.fieldName}（${f.fieldKey}）`, value: f.fieldKey })),
)

const workItemIo = computed(() => layout.value.features?.work_item_io)

const enabledTabs = computed(() => {
  const ids = layout.value.detailTabs ?? []
  const byId = new Map(tabCatalog.value.map((t) => [t.id, t]))
  return ids.map((id) => byId.get(id)).filter(Boolean) as DetailTabDefinition[]
})

const availableTabsToAdd = computed(() => {
  const enabled = new Set(layout.value.detailTabs ?? [])
  return tabCatalog.value.filter((t) => !enabled.has(t.id))
})

function defaultFeatures(): TypeFeaturesConfig {
  return { work_item_io: { enabled: true, exportFieldKeys: [], importFieldKeys: [] } }
}

function normalizeFeatures(raw?: TypeFeaturesConfig | null): TypeFeaturesConfig {
  const base = defaultFeatures()
  const io = raw?.work_item_io
  if (!io) return base
  return {
    work_item_io: {
      enabled: io.enabled !== false,
      exportFieldKeys: [...(io.exportFieldKeys ?? [])],
      importFieldKeys: [...(io.importFieldKeys ?? [])],
    },
  }
}

function parseTab(raw: unknown): DetailTabKey {
  if (
    raw === 'detailTabs' ||
    raw === 'features' ||
    raw === 'status' ||
    raw === 'workflow' ||
    raw === 'fields'
  ) {
    return raw
  }
  return 'fields'
}

function syncTabFromRoute() {
  activeTab.value = parseTab(route.query.tab)
}

function onTabChange(name: string) {
  const tab = parseTab(name)
  activeTab.value = tab
  router.replace({
    path: route.path,
    query: tab === 'fields' ? {} : { tab },
  })
}

function fieldId(row: FieldDefinition) {
  return row.id == null ? '' : String(row.id)
}

function isCustomField(field: FieldDefinition) {
  return field.systemFlag !== 1 && !!field.id
}

function isChecked(list: string[], key: string) {
  return list.includes(key)
}

type FieldLayoutListKey = 'listFields' | 'searchFields' | 'createFields'

async function toggle(listKey: FieldLayoutListKey, fieldKey: string, checked: boolean) {
  const list = [...layout.value[listKey]]
  const idx = list.indexOf(fieldKey)
  if (checked && idx < 0) list.push(fieldKey)
  if (!checked && idx >= 0) list.splice(idx, 1)
  layout.value = { ...layout.value, [listKey]: list }
  await persistLayout()
}

async function addTab(tabId: string) {
  const tabs = [...(layout.value.detailTabs ?? [])]
  if (tabs.includes(tabId)) return
  tabs.push(tabId)
  layout.value = { ...layout.value, detailTabs: tabs }
  await persistLayout()
}

async function removeTab(tabId: string) {
  const tabs = (layout.value.detailTabs ?? []).filter((id) => id !== tabId)
  if (!tabs.length) {
    message.warning('至少保留一个详情 Tab')
    return
  }
  layout.value = { ...layout.value, detailTabs: tabs }
  await persistLayout()
}

async function moveTab(tabId: string, delta: -1 | 1) {
  const tabs = [...(layout.value.detailTabs ?? [])]
  const idx = tabs.indexOf(tabId)
  if (idx < 0) return
  const next = idx + delta
  if (next < 0 || next >= tabs.length) return
  ;[tabs[idx], tabs[next]] = [tabs[next], tabs[idx]]
  layout.value = { ...layout.value, detailTabs: tabs }
  await persistLayout()
}

function patchWorkItemIo(patch: Partial<WorkItemIoFeatureConfig>) {
  const current = normalizeFeatures(layout.value.features)
  const io = { ...current.work_item_io!, ...patch }
  layout.value = { ...layout.value, features: { work_item_io: io } }
}

async function setWorkItemIoEnabled(enabled: boolean) {
  patchWorkItemIo({ enabled })
  await persistLayout()
}

async function setWorkItemIoExportKeys(keys: string[]) {
  patchWorkItemIo({ exportFieldKeys: keys })
  await persistLayout()
}

async function setWorkItemIoImportKeys(keys: string[]) {
  patchWorkItemIo({ importFieldKeys: keys })
  await persistLayout()
}

async function load() {
  loading.value = true
  ready.value = false
  try {
    const [fieldList, layoutCfg, tabs, features] = await Promise.all([
      pmFieldApi.list(projectId.value, typeCode.value),
      pmFieldLayoutApi.get(projectId.value, typeCode.value),
      pmMetaApi.detailTabs(),
      pmMetaApi.features(),
    ])
    fields.value = fieldList
    tabCatalog.value = tabs ?? []
    featureCatalog.value = features ?? []
    layout.value = {
      listFields: layoutCfg.listFields ?? [],
      searchFields: layoutCfg.searchFields ?? [],
      createFields: layoutCfg.createFields ?? [],
      detailTabs: layoutCfg.detailTabs?.length
        ? layoutCfg.detailTabs
        : (tabs ?? []).filter((t) => t.defaultEnabled).map((t) => t.id),
      features: normalizeFeatures(layoutCfg.features),
    }
  } finally {
    loading.value = false
    ready.value = true
  }
}

async function persistLayout() {
  if (!ready.value) return
  saving.value = true
  try {
    await pmFieldLayoutApi.save(projectId.value, typeCode.value, layout.value)
    await fieldStore.loadSchema(projectId.value, typeCode.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
    await load()
  } finally {
    saving.value = false
  }
}

function openCreateField() {
  editingId.value = null
  showDrawer.value = true
}

function openEditField(field: FieldDefinition) {
  const id = fieldId(field)
  if (!id) return
  editingId.value = id
  showDrawer.value = true
}

async function openPickExisting() {
  loadingAvailable.value = true
  showPickModal.value = true
  pickingFieldId.value = null
  try {
    availableFields.value = await pmFieldApi.listAvailable(projectId.value, typeCode.value)
  } finally {
    loadingAvailable.value = false
  }
}

async function confirmPickExisting() {
  if (!pickingFieldId.value) {
    message.warning('请选择要添加的字段')
    return
  }
  saving.value = true
  try {
    await pmFieldApi.addToType(projectId.value, pickingFieldId.value, typeCode.value)
    message.success('字段已添加到该事项类型')
    showPickModal.value = false
    await load()
    await fieldStore.loadSchema(projectId.value, typeCode.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    saving.value = false
  }
}

async function removeField(field: FieldDefinition) {
  const id = fieldId(field)
  if (!id) return
  saving.value = true
  try {
    await pmFieldApi.removeFromType(projectId.value, id, typeCode.value)
    message.success('字段已移除')
    await load()
    await fieldStore.loadSchema(projectId.value, typeCode.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '移除失败')
  } finally {
    saving.value = false
  }
}

async function onFieldSaved(savedId?: string) {
  showDrawer.value = false
  await load()
  if (savedId) {
    const field = fields.value.find((f) => fieldId(f) === savedId)
    if (field) {
      const key = field.fieldKey
      const next = { ...layout.value }
      let changed = false
      if (!next.createFields.includes(key)) {
        next.createFields = [...next.createFields, key]
        changed = true
      }
      if (!next.listFields.includes(key)) {
        next.listFields = [...next.listFields, key]
        changed = true
      }
      if (changed) {
        layout.value = next
        await persistLayout()
      }
    }
  }
  await fieldStore.loadSchema(projectId.value, typeCode.value)
}

function goBack() {
  router.push(`/pm/projects/${projectId.value}/settings/types`)
}

const availableOptions = computed(() =>
  availableFields.value.map((f) => ({
    label: `${f.fieldName} (${f.fieldKey})`,
    value: fieldId(f),
  })),
)

watch(typeCode, load, { immediate: true })
watch(() => route.query.tab, syncTabFromRoute, { immediate: true })
</script>

<template>
  <n-space vertical size="large">
    <n-page-header :title="typeLabel" @back="goBack">
      <template #subtitle>
        配置该事项类型的属性、详情面板、功能、状态与流转
      </template>
      <template v-if="activeTab === 'fields'" #extra>
        <n-space>
          <n-button @click="openPickExisting">从字段库添加</n-button>
          <n-button type="primary" @click="openCreateField">添加属性</n-button>
        </n-space>
      </template>
    </n-page-header>

    <n-tabs :value="activeTab" type="line" @update:value="onTabChange">
      <n-tab-pane name="fields" tab="属性" display-directive="show">
        <n-spin :show="loading || saving">
          <n-card size="small" title="字段展示">
            <n-text depth="3" style="display: block; margin-bottom: 12px; font-size: 12px">
              管理本类型可用属性，并控制在列表、搜索、新建中的展示
            </n-text>
            <n-table :bordered="false" size="small" :single-line="false">
              <thead>
                <tr>
                  <th>字段名称</th>
                  <th>字段编码</th>
                  <th>类型</th>
                  <th style="width: 90px">列表</th>
                  <th style="width: 90px">搜索</th>
                  <th style="width: 90px">新建</th>
                  <th style="width: 120px">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="field in configurableFields" :key="field.fieldKey">
                  <td>
                    <n-space align="center" :size="8">
                      {{ field.fieldName }}
                      <n-tag v-if="field.systemFlag === 1" size="small" :bordered="false">系统</n-tag>
                    </n-space>
                  </td>
                  <td><n-text depth="3">{{ field.fieldKey }}</n-text></td>
                  <td>{{ FIELD_TYPE_LABELS[field.fieldType] ?? field.fieldType }}</td>
                  <td>
                    <n-switch
                      size="small"
                      :disabled="saving"
                      :value="isChecked(layout.listFields, field.fieldKey)"
                      @update:value="(v) => toggle('listFields', field.fieldKey, v)"
                    />
                  </td>
                  <td>
                    <n-switch
                      size="small"
                      :disabled="saving"
                      :value="isChecked(layout.searchFields, field.fieldKey)"
                      @update:value="(v) => toggle('searchFields', field.fieldKey, v)"
                    />
                  </td>
                  <td>
                    <n-switch
                      size="small"
                      :disabled="saving"
                      :value="isChecked(layout.createFields, field.fieldKey)"
                      @update:value="(v) => toggle('createFields', field.fieldKey, v)"
                    />
                  </td>
                  <td>
                    <n-space v-if="isCustomField(field)" :size="4">
                      <n-button text type="primary" size="small" @click="openEditField(field)">编辑</n-button>
                      <n-popconfirm @positive-click="removeField(field)">
                        <template #trigger>
                          <n-button text type="error" size="small">移除</n-button>
                        </template>
                        确定从「{{ typeLabel }}」移除此字段吗？若仅适用本类型将一并删除字段定义。
                      </n-popconfirm>
                    </n-space>
                    <n-text v-else depth="3">—</n-text>
                  </td>
                </tr>
              </tbody>
            </n-table>
            <n-empty
              v-if="!loading && !configurableFields.length"
              description="暂无属性，请点击「添加属性」或「从字段库添加」"
              style="padding: 24px 0"
            />
          </n-card>
        </n-spin>
      </n-tab-pane>

      <n-tab-pane name="detailTabs" tab="详情面板" display-directive="show">
        <n-spin :show="loading || saving">
          <n-card size="small" title="详情 Tab">
            <n-text depth="3" style="display: block; margin-bottom: 12px; font-size: 12px">
              控制事项详情页展示哪些面板及顺序（至少保留一个）
            </n-text>
            <n-space vertical :size="8">
              <div v-for="(tab, idx) in enabledTabs" :key="tab.id" class="tab-row">
                <n-text>{{ tab.name }}</n-text>
                <n-text depth="3" style="font-size: 12px">{{ tab.id }}</n-text>
                <n-space :size="4">
                  <n-button size="tiny" quaternary :disabled="idx === 0" @click="moveTab(tab.id, -1)">上移</n-button>
                  <n-button
                    size="tiny"
                    quaternary
                    :disabled="idx === enabledTabs.length - 1"
                    @click="moveTab(tab.id, 1)"
                  >
                    下移
                  </n-button>
                  <n-button size="tiny" quaternary type="error" @click="removeTab(tab.id)">移除</n-button>
                </n-space>
              </div>
              <n-empty v-if="!enabledTabs.length" description="暂无启用 Tab" size="small" />
            </n-space>
            <n-space v-if="availableTabsToAdd.length" style="margin-top: 12px" :size="8">
              <n-button
                v-for="tab in availableTabsToAdd"
                :key="tab.id"
                size="small"
                dashed
                @click="addTab(tab.id)"
              >
                + {{ tab.name }}
              </n-button>
            </n-space>
          </n-card>
        </n-spin>
      </n-tab-pane>

      <n-tab-pane name="features" tab="功能" display-directive="show">
        <n-spin :show="loading || saving">
          <n-card size="small" title="事项类型功能">
            <n-text depth="3" style="display: block; margin-bottom: 16px; font-size: 12px">
              按类型开关列表能力；与侧栏「功能模块」（Components）无关。空字段配置时导入导出回退列表展示字段。
            </n-text>
            <n-space vertical :size="16">
              <div
                v-for="feat in featureCatalog"
                :key="feat.id"
                class="feature-card"
              >
                <div class="feature-card__header">
                  <div>
                    <n-text strong>{{ feat.name }}</n-text>
                    <n-text depth="3" style="display: block; font-size: 12px; margin-top: 2px">
                      {{ feat.id }}
                      <template v-if="feat.surfaces?.length"> · {{ feat.surfaces.join(' / ') }}</template>
                    </n-text>
                  </div>
                  <n-switch
                    v-if="feat.id === 'work_item_io'"
                    :disabled="saving"
                    :value="workItemIo?.enabled !== false"
                    @update:value="setWorkItemIoEnabled"
                  />
                </div>
                <template v-if="feat.id === 'work_item_io' && workItemIo?.enabled !== false">
                  <n-form-item label="默认导出字段" label-placement="top" style="margin-bottom: 12px">
                    <n-select
                      multiple
                      filterable
                      clearable
                      :disabled="saving"
                      :options="featureFieldOptions"
                      :value="workItemIo?.exportFieldKeys ?? []"
                      placeholder="空则按列表展示字段"
                      @update:value="setWorkItemIoExportKeys"
                    />
                  </n-form-item>
                  <n-form-item label="默认导入字段" label-placement="top" :show-feedback="false">
                    <n-select
                      multiple
                      filterable
                      clearable
                      :disabled="saving"
                      :options="featureFieldOptions"
                      :value="workItemIo?.importFieldKeys ?? []"
                      placeholder="空则按现有导入默认"
                      @update:value="setWorkItemIoImportKeys"
                    />
                  </n-form-item>
                </template>
              </div>
              <n-empty v-if="!featureCatalog.length" description="暂无可用功能" size="small" />
            </n-space>
          </n-card>
        </n-spin>
      </n-tab-pane>

      <n-tab-pane name="status" tab="状态配置" display-directive="if">
        <StatusWorkflowView embedded section="status" :fixed-type-code="typeCode" />
      </n-tab-pane>

      <n-tab-pane name="workflow" tab="流转配置" display-directive="if">
        <StatusWorkflowView embedded section="transitions" :fixed-type-code="typeCode" />
      </n-tab-pane>
    </n-tabs>

    <PmFieldEditorDrawer
      :key="`${showDrawer}-${editingId ?? 'new'}`"
      v-model:show="showDrawer"
      :project-id="projectId"
      :field-id="editingId"
      :default-type-code="typeCode"
      lock-type-code
      @saved="onFieldSaved"
    />

    <n-modal v-model:show="showPickModal" preset="card" title="从字段库添加" style="width: 480px">
      <n-spin :show="loadingAvailable">
        <n-empty v-if="!availableFields.length && !loadingAvailable" description="暂无可用字段，请先在「字段」页新建" />
        <n-select
          v-else
          v-model:value="pickingFieldId"
          :options="availableOptions"
          placeholder="选择要添加的自定义字段"
          filterable
        />
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPickModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" :disabled="!pickingFieldId" @click="confirmPickExisting">
            添加
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </n-space>
</template>

<style scoped>
.tab-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid var(--n-border-color);
  border-radius: var(--n-border-radius);
}

.tab-row > :first-child {
  font-weight: 500;
  min-width: 72px;
}

.tab-row > :nth-child(2) {
  flex: 1;
}

.feature-card {
  padding: 12px 14px;
  border: 1px solid var(--n-border-color);
  border-radius: var(--n-border-radius);
}

.feature-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
}
</style>
