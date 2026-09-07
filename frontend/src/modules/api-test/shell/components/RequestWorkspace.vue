<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/modules/user/stores/auth'
import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import type { HttpMethod, ApiDefinitionParamDTO } from '@/modules/api-test/define/types/definition'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import ApiWorkspaceResponse from '@/modules/api-test/define/components/ApiWorkspaceResponse.vue'
import KeyValueEditor from '@/modules/api-test/shared/components/KeyValueEditor.vue'
import ScriptEditor from '@/modules/api-test/debug/components/ScriptEditor.vue'
import AssertionEditor from '@/modules/api-test/debug/components/AssertionEditor.vue'
import ExtractEditor from '@/modules/api-test/debug/components/ExtractEditor.vue'
import AuthEditor from '@/modules/api-test/debug/components/AuthEditor.vue'
import BodyEditor from '@/modules/api-test/debug/components/BodyEditor.vue'
import RequestSettings from '@/modules/api-test/debug/components/RequestSettings.vue'
import { useDebugStore } from '@/modules/api-test/debug/stores/debug'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { RequestDraft } from '@/modules/api-test/shell/types/workspace'
import { createRequestInCollection } from '@/modules/api-test/shell/utils/createRequestInCollection'
import { clampResponseHeight } from '@/modules/api-test/shell/utils/layoutPersist'
import {
  buildBreadcrumbSegments,
  resolveFolderNames,
} from '@/modules/api-test/shell/utils/breadcrumbPath'
import { draftToCurl } from '@/modules/api-test/debug/utils/curlExport'
import { buildExecutePayload } from '@/modules/api-test/debug/utils/draftExecute'
import { interpolate } from '@/modules/api-test/debug/utils/interpolate'
import { mergePathParams, replaceQuery, syncQueryPairsFromUrl } from '@/modules/api-test/debug/utils/urlParams'
import { enabledNamedPairs } from '@/modules/api-test/shared/utils/keyValue'
import type { KeyValuePair } from '@/modules/api-test/shared/types/keyValue'
import type { AuthConfig } from '@/modules/api-test/debug/utils/auth'
import type { BodyMode } from '@/modules/api-test/debug/utils/bodyMode'

const PROJECT_ID = 1

const message = useMessage()
const authStore = useAuthStore()
const workspace = useWorkspaceStore()
const debugStore = useDebugStore()
const envStore = useEnvironmentStore()
const collectionStore = useCollectionStore()

const { activeTab, responseHeight } = storeToRefs(workspace)
const { pageResult, currentDetail, detailsById } = storeToRefs(collectionStore)
const executing = computed(() => debugStore.executing)

function requireUserId(): number | null {
  const raw = authStore.user?.id
  const id = Number(raw)
  if (raw == null || !Number.isInteger(id) || id <= 0) {
    message.warning('请先登录后再保存')
    return null
  }
  return id
}

const METHOD_OPTIONS = HTTP_METHOD_OPTIONS.map((o) => ({ label: o.label, value: o.value }))

const requestTab = ref('query')
const moreTab = ref('scripts')
const showScratchDialog = ref(false)
const scratchSaving = ref(false)
const scratchName = ref('')
const scratchCollectionId = ref<number | null>(null)

const collectionOptions = computed(() =>
  (pageResult.value.records ?? []).map((c) => ({ label: c.name, value: c.id })),
)

const breadcrumbSegments = computed(() => {
  const tab = activeTab.value
  if (!tab) return []

  let collectionName: string | null | undefined
  let folderNames: string[] = []

  if (tab.source === 'collection' && tab.collectionId != null) {
    const detail =
      detailsById.value[tab.collectionId]
      ?? (currentDetail.value?.id === tab.collectionId ? currentDetail.value : null)
    collectionName =
      detail?.name
      ?? pageResult.value.records?.find((c) => c.id === tab.collectionId)?.name
      ?? null
    folderNames = resolveFolderNames(detail?.folders ?? [], tab.folderId)
  }

  return buildBreadcrumbSegments({
    source: tab.source,
    title: tab.title,
    collectionName,
    folderNames,
  })
})

const breadcrumbPrefix = computed(() => breadcrumbSegments.value.slice(0, -1))

const previewUrl = computed(() => {
  const tab = activeTab.value
  if (!tab?.draft.url) return ''
  const vars: Record<string, string> = {}
  const secrets: Record<string, boolean> = {}
  const detail = envStore.currentDetail
  if (detail && detail.id === envStore.selectedEnvironmentId) {
    for (const item of detail.variables || []) {
      vars[item.name] = item.value
      if (item.isSecret) secrets[item.name] = true
    }
  }
  const payload = buildExecutePayload(tab.draft)
  const search = payload.queryParams && Object.keys(payload.queryParams).length
    ? `?${new URLSearchParams(payload.queryParams).toString()}`
    : ''
  return interpolate(`${payload.url}${search}`, vars, secrets)
})

watch(
  () => envStore.selectedEnvironmentId,
  async (id) => {
    if (id != null && envStore.currentDetail?.id !== id) {
      try {
        await envStore.loadDetail(id)
      } catch {
        // preview stays unresolved
      }
    }
  },
  { immediate: true },
)

function patch(partial: Partial<RequestDraft>) {
  const tab = activeTab.value
  if (!tab) return
  workspace.patchDraft(tab.id, partial)
  if (partial.method) {
    workspace.setTabMeta(tab.id, { method: partial.method })
  }
}

function onTitleChange(value: string) {
  const tab = activeTab.value
  if (!tab) return
  workspace.setTabTitle(tab.id, value)
}

function onUrlChange(value: string) {
  const tab = activeTab.value
  if (!tab) return
  workspace.patchDraft(tab.id, {
    url: value,
    queryParams: syncQueryPairsFromUrl(value, tab.draft.queryParams),
    pathParams: mergePathParams(value, tab.draft.pathParams),
  })
}

function onQueryChange(pairs: KeyValuePair[]) {
  const tab = activeTab.value
  if (!tab) return
  workspace.patchDraft(tab.id, {
    queryParams: pairs,
    url: replaceQuery(tab.draft.url, pairs),
  })
}

async function handleSend() {
  const tab = workspace.activeTab
  if (!tab?.draft.url) { message.warning('请输入请求 URL'); return }
  try {
    const result = await debugStore.execute(buildExecutePayload(tab.draft, {
      projectId: 1,
      definitionId: tab.definitionId,
      environmentId: envStore.selectedEnvironmentId ?? undefined,
    }))
    workspace.setTabResult(tab.id, result)
    message.success('调试完成')
    debugStore.bumpHistoryEpoch()
  } catch (e: any) {
    message.error(e.message || '请求失败')
  }
}

async function copyCurl() {
  const tab = workspace.activeTab
  if (!tab) return
  try {
    await navigator.clipboard.writeText(draftToCurl(tab.draft))
    message.success('已复制 cURL')
  } catch {
    message.error('复制失败')
  }
}

function onSendHotkey(event: KeyboardEvent) {
  if (!(event.ctrlKey || event.metaKey) || event.key !== 'Enter') return
  event.preventDefault()
  void handleSend()
}

function pairsToParams(pairs: KeyValuePair[], paramType: ApiDefinitionParamDTO['paramType']): ApiDefinitionParamDTO[] {
  return enabledNamedPairs(pairs).map((row) => ({
    paramType,
    name: row.key,
    defaultValue: row.value || '',
    dataType: 'string' as const,
    required: false,
    description: '',
    sortOrder: 0,
  }))
}

function buildParamsFromDraft(draft: RequestDraft): ApiDefinitionParamDTO[] {
  const params: ApiDefinitionParamDTO[] = [
    ...pairsToParams(draft.queryParams, 'query'),
    ...pairsToParams(draft.headers, 'header'),
    ...pairsToParams(draft.pathParams, 'path'),
  ]
  if (draft.bodyMode !== 'none' && draft.body) {
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
  const userId = requireUserId()
  if (userId == null) return

  if (tab.source === 'scratch') {
    scratchName.value = tab.title
    scratchCollectionId.value = null
    showScratchDialog.value = true
    if (!pageResult.value.records?.length) {
      try {
        await collectionStore.loadPage({ projectId: PROJECT_ID, pageNo: 1, pageSize: 200 })
      } catch {
        // collection picker is optional until confirm
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
      description: tab.draft.description,
      params: buildParamsFromDraft(tab.draft),
      contentType: tab.draft.contentType,
    }, userId)
    if (tab.source === 'collection' && tab.collectionId != null && tab.refId != null) {
      await collectionStore.updateItem(tab.collectionId, tab.refId, {
        definitionId: tab.definitionId,
        name: tab.title,
      })
      await collectionStore.loadDetail(tab.collectionId)
    }
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
  if (scratchCollectionId.value == null) {
    message.warning('请选择目标集合')
    return
  }
  const userId = requireUserId()
  if (userId == null) return

  scratchSaving.value = true
  try {
    const name = scratchName.value.trim()
    const created = await createRequestInCollection({
      projectId: PROJECT_ID,
      collectionId: scratchCollectionId.value,
      userId,
      name,
      method: tab.draft.method as HttpMethod,
      path: tab.draft.url || '/',
    })
    await apiDefinitionApi.update(created.definitionId, {
      name,
      path: tab.draft.url || '/',
      method: tab.draft.method as HttpMethod,
      description: tab.draft.description,
      params: buildParamsFromDraft(tab.draft),
      contentType: tab.draft.contentType,
    }, userId)
    workspace.setTabMeta(tab.id, {
      source: 'collection',
      refId: created.itemId,
      definitionId: created.definitionId,
      collectionId: scratchCollectionId.value,
      title: name,
      method: tab.draft.method,
    })
    workspace.markClean(tab.id)
    // Refresh collection detail so CollectionsSidebar detailCache picks up the new item
    await collectionStore.loadDetail(scratchCollectionId.value)
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

onUnmounted(() => {
  stopResponseResize?.()
  window.removeEventListener('keydown', onSendHotkey)
})

onMounted(() => {
  window.addEventListener('keydown', onSendHotkey)
})
</script>

<template>
  <div v-if="activeTab" class="request-workspace">
    <n-alert
      v-if="activeTab.loadError"
      type="error"
      :title="activeTab.loadError"
      class="request-workspace__alert"
    />

    <div class="request-workspace__name-row">
      <div
        v-if="breadcrumbPrefix.length"
        data-testid="request-breadcrumb"
        class="request-workspace__breadcrumb"
      >
        <template v-for="(seg, i) in breadcrumbPrefix" :key="`${i}-${seg}`">
          <span class="request-workspace__breadcrumb-seg">{{ seg }}</span>
          <span class="request-workspace__breadcrumb-sep" aria-hidden="true">›</span>
        </template>
      </div>
      <n-input
        :value="activeTab.title"
        data-testid="request-name"
        placeholder="请求名称"
        size="small"
        class="request-workspace__name"
        @update:value="onTitleChange"
      />
    </div>

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
        @update:value="onUrlChange"
      />
      <n-button
        type="primary"
        size="small"
        data-testid="request-send"
        :loading="executing"
        :disabled="!activeTab.draft.url"
        @click="handleSend"
      >
        Send
      </n-button>
      <n-button quaternary size="small" data-testid="request-save" @click="handleSave">
        Save
      </n-button>
      <n-button quaternary size="small" data-testid="request-curl" @click="copyCurl">
        cURL
      </n-button>
    </div>
    <div v-if="previewUrl" class="request-workspace__preview" data-testid="url-preview">
      {{ previewUrl }}
    </div>

    <n-tabs
      v-model:value="requestTab"
      type="line"
      size="small"
      class="request-workspace__tabs"
    >
      <n-tab-pane name="query" tab="Query">
        <key-value-editor
          :pairs="activeTab.draft.queryParams"
          key-placeholder="参数名"
          value-placeholder="参数值（支持 {{ var }}）"
          @update:pairs="onQueryChange"
        />
      </n-tab-pane>
      <n-tab-pane name="path" tab="Path">
        <key-value-editor
          :pairs="activeTab.draft.pathParams"
          key-placeholder="变量名"
          value-placeholder="变量值"
          @update:pairs="(v) => patch({ pathParams: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="headers" tab="Headers">
        <p v-if="activeTab.draft.auth.type !== 'none'" class="request-workspace__hint">
          认证头由 Auth 生成，发送时自动附加。
        </p>
        <key-value-editor
          :pairs="activeTab.draft.headers"
          key-placeholder="Header 名称"
          value-placeholder="Header 值（支持 {{ var }}）"
          @update:pairs="(v) => patch({ headers: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="auth" tab="Auth">
        <auth-editor
          :auth="activeTab.draft.auth"
          @update:auth="(v: AuthConfig) => patch({ auth: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="body" tab="Body">
        <body-editor
          :body-mode="activeTab.draft.bodyMode"
          :body="activeTab.draft.body"
          :content-type="activeTab.draft.contentType"
          :form-fields="activeTab.draft.formFields"
          @update:body-mode="(v: BodyMode) => patch({ bodyMode: v })"
          @update:body="(v: string) => patch({ body: v })"
          @update:content-type="(v: string) => patch({ contentType: v })"
          @update:form-fields="(v) => patch({ formFields: v })"
        />
      </n-tab-pane>
      <n-tab-pane name="more" tab="More">
        <n-tabs v-model:value="moreTab" type="line" size="small">
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
            <n-input
              data-testid="docs-description"
              :value="activeTab.draft.description"
              type="textarea"
              :rows="12"
              placeholder="接口描述 / 文档"
              @update:value="(v: string) => patch({ description: v })"
            />
          </n-tab-pane>
          <n-tab-pane name="settings" tab="Settings">
            <request-settings
              :timeout-ms="activeTab.draft.timeoutMs"
              :follow-redirects="activeTab.draft.followRedirects"
              @update:timeout-ms="(v: number) => patch({ timeoutMs: v })"
              @update:follow-redirects="(v: boolean) => patch({ followRedirects: v })"
            />
          </n-tab-pane>
        </n-tabs>
      </n-tab-pane>
    </n-tabs>

    <div
      class="request-workspace__resizer"
      data-testid="response-resizer"
      title="拖拽调整响应区高度"
      @pointerdown="onResponseResizeStart"
    />

    <div class="request-workspace__response" :style="{ height: `${responseHeight}px` }">
      <ApiWorkspaceResponse :result="activeTab.result" />
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
        <n-form-item label="目标集合">
          <n-select
            v-model:value="scratchCollectionId"
            :options="collectionOptions"
            placeholder="选择集合"
            clearable
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

.request-workspace__alert {
  margin: var(--api-density-pad-y, 6px) var(--api-density-pad-x, 10px) 0;
}

.request-workspace__name-row {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 4px 6px;
  align-items: center;
  padding: var(--api-density-pad-y, 6px) var(--api-density-pad-x, 10px) 0;
}

.request-workspace__breadcrumb {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  max-width: 100%;
  font-size: 13px;
  color: var(--wb-muted, #6b7280);
}

.request-workspace__breadcrumb-sep {
  color: var(--wb-muted, #9ca3af);
}

.request-workspace__name {
  max-width: 480px;
  flex: 1;
  min-width: 120px;
}

.request-workspace__name :deep(.n-input__input-el) {
  font-size: 15px;
  font-weight: 600;
}

.request-workspace__url-bar {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  padding: var(--api-density-pad-y, 6px) var(--api-density-pad-x, 10px) 0;
}

.request-workspace__preview {
  padding: 2px var(--api-density-pad-x, 10px) 0;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
}

.request-workspace__hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.request-workspace__tabs {
  flex: 1;
  min-height: 0;
  padding: 0 var(--api-density-pad-x, 10px);
  overflow: auto;
}

.request-workspace__tabs :deep(.n-tabs-bar) {
  background-color: var(--api-test-accent, #4098fc);
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
  padding: 0 var(--api-density-pad-x, 10px);
  overflow: auto;
}

.request-workspace__response-tabs :deep(.n-tabs-bar) {
  background-color: var(--api-test-accent, #4098fc);
}
</style>
