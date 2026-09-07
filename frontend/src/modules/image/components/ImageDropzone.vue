<script setup lang="ts">
import { ImagePlus } from '@lucide/vue'
import type { UploadFileInfo } from 'naive-ui'
import type { QueueItem } from '@/modules/image/composables/useImageSession'
import { formatFileSize } from '@/modules/image/composables/imageHelpers'

const props = defineProps<{
  accept: string
  items: QueueItem[]
  activeId: string | null
}>()

const emit = defineEmits<{
  select: [files: File[]]
  activate: [localId: string]
  remove: [localId: string]
}>()

function onChange({ fileList }: { fileList: UploadFileInfo[] }) {
  const files = fileList.map((entry) => entry.file).filter((file): file is File => !!file)
  if (files.length) emit('select', files)
}

function thumb(item: QueueItem): string | null {
  return item.serverPreviewUrl || item.objectUrl
}
</script>

<template>
  <div class="dropzone">
    <n-upload
      :default-upload="false"
      :show-file-list="false"
      :accept="accept"
      multiple
      @change="onChange"
    >
      <n-upload-dragger class="dragger">
        <div class="empty">
          <n-icon size="32" color="#2080f0"><ImagePlus /></n-icon>
          <p class="title">{{ items.length ? '继续添加图片' : '拖拽图片到此处，或点击上传' }}</p>
          <p class="hint">JPEG / PNG / WebP / GIF / BMP / TIFF / HEIC</p>
          <p class="hint">可多选，单文件不超过 50MB</p>
        </div>
      </n-upload-dragger>
    </n-upload>

    <div v-if="items.length" class="queue">
      <button
        v-for="item in items"
        :key="item.localId"
        type="button"
        class="queue-item"
        :class="{ active: item.localId === activeId }"
        @click="emit('activate', item.localId)"
      >
        <img v-if="thumb(item)" :src="thumb(item)!" class="qthumb" alt="" />
        <div v-else class="qthumb qthumb--ph">预览中</div>
        <div class="qmeta">
          <div class="name" :title="item.file.name">{{ item.file.name }}</div>
          <div class="sub">
            {{ formatFileSize(item.file.size) }}
            <span v-if="item.probing"> · 读取中</span>
            <span v-else-if="item.converting"> · 转换中</span>
            <span v-else-if="item.session"> · {{ item.session.mimeType }}</span>
          </div>
        </div>
        <n-button size="tiny" quaternary @click.stop="emit('remove', item.localId)">移除</n-button>
      </button>
    </div>
  </div>
</template>

<style scoped>
.dropzone {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
}
.dragger {
  min-height: 120px;
}
.empty {
  text-align: center;
  padding: 10px 8px;
}
.title {
  margin: 8px 0 4px;
  font-size: 13px;
  font-weight: 500;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--wb-muted);
}
.queue {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  overflow: auto;
}
.queue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border: 1px solid var(--wb-border);
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.queue-item.active {
  border-color: #2080f0;
  background: var(--wb-chip-bg);
}
.qthumb {
  width: 40px;
  height: 40px;
  object-fit: contain;
  border-radius: 6px;
  background: var(--wb-chip-bg);
  flex: 0 0 auto;
}
.qthumb--ph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: var(--wb-muted);
}
.qmeta {
  min-width: 0;
  flex: 1;
}
.name {
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--wb-muted);
}
</style>
