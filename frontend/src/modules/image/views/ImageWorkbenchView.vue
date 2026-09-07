<script setup lang="ts">
import { computed, ref } from 'vue'
import ImageDropzone from '@/modules/image/components/ImageDropzone.vue'
import ImageCanvas from '@/modules/image/components/ImageCanvas.vue'
import ImageToolbar from '@/modules/image/components/ImageToolbar.vue'
import MetadataInspector from '@/modules/image/components/MetadataInspector.vue'
import ConvertPanel from '@/modules/image/components/ConvertPanel.vue'
import { formatFileSize, useImageSession } from '@/modules/image/composables/useImageSession'
import type { CropAspect, ImageCropEvent } from '@/modules/image/types/image'

const {
  ACCEPT,
  items,
  activeId,
  session,
  metadata,
  result,
  probing,
  converting,
  error,
  previewMode,
  geometry,
  targetFormat,
  quality,
  maxSide,
  stripMetadata,
  applyOrientation,
  displayUrl,
  needsServerPreviewHint,
  history,
  addFiles,
  activate,
  removeItem,
  rotate,
  toggleFlipX,
  resetGeometry,
  setCrop,
  convert,
  convertAll,
} = useImageSession()

const aspect = ref<CropAspect>('free')
const canvasRef = ref<InstanceType<typeof ImageCanvas> | null>(null)
const inspectorTab = ref<'inspect' | 'convert'>('inspect')
const hasGps = computed(() => !!metadata.value?.privacy?.hasGps)

function onCrop(event: ImageCropEvent | null) {
  setCrop(event)
}
</script>

<template>
  <div class="workbench">
    <aside class="pane pane-left">
      <div class="pane-title">源文件队列</div>
      <ImageDropzone
        :accept="ACCEPT"
        :items="items"
        :active-id="activeId"
        @select="addFiles"
        @activate="activate"
        @remove="removeItem"
      />
      <div class="history">
        <div class="pane-title">最近转换</div>
        <n-empty v-if="!history.length" description="转换后会留下记录，不含原图" size="small" />
        <ul v-else class="history-list">
          <li v-for="row in history" :key="String(row.id)">
            <div class="name" :title="row.fileName">{{ row.fileName }}</div>
            <div class="sub">
              {{ row.targetFormat }} · {{ row.width }}×{{ row.height }}
              <span v-if="row.resultSize"> · {{ formatFileSize(row.resultSize) }}</span>
            </div>
          </li>
        </ul>
      </div>
    </aside>

    <section class="pane pane-center">
      <ImageToolbar
        v-model:aspect="aspect"
        v-model:preview-mode="previewMode"
        :has-result="!!result"
        :server-decode="!!session?.needsServerPreview"
        @fit="canvasRef?.fit()"
        @one-to-one="canvasRef?.oneToOne()"
        @rotate-left="rotate(-90)"
        @rotate-right="rotate(90)"
        @flip="toggleFlipX()"
        @reset="resetGeometry()"
      />
      <n-alert v-if="error" type="error" :bordered="false" class="alert">
        {{ error }}
      </n-alert>
      <n-alert v-if="needsServerPreviewHint" type="info" :bordered="false" class="alert">
        浏览器无法解码该格式，正在请求服务端预览
      </n-alert>
      <ImageCanvas
        ref="canvasRef"
        :src="displayUrl"
        :rotate="geometry.rotate"
        :flip-x="geometry.flipX"
        :aspect="aspect"
        @crop="onCrop"
      />
    </section>

    <aside class="pane pane-right">
      <n-tabs v-model:value="inspectorTab" type="line" animated size="small">
        <n-tab-pane name="inspect" tab="检查器">
          <MetadataInspector :probing="probing" :session="session" :metadata="metadata" />
        </n-tab-pane>
        <n-tab-pane name="convert" tab="转换">
          <ConvertPanel
            v-model:target-format="targetFormat"
            v-model:quality="quality"
            v-model:max-side="maxSide"
            v-model:strip-metadata="stripMetadata"
            v-model:apply-orientation="applyOrientation"
            :converting="converting"
            :queue-count="items.length"
            :has-gps="hasGps"
            :result="result"
            @convert-download="convert(false)"
            @convert-preview="convert(true)"
            @convert-all="convertAll(false)"
          />
        </n-tab-pane>
      </n-tabs>
    </aside>
  </div>
</template>

<style scoped>
.workbench {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 300px;
  gap: 12px;
  height: calc(100vh - 56px);
  padding: 12px 16px 16px;
  box-sizing: border-box;
}
.pane {
  background: var(--wb-card-bg);
  border: 1px solid var(--wb-border);
  border-radius: 10px;
  padding: 12px;
  min-height: 0;
  overflow: auto;
}
.pane-center {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}
.pane-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pane-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.alert {
  flex: 0 0 auto;
}
.history {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border-top: 1px solid var(--wb-border);
  padding-top: 10px;
}
.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.history-list .name {
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-list .sub {
  font-size: 11px;
  color: var(--wb-muted);
}
@media (max-width: 960px) {
  .workbench {
    grid-template-columns: 1fr;
    height: auto;
  }
  .pane-center {
    min-height: 480px;
  }
}
</style>
