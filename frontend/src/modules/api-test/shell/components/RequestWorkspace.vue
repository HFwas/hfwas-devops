<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useMessage, type TreeSelectOption } from 'naive-ui'
import { useAuthStore } from '@/modules/user/stores/auth'
import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import type { HttpMethod, ApiDefinitionParamDTO } from '@/modules/api-test/define/types/definition'
import type { ApiGroupVO } from '@/modules/api-test/define/types/group'
import { useApiGroupStore } from '@/modules/api-test/define/stores/group'
import ApiWorkspaceResponse from '@/modules/api-test/define/components/ApiWorkspaceResponse.vue'
import KeyValueEditor from '@/modules/api-test/shared/components/KeyValueEditor.vue'
import ScriptEditor from '@/modules/api-test/debug/components/ScriptEditor.vue'
import AssertionEditor from '@/modules/api-test/debug/components/AssertionEditor.vue'
import ExtractEditor from '@/modules/api-test/debug/components/ExtractEditor.vue'
import ComingSoonPane from '@/modules/api-test/shell/components/ComingSoonPane.vue'
import { useDebugStore } from '@/modules/api-test/debug/stores/debug'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { RequestDraft } from '@/modules/api-test/shell/types/workspace'
import { clampResponseHeight } from '@/modules/api-test/shell/utils/layoutPersist'

const PROJECT_ID = 1

const message = useMessage()
const authStore = useAuthStore()
const workspace = useWorkspaceStore()
const debugStore = useDebugStore()
const envStore = useEnvironmentStore()
const groupStore = useApiGroupStore()

const { activeTab, responseHeight } = storeToRefs(workspace)
const userId = computed(() => Number(authStore.user?.id) || 0)
const executing = computed(() => debugStore.executing)

const METHOD_OPTIONS = HTTP_METHOD_OPTIONS.map((o) => ({ label: o.label, value: o.value }))
const CONTENT_TYPE_OPTIONS = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/xml', value: 'application/xml' },
  { label: 'application/x-www-form-urlencoded', value: 'application/x-www-form-urlencoded' },
  { label: 'multipart/form-data', value: 'multipart/form-data' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/html', value: 'text/html' },
]

const requestTab = ref('params')
const responseTab = ref('response')
const showScratchDialog = ref(false)
const scratchSaving = ref(false)
const scratchName = ref('')
const scratchGroupId = ref<number | null>(null)

const groupOptions = computed(() => buildGroupTreeOptions(groupStore.groupTree))

function buildGroupTreeOptions(groups: ApiGroupVO[]): TreeSelectOption[] {
  return groups.map((g) => ({
    key: g.id,
    label: g.name,
    children: g.children?.length ? buildGroupTreeOptions(g.children) : undefined,
  }))
}

function patch(partial: Partial<RequestDraft>) {
  const tab = activeTab.value
  if (!tab) return
  workspace.patchDraft(tab.id, partial)
  if (partial.method) {
    tab.method = partial.method
  }
}

async function handleSend() {
  const tab = workspace.activeTab
  if (!tab?.draft.url) { message.warning('请输入请求 URL'); return }
  try {
    const result = await debugStore.execute({
      projectId: 1,
      definitionId: tab.definitionId,
      environmentId: envStore.selectedEnvironmentId ?? undefined,
      url: tab.draft.url,
      method: tab.draft.method,
      headers: tab.draft.headers,
      queryParams: tab.draft.queryParams,
      body: tab.draft.body || undefined,
      contentType: tab.draft.contentType,
      preRequestScript: tab.draft.preRequestScript || undefined,
      postResponseScript: tab.draft.postResponseScript || undefined,
      assertions: tab.draft.assertions,
      extracts: tab.draft.extracts,
    })
    workspace.setTabResult(tab.id, result)
    message.success('调试完成')
  } catch (e: any) {
    message.error(e.message || '请求失败')
  }
}

function buildParamsFromDraft(draft: RequestDraft): ApiDefinitionParamDTO[] {
  const params: ApiDefinitionParamDTO[] = [
    ...Object.entries(draft.queryParams)
      .filter(([name]) => name)
      .map(([name, value]) => ({
        paramType: 'query' as const,
        name,
        defaultValue: value || '',
        dataType: 'string' as const,
        required: false,
        description: '',
        sortOrder: 0,
      })),
    ...Object.entries(draft.headers)
      .filter(([name]) => name)
      .map(([name, value]) => ({
        paramType: 'header' as const,
        name,
        defaultValue: value || '',
        dataType: 'string' as const,
        required: false,
        description: '',
        sortOrder: 0,
      })),
  ]
  if (draft.body) {
    params.push({
      paramType: 'body',
      name: 'body',
      defaultValue: draft.body,
      dataType: 'string',
      required: false,
      description: '',
      sortOrder: 0,
    })
  }
  return params
}

async function handleSave() {
  const tab = workspace.activeTab
  if (!tab) return

  if (tab.source === 'scratch') {
    scratchName.value = tab.title
    scratchGroupId.value = null
    showScratchDialog.value = true
    if (!groupStore.groupTree.length) {
      try {
        await groupStore.loadTree(PROJECT_ID)
      } catch {
        // group picker is optional
      }
    }
    return
  }

  if (!tab.definitionId) {
    message.error('当前请求未关联接口定义，无法保存')
    return
  }

  try {
    await apiDefinitionApi.update(tab.definitionId, {
      name: tab.title,
      path: tab.draft.url,
      method: tab.draft.method as HttpMethod,
      params: buildParamsFromDraft(tab.draft),
      contentType: tab.draft.contentType,
    }, userId.value)
    workspace.markClean(tab.id)
    message.success('保存成功')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  }
}

async function confirmScratchSave() {
  const tab = workspace.activeTab
  if (!tab || tab.source !== 'scratch') return
  if (!scratchName.value.trim()) {
    message.warning('请输入接口名称')
    return
  }

  scratchSaving.value = true
  try {
    const created = await apiDefinitionApi.create({
      projectId: PROJECT_ID,
      name: scratchName.value.trim(),
      groupId: scratchGroupId.value,
      path: tab.draft.url,
      method: tab.draft.method as HttpMethod,
      params: buildParamsFromDraft(tab.draft),
      contentType: tab.draft.contentType,
    }, userId.value)
    tab.source = 'definition'
    tab.refId = created.id
    tab.definitionId = created.id
    tab.title = scratchName.value.trim()
    tab.method = tab.draft.method
    workspace.markClean(tab.id)
    showScratchDialog.value = false
    message.success('保存成功')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    scratchSaving.value = false
  }
}

let stopResponseResize: (() => void) | null = null

function onResponseResizeStart(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  stopResponseResize?.()
  const startY = event.clientY
  const startHeight = responseHeight.value

  function onMove(moveEvent: PointerEvent) {
    workspace.setLayout({
      responseHeight: clampResponseHeight(startHeight - (moveEvent.clientY - startY)),
    })
  }

  function onUp() {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    document.body.style.removeProperty('user-select')
    document.body.style.removeProperty('cursor')
    stopResponseResize = null
  }

  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'row-resize'
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
  stopResponseResize = onUp
}

onUnmounted(() => stopResponseResize?.())
</script>

<template>
  <div v-if="activeTab" class="request-workspace">
    <n-alert
      v-if="activeTab.loadError"
      type="error"
      :title="activeTab.loadError"
      style="margin: 8px 16px 0;"
    />

    <div class="request-workspace__url-bar">
      <n-select
        :value="activeTab.draft.method"
        :options="METHOD_OPTIONS"
        style="width: 110px; flex-shrink: 0;"
        size="small"
        @update:value="(v: string) => patch({ method: v })"
      />
      <n-input
        :value="activeTab.draft.url"
        placeholder="请求 URL（支持 {{ var }} 变量占位符）"
        size="small"
        clearable
        style="flex: 1;"
        @update:value="(v: string) => patch({ url: v })"
      />
      <n-button
        type="primary"
        data-testid="request-send"
        :loading="executing"
        :disabled="!activeTab.draft.url"
        @click="handleSend"
      >
        发送
      </n-button>
      <n-button quaternary size="small" data-testid="request-save" @click="handleSave">
        保存
      </n-button>
    </div>

    <n-tabs
      v-model:value="requestTab"
      type="line"
      size="small"
      class="request-workspace__tabs"
    >
      <n-tab-pane name="params" tab="Params">
        <key-value-editor
          :pairs="activeTab.draft.queryParams"
          key-placeholder="参数名"
          value-placeholder="参数值（支持 {{ var }}）"
          @update:pairs="(v) => patch({ queryParams: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="auth" tab="Auth">
        <ComingSoonPane title="Auth" />
      </n-tab-pane>
      <n-tab-pane name="headers" tab="Headers">
        <key-value-editor
          :pairs="activeTab.draft.headers"
          key-placeholder="Header 名称"
          value-placeholder="Header 值（支持 {{ var }}）"
          @update:pairs="(v) => patch({ headers: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="body" tab="Body">
        <div class="request-workspace__body-header">
          <n-select
            :value="activeTab.draft.contentType"
            :options="CONTENT_TYPE_OPTIONS"
            size="small"
            style="width: 200px;"
            @update:value="(v: string) => patch({ contentType: v })"
          />
        </div>
        <n-input
          :value="activeTab.draft.body"
          type="textarea"
          :rows="10"
          placeholder="请求体内容（支持 {{ var }} 变量占位符）"
          style="font-family: monospace; font-size: 13px;"
          @update:value="(v: string) => patch({ body: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="scripts" tab="Scripts">
        <div class="request-workspace__script-section">
          <div class="request-workspace__script-label">前置脚本（发送前执行）</div>
          <script-editor
            :model-value="activeTab.draft.preRequestScript"
            @update:model-value="(v) => patch({ preRequestScript: v })"
          />
        </div>
        <div class="request-workspace__script-section">
          <div class="request-workspace__script-label">后置脚本（响应后执行）</div>
          <script-editor
            :model-value="activeTab.draft.postResponseScript"
            @update:model-value="(v) => patch({ postResponseScript: v })"
          />
        </div>
      </n-tab-pane>
      <n-tab-pane name="tests" tab="Tests">
        <div class="request-workspace__script-label">断言</div>
        <assertion-editor
          :assertions="activeTab.draft.assertions"
          @update:assertions="(v) => patch({ assertions: v })"
        />
        <div class="request-workspace__script-label" style="margin-top: 16px;">变量提取</div>
        <extract-editor
          :extracts="activeTab.draft.extracts"
          @update:extracts="(v) => patch({ extracts: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="docs" tab="Docs">
        <ComingSoonPane title="Docs" />
      </n-tab-pane>
      <n-tab-pane name="settings" tab="Settings">
        <ComingSoonPane title="Settings" />
      </n-tab-pane>
    </n-tabs>

    <div
      class="request-workspace__resizer"
      data-testid="response-resizer"
      title="拖拽调整响应区高度"
      @pointerdown="onResponseResizeStart"
    />

    <div class="request-workspace__response" :style="{ height: `${responseHeight}px` }">
      <n-tabs v-model:value="responseTab" type="line" size="small" class="request-workspace__response-tabs">
        <n-tab-pane name="response" tab="响应">
          <ApiWorkspaceResponse :result="activeTab.result" />
        </n-tab-pane>
        <n-tab-pane name="visualize" tab="Visualize">
          <ComingSoonPane title="Visualize" />
        </n-tab-pane>
      </n-tabs>
    </div>

    <n-modal
      v-model:show="showScratchDialog"
      title="保存为接口"
      preset="card"
      style="width: 420px;"
      :mask-closable="false"
    >
      <n-form label-placement="top">
        <n-form-item label="接口名称">
          <n-input v-model:value="scratchName" placeholder="请输入接口名称" />
        </n-form-item>
        <n-form-item label="所属分组（可选）">
          <n-tree-select
            v-model:value="scratchGroupId"
            :options="groupOptions"
            :default-expand-all="true"
            placeholder="选择分组（可选）"
            clearable
            key-field="key"
            label-field="label"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showScratchDialog = false">取消</n-button>
          <n-button type="primary" :loading="scratchSaving" @click="confirmScratchSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.request-workspace {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.request-workspace__url-bar {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  padding: 12px 16px 0;
}

.request-workspace__tabs {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  overflow: auto;
}

.request-workspace__body-header {
  margin-bottom: 8px;
}

.request-workspace__script-section {
  margin-bottom: 16px;
}

.request-workspace__script-label {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--wb-muted, #6b7280);
}

.request-workspace__resizer {
  flex-shrink: 0;
  height: 4px;
  cursor: row-resize;
  background: var(--wb-border, #e5e7eb);
  touch-action: none;
}

.request-workspace__resizer:hover {
  background: var(--api-test-accent, #4098fc);
}

.request-workspace__response {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.request-workspace__response-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 0 16px;
  overflow: auto;
}
</style>
