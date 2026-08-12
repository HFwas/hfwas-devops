<script setup lang="ts">
import { useMessage } from 'naive-ui'
import PmConditionGroup from '@/modules/pm/components/PmConditionGroup/index.vue'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import { useProjectModules } from '@/modules/pm/composables/useProjectModules'
import { useTransitionPostFunctionMeta } from '@/modules/pm/composables/useTransitionPostFunctionMeta'
import { useUserOptions } from '@/modules/pm/composables/useUserOptions'
import type {
  QueryConditionGroup,
  TransitionConditionSpec,
  TransitionPostFunction,
  TransitionPostFunctionType,
  TransitionValidator,
} from '@/modules/pm/types'
import {
  CURRENT_USER_TOKEN,
  emptyTransitionConditions,
  isTransitionConditionsEmpty,
} from '@/modules/pm/types'
import {
  actionTypeLabel,
  blankAction,
  summarizeTransitionAction,
} from '@/modules/pm/utils/transitionActionSummary'

const props = defineProps<{
  show: boolean
  projectId: string | number
  typeCode: string
  fromStatusCode: string
  fromStatusName: string
  toStatusCode: string
  toStatusName: string
  transitionName: string
  conditions?: TransitionConditionSpec
  validators: TransitionValidator[]
  postFunctions: TransitionPostFunction[]
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  save: [payload: {
    name: string
    conditions: TransitionConditionSpec
    validators: TransitionValidator[]
    postFunctions: TransitionPostFunction[]
  }]
}>()

const message = useMessage()
const fieldStore = useFieldSchemaStore()
const projectIdRef = computed(() => props.projectId)
const typeCodeRef = computed(() => props.typeCode)
const { loading: metaLoading, meta, fieldMetaMap, fieldLabelMap, load: reloadMeta } =
  useTransitionPostFunctionMeta(projectIdRef, typeCodeRef)
const { selectOptions: userOptions, labelMap: userLabelMap, load: loadUsers } = useUserOptions()
const { labelMap: moduleLabelMap, selectOptions: moduleOptions, load: loadModules } =
  useProjectModules(projectIdRef)

const localActions = ref<TransitionPostFunction[]>([])
const localName = ref('')
const localConditions = ref<TransitionConditionSpec>(emptyTransitionConditions())
const requiredFieldKeys = ref<string[]>([])
const expandedIndex = ref<number | null>(null)

const fieldDefs = computed(() => fieldStore.getSchema(props.projectId, props.typeCode))
const searchableFields = computed(() => fieldDefs.value.filter((f) => f.searchable))
const conditionCount = computed(
  () => (localConditions.value.conditions?.length ?? 0) + (localConditions.value.groups?.length ?? 0),
)

const conditionGroup = computed({
  get: (): QueryConditionGroup => ({
    logic: localConditions.value.logic || 'AND',
    conditions: localConditions.value.conditions || [],
    groups: localConditions.value.groups || [],
  }),
  set: (group: QueryConditionGroup) => {
    localConditions.value = {
      logic: group.logic || 'AND',
      conditions: group.conditions || [],
      groups: group.groups || [],
    }
  },
})

const fieldSelectOptions = computed(() =>
  (meta.value?.fields ?? []).map((f) => ({ label: f.fieldName, value: f.fieldKey })),
)

const addTypeOptions: Array<{ label: string; value: TransitionPostFunctionType }> = [
  { label: '修改字段', value: 'SET_FIELD' },
  { label: '通知负责人', value: 'NOTIFY_ASSIGNEE' },
  { label: '通知指定成员', value: 'NOTIFY_USER' },
  { label: '群通知（钉钉/飞书）', value: 'WEBHOOK' },
]

const summaryCtx = computed(() => ({
  fieldLabelMap: fieldLabelMap.value,
  fieldMetaMap: fieldMetaMap.value,
  userLabelMap: userLabelMap.value,
  moduleLabelMap: moduleLabelMap.value,
}))

watch(
  () => props.show,
  async (visible) => {
    if (!visible) return
    localName.value = props.transitionName ?? ''
    localActions.value = (props.postFunctions ?? []).map((item) => ({ ...item }))
    localConditions.value = {
      logic: props.conditions?.logic || 'AND',
      conditions: (props.conditions?.conditions ?? []).map((c) => ({ ...c })),
      groups: (props.conditions?.groups ?? []).map((g) => ({
        ...g,
        conditions: [...(g.conditions ?? [])],
        groups: [...(g.groups ?? [])],
      })),
    }
    const required = (props.validators ?? []).find((v) => v.type === 'REQUIRED_FIELDS')
    requiredFieldKeys.value = [...(required?.fieldKeys ?? [])]
    expandedIndex.value = null
    await Promise.all([
      reloadMeta(true),
      loadUsers(),
      loadModules(),
      fieldStore.loadSchema(props.projectId, props.typeCode),
    ])
  },
)

function onConditionGroupUpdate(g: QueryConditionGroup) {
  localConditions.value = {
    logic: g.logic || 'AND',
    conditions: g.conditions || [],
    groups: g.groups || [],
  }
}

function applyAssigneeOnly() {
  localConditions.value = {
    logic: 'AND',
    conditions: [{ field: 'assignee_id', operator: 'EQ', value: CURRENT_USER_TOKEN }],
    groups: [],
  }
  if (!localName.value.trim() || localName.value.startsWith('→')) {
    localName.value = `仅负责人 · → ${props.toStatusName}`
  }
}

function clearConditions() {
  localConditions.value = emptyTransitionConditions()
}

function close() {
  emit('update:show', false)
}

function addBlankAction(type: TransitionPostFunctionType) {
  localActions.value.push(blankAction(type))
  expandedIndex.value = localActions.value.length - 1
}

function removeAction(index: number) {
  localActions.value.splice(index, 1)
  if (expandedIndex.value === index) expandedIndex.value = null
}

function moveAction(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= localActions.value.length) return
  const list = [...localActions.value]
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
  localActions.value = list
  expandedIndex.value = target
}

function fieldOptions(fieldKey?: string) {
  if (!fieldKey) return []
  return fieldMetaMap.value[fieldKey]?.options ?? []
}

function fieldType(fieldKey?: string) {
  if (!fieldKey) return 'TEXT'
  return fieldMetaMap.value[fieldKey]?.fieldType ?? 'TEXT'
}

function validateAction(action: TransitionPostFunction) {
  if (action.type === 'SET_FIELD') {
    if (!action.fieldKey) return '请选择要修改的字段'
    if (fieldType(action.fieldKey) === 'USER' && !action.value) return '请选择用户'
    if (fieldKeyNeedsValue(action.fieldKey) && (action.value == null || action.value === '')) {
      return '请填写字段值'
    }
  }
  if (action.type === 'NOTIFY_USER' && !action.userId) return '请选择通知成员'
  return null
}

function fieldKeyNeedsValue(fieldKey: string) {
  const type = fieldType(fieldKey)
  return !['USER', 'MODULE'].includes(type) || fieldKey === 'assignee_id' || fieldKey === 'reporter_id' || fieldKey === 'module_id'
}

function buildValidators(): TransitionValidator[] {
  if (!requiredFieldKeys.value.length) return []
  return [{ type: 'REQUIRED_FIELDS', fieldKeys: [...requiredFieldKeys.value] }]
}

function save() {
  for (const action of localActions.value) {
    const err = validateAction(action)
    if (err) {
      message.warning(err)
      return
    }
  }
  const name = localName.value.trim() || `→ ${props.toStatusName}`
  const conditions = isTransitionConditionsEmpty(localConditions.value)
    ? emptyTransitionConditions()
    : {
        logic: localConditions.value.logic || 'AND',
        conditions: [...(localConditions.value.conditions ?? [])],
        groups: [...(localConditions.value.groups ?? [])],
      }
  emit('save', {
    name,
    conditions,
    validators: buildValidators(),
    postFunctions: localActions.value.map((item) => ({ ...item })),
  })
  emit('update:show', false)
}

function insertPlaceholder(action: TransitionPostFunction, token: string) {
  action.content = `${action.content ?? ''}${token}`
}
</script>

<template>
  <n-drawer :show="show" :width="520" placement="right" @update:show="emit('update:show', $event)">
    <n-drawer-content closable @close="close">
      <template #header>
        <n-space vertical :size="4">
          <n-text strong>配置流转规则</n-text>
          <n-space align="center" :size="8">
            <n-tag size="small" :bordered="false">{{ fromStatusName }}</n-tag>
            <n-text depth="3">→</n-text>
            <n-tag size="small" type="success" :bordered="false">{{ toStatusName }}</n-tag>
          </n-space>
        </n-space>
      </template>

      <n-spin :show="metaLoading">
        <n-space vertical size="large">
          <n-alert type="info" :bordered="false">
            可配置可见条件、流转前必填字段与后置动作。修改后需在工作流页点击「保存配置」才会持久化。
          </n-alert>

          <section>
            <n-form-item label="流转名称" label-placement="top">
              <n-input v-model:value="localName" placeholder="如：开始处理" />
            </n-form-item>
          </section>

          <section>
            <n-space align="center" justify="space-between" style="margin-bottom: 10px">
              <n-text depth="3">可见条件{{ conditionCount ? ` · ${conditionCount} 条` : '' }}</n-text>
              <n-space :size="6">
                <n-button size="tiny" quaternary @click="applyAssigneeOnly">仅负责人</n-button>
                <n-button size="tiny" quaternary :disabled="!conditionCount" @click="clearConditions">清空</n-button>
              </n-space>
            </n-space>
            <n-text depth="3" style="display: block; font-size: 12px; margin-bottom: 10px">
              不满足条件时，该流转不会出现在事项的可选列表中。空条件表示始终可见。
            </n-text>
            <n-empty
              v-if="!searchableFields.length"
              description="暂无可用搜索字段，请在事项配置中开启字段「搜索」"
              size="small"
            />
            <PmConditionGroup
              v-else
              :field-defs="searchableFields"
              :group="conditionGroup"
              :project-id="projectId"
              @update:group="onConditionGroupUpdate"
            />
          </section>

          <section>
            <n-text depth="3" style="display: block; margin-bottom: 10px">流转前校验</n-text>
            <n-form-item label="必填字段" label-placement="top">
              <n-select
                v-model:value="requiredFieldKeys"
                multiple
                filterable
                clearable
                placeholder="选择流转到目标状态前必须填写的字段"
                :options="fieldSelectOptions"
              />
            </n-form-item>
            <n-text depth="3" style="font-size: 12px">
              例如关单前要求填写优先级或自定义「解决结果」字段。
            </n-text>
          </section>

          <section>
            <n-space align="center" justify="space-between" style="margin-bottom: 10px">
              <n-text depth="3">流转后动作</n-text>
              <n-dropdown
                trigger="click"
                :options="addTypeOptions.map((o) => ({ label: o.label, key: o.value }))"
                @select="(key) => addBlankAction(key as TransitionPostFunctionType)"
              >
                <n-button size="small" quaternary type="primary">添加动作</n-button>
              </n-dropdown>
            </n-space>
          </section>

          <section>
            <n-text depth="3" style="display: block; margin-bottom: 10px">
              执行顺序（{{ localActions.length }}）
            </n-text>

            <n-empty v-if="!localActions.length" description="暂未添加动作，可通过「添加动作」添加" />

            <n-timeline v-else size="large">
              <n-timeline-item v-for="(action, index) in localActions" :key="index" type="info">
                <div class="action-card" :class="{ expanded: expandedIndex === index }">
                  <div class="action-head" @click="expandedIndex = expandedIndex === index ? null : index">
                    <n-space align="center" :size="8" style="flex: 1; min-width: 0">
                      <n-tag size="tiny" :bordered="false" type="primary">{{ actionTypeLabel(action.type) }}</n-tag>
                      <n-text style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                        {{ summarizeTransitionAction(action, summaryCtx) }}
                      </n-text>
                    </n-space>
                    <n-space :size="4" @click.stop>
                      <n-button size="tiny" quaternary :disabled="index === 0" @click="moveAction(index, -1)">上移</n-button>
                      <n-button
                        size="tiny"
                        quaternary
                        :disabled="index === localActions.length - 1"
                        @click="moveAction(index, 1)"
                      >
                        下移
                      </n-button>
                      <n-button size="tiny" quaternary type="error" @click="removeAction(index)">删除</n-button>
                    </n-space>
                  </div>

                  <div v-if="expandedIndex === index" class="action-body">
                    <n-form-item label="动作类型" label-placement="top">
                      <n-select
                        v-model:value="action.type"
                        :options="addTypeOptions"
                        @update:value="() => { action.fieldKey = undefined; action.value = undefined; action.userId = undefined }"
                      />
                    </n-form-item>

                    <template v-if="action.type === 'SET_FIELD'">
                      <n-form-item label="字段" label-placement="top">
                        <n-select
                          v-model:value="action.fieldKey"
                          :options="(meta?.fields ?? []).map((f) => ({ label: f.fieldName, value: f.fieldKey }))"
                          filterable
                        />
                      </n-form-item>
                      <n-form-item v-if="fieldOptions(action.fieldKey).length" label="值" label-placement="top">
                        <n-select
                          v-model:value="action.value as string"
                          :options="fieldOptions(action.fieldKey).map((o) => ({ label: o.label, value: o.value }))"
                        />
                      </n-form-item>
                      <n-form-item v-else-if="fieldType(action.fieldKey) === 'USER' || action.fieldKey === 'assignee_id' || action.fieldKey === 'reporter_id'" label="用户" label-placement="top">
                        <n-select v-model:value="action.value as string | number | null" :options="userOptions" filterable />
                      </n-form-item>
                      <n-form-item v-else-if="fieldType(action.fieldKey) === 'MODULE' || action.fieldKey === 'module_id'" label="模块" label-placement="top">
                        <n-select v-model:value="action.value as string | number | null" :options="moduleOptions" filterable />
                      </n-form-item>
                      <n-form-item v-else label="值" label-placement="top">
                        <n-input v-model:value="action.value as string" placeholder="输入目标值" />
                      </n-form-item>
                    </template>

                    <template v-else-if="action.type === 'NOTIFY_USER'">
                      <n-form-item label="通知成员" label-placement="top">
                        <n-select v-model:value="action.userId" :options="userOptions" filterable />
                      </n-form-item>
                    </template>

                    <n-alert
                      v-if="action.type === 'WEBHOOK'"
                      type="warning"
                      :bordered="false"
                      style="margin-bottom: 12px"
                    >
                      群通知将推送到当前租户已启用的钉钉 / 飞书渠道，此处不配置独立 Webhook URL。
                    </n-alert>

                    <n-collapse v-if="['NOTIFY_ASSIGNEE', 'NOTIFY_USER', 'WEBHOOK'].includes(action.type)">
                      <n-collapse-item title="自定义通知内容（可选）" name="advanced">
                        <n-form-item label="标题" label-placement="top">
                          <n-input v-model:value="action.title" placeholder="默认：工作项状态已更新" />
                        </n-form-item>
                        <n-form-item label="内容" label-placement="top">
                          <n-input
                            v-model:value="action.content"
                            type="textarea"
                            :autosize="{ minRows: 2, maxRows: 4 }"
                            placeholder="默认包含事项标题与状态变更说明"
                          />
                        </n-form-item>
                        <n-space :size="6">
                          <n-tag
                            v-for="token in meta?.placeholders ?? []"
                            :key="token"
                            size="small"
                            style="cursor: pointer"
                            @click="insertPlaceholder(action, token)"
                          >
                            {{ token }}
                          </n-tag>
                        </n-space>
                      </n-collapse-item>
                    </n-collapse>
                  </div>
                </div>
              </n-timeline-item>
            </n-timeline>
          </section>
        </n-space>
      </n-spin>

      <template #footer>
        <n-space justify="end" style="width: 100%">
          <n-button @click="close">取消</n-button>
          <n-button type="primary" @click="save">保存规则</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.action-card {
  border: 1px solid var(--n-border-color);
  border-radius: var(--n-border-radius);
  overflow: hidden;
}

.action-card.expanded {
  border-color: var(--n-primary-color);
}

.action-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  background: var(--n-color-embedded, var(--n-action-color));
}

.action-body {
  padding: 12px;
  border-top: 1px solid var(--n-border-color);
}
</style>
