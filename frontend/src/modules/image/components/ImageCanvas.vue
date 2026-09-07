<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import type { CropAspect, ImageCrop } from '@/modules/image/types/image'

const props = defineProps<{
  src: string | null
  rotate: number
  flipX: boolean
  aspect: CropAspect
}>()

const emit = defineEmits<{
  crop: [crop: ImageCrop | null]
}>()

const imgRef = ref<HTMLImageElement | null>(null)
const paintedSrc = ref<string | null>(null)
const cropper = ref<Cropper | null>(null)

function aspectRatio(aspect: CropAspect): number {
  if (aspect === '1:1') return 1
  if (aspect === '4:3') return 4 / 3
  if (aspect === '16:9') return 16 / 9
  return NaN
}

async function paint() {
  destroyCropper()
  if (!props.src) {
    paintedSrc.value = null
    emit('crop', null)
    return
  }
  const image = new window.Image()
  image.crossOrigin = 'anonymous'
  image.src = props.src
  try {
    await image.decode()
  } catch {
    paintedSrc.value = props.src
    await nextTick()
    mountCropper()
    return
  }
  const swapped = props.rotate % 180 !== 0
  const width = swapped ? image.height : image.width
  const height = swapped ? image.width : image.height
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, width)
  canvas.height = Math.max(1, height)
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    paintedSrc.value = props.src
    await nextTick()
    mountCropper()
    return
  }
  ctx.translate(width / 2, height / 2)
  ctx.rotate((props.rotate * Math.PI) / 180)
  ctx.scale(props.flipX ? -1 : 1, 1)
  ctx.drawImage(image, -image.width / 2, -image.height / 2)
  paintedSrc.value = canvas.toDataURL('image/jpeg', 0.92)
  await nextTick()
  mountCropper()
}

function mountCropper() {
  if (!imgRef.value || !paintedSrc.value) return
  cropper.value = new Cropper(imgRef.value, {
    viewMode: 1,
    dragMode: 'move',
    autoCropArea: 1,
    background: false,
    responsive: true,
    aspectRatio: aspectRatio(props.aspect),
    cropend() {
      emitCrop()
    },
    ready() {
      emitCrop()
    },
  })
}

function emitCrop() {
  const instance = cropper.value
  if (!instance) {
    emit('crop', null)
    return
  }
  const data = instance.getData(true)
  emit('crop', {
    x: Math.max(0, Math.round(data.x)),
    y: Math.max(0, Math.round(data.y)),
    width: Math.max(1, Math.round(data.width)),
    height: Math.max(1, Math.round(data.height)),
  })
}

function destroyCropper() {
  cropper.value?.destroy()
  cropper.value = null
}

function fit() {
  cropper.value?.reset()
}

function oneToOne() {
  cropper.value?.zoomTo(1)
}

watch(
  () => [props.src, props.rotate, props.flipX] as const,
  () => {
    void paint()
  },
  { immediate: true },
)

watch(
  () => props.aspect,
  (aspect) => {
    cropper.value?.setAspectRatio(aspectRatio(aspect))
    emitCrop()
  },
)

onBeforeUnmount(() => destroyCropper())

defineExpose({ fit, oneToOne })
</script>

<template>
  <div class="canvas">
    <div v-if="!src" class="placeholder">选择一张图片后在这里预览和裁剪</div>
    <img v-else-if="paintedSrc" ref="imgRef" :src="paintedSrc" alt="预览" class="crop-target" />
  </div>
</template>

<style scoped>
.canvas {
  position: relative;
  flex: 1;
  min-height: 0;
  background: repeating-conic-gradient(var(--wb-chip-bg) 0% 25%, transparent 0% 50%) 50% / 16px 16px;
  overflow: hidden;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--wb-muted);
  font-size: 13px;
  padding: 24px;
  text-align: center;
}
.crop-target {
  display: block;
  max-width: 100%;
}
.canvas :deep(.cropper-container) {
  max-width: 100%;
}
</style>
