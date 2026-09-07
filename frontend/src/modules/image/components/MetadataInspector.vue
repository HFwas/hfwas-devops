<script setup lang="ts">
import { computed } from 'vue'
import { useMessage } from 'naive-ui'
import { rawTagEntries } from '@/modules/image/composables/imageHelpers'
import type { ImageMetadataVO, ImageSessionVO } from '@/modules/image/types/image'

const props = defineProps<{
  probing: boolean
  session: ImageSessionVO | null
  metadata: ImageMetadataVO | null
}>()

const message = useMessage()

const summary = computed(() => {
  const meta = props.metadata
  const session = props.session
  if (!meta && !session) return null
  return {
    width: meta?.pixel.width ?? session?.width,
    height: meta?.pixel.height ?? session?.height,
    mime: meta?.format.mime ?? session?.mimeType,
    colorSpace: meta?.pixel.colorSpace ?? '—',
    hasAlpha: meta?.pixel.hasAlpha ? '是' : '否',
    frames: meta?.pixel.frames ?? 1,
    orientation: meta?.capture?.orientation ?? '—',
    orientationApplied: !!meta?.orientationApplied,
    hasGps: meta?.privacy.hasGps ?? false,
    hasFace: meta?.privacy.hasFaceRegions ?? false,
    hasIcc: !!meta?.pixel.hasIcc,
  }
})

const captureItems = computed(() => {
  const c = props.metadata?.capture
  if (!c) return []
  return [
    { label: '厂商', value: c.make },
    { label: '型号', value: c.model },
    { label: '拍摄时间', value: c.takenAt },
    { label: '方向', value: c.orientation != null ? String(c.orientation) : null },
  ].filter((item) => item.value)
})

const copyrightItems = computed(() => {
  const c = props.metadata?.copyright
  if (!c) return []
  return [
    { label: '作者', value: c.artist },
    { label: '版权', value: c.copyright },
  ].filter((item) => item.value)
})

const tags = computed(() => rawTagEntries(props.metadata?.rawTags))

async function copyJson() {
  if (!props.metadata) return
  await navigator.clipboard.writeText(JSON.stringify(props.metadata, null, 2))
  message.success('已复制元数据 JSON')
}
</script>

<template>
  <n-spin :show="probing">
    <n-empty v-if="!session && !probing" description="上传后读取尺寸与拍摄信息" size="small" />
    <n-skeleton v-else-if="probing && !metadata" :repeat="4" text />
    <n-tabs v-else type="line" animated size="small">
      <n-tab-pane name="summary" tab="概要">
        <n-descriptions v-if="summary" :column="1" label-placement="left" size="small">
          <n-descriptions-item label="尺寸">{{ summary.width }} × {{ summary.height }}</n-descriptions-item>
          <n-descriptions-item label="格式">{{ summary.mime }}</n-descriptions-item>
          <n-descriptions-item label="色彩">
            {{ summary.colorSpace }}
            <n-tag v-if="summary.hasIcc" size="tiny" :bordered="false">ICC</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="透明">{{ summary.hasAlpha }}</n-descriptions-item>
          <n-descriptions-item label="帧数">
            {{ summary.frames }}
            <n-tag v-if="summary.frames > 1" size="tiny" type="warning" :bordered="false">仅处理第一帧</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="方向">
            {{ summary.orientation }}
            <n-tag v-if="summary.orientationApplied" size="tiny" type="success" :bordered="false">画面已纠正</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="位置">
            <n-tag v-if="summary.hasGps" type="warning" size="small">含 GPS</n-tag>
            <span v-else>无 GPS</span>
          </n-descriptions-item>
          <n-descriptions-item v-if="summary.hasFace" label="人脸">
            <n-tag type="info" size="small">含人脸区域</n-tag>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>
      <n-tab-pane name="meta" tab="元数据">
        <n-space vertical>
          <n-button size="tiny" :disabled="!metadata" @click="copyJson">复制 JSON</n-button>
          <n-collapse>
            <n-collapse-item title="拍摄" name="capture">
              <n-descriptions v-if="captureItems.length" :column="1" size="small" label-placement="left">
                <n-descriptions-item v-for="item in captureItems" :key="item.label" :label="item.label">
                  {{ item.value }}
                </n-descriptions-item>
              </n-descriptions>
              <n-text v-else depth="3">无拍摄信息</n-text>
            </n-collapse-item>
            <n-collapse-item title="GPS" name="gps">
              <n-alert v-if="metadata?.privacy?.hasGps" type="warning" :bordered="false">
                {{ metadata.gps?.lat }}, {{ metadata.gps?.lng }}
              </n-alert>
              <n-text v-else depth="3">未检测到位置信息</n-text>
            </n-collapse-item>
            <n-collapse-item title="版权" name="copyright">
              <n-descriptions v-if="copyrightItems.length" :column="1" size="small" label-placement="left">
                <n-descriptions-item v-for="item in copyrightItems" :key="item.label" :label="item.label">
                  {{ item.value }}
                </n-descriptions-item>
              </n-descriptions>
              <n-text v-else depth="3">无版权字段</n-text>
            </n-collapse-item>
            <n-collapse-item :title="`厂商标签 (${tags.length})`" name="raw">
              <n-empty v-if="!tags.length" description="无原始标签" size="small" />
              <n-descriptions v-else :column="1" size="small" label-placement="left">
                <n-descriptions-item v-for="item in tags" :key="item.key" :label="item.key">
                  <span class="tag-value">{{ item.value }}</span>
                </n-descriptions-item>
              </n-descriptions>
            </n-collapse-item>
          </n-collapse>
        </n-space>
      </n-tab-pane>
    </n-tabs>
  </n-spin>
</template>

<style scoped>
.tag-value {
  word-break: break-all;
  font-size: 12px;
}
</style>
