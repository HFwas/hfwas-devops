import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  convertImage,
  createImageSession,
  deleteImageSession,
  downloadImageResult,
  fetchConvertJob,
  fetchImageHistory,
  fetchImageMetadata,
  fetchImagePreviewBlob,
} from '@/modules/image/api/image'
import { formatFileSize, isPendingConvert, nextLocalId, waitForConvert, cropForConvert, mapPool } from '@/modules/image/composables/imageHelpers'
import type {
  ImageConvertRequest,
  ImageConvertVO,
  ImageCropEvent,
  ImageGeometry,
  ImageHistoryVO,
  ImageMetadataVO,
  ImageSessionVO,
  PreviewMode,
} from '@/modules/image/types/image'
import { isApiError } from '@/shared/errors/apiError'

export { formatFileSize, isPendingConvert }

const ACCEPT =
  '.jpg,.jpeg,.png,.webp,.gif,.bmp,.tif,.tiff,.heic,.heif,image/jpeg,image/png,image/webp,image/gif,image/bmp,image/tiff,image/heic,image/heif'
const MAX_BYTES = 50 * 1024 * 1024

export interface QueueItem {
  localId: string
  file: File
  objectUrl: string
  serverPreviewUrl: string | null
  session: ImageSessionVO | null
  metadata: ImageMetadataVO | null
  result: ImageConvertVO | null
  resultPreviewUrl: string | null
  geometry: ImageGeometry
  displayedWidth: number
  displayedHeight: number
  probing: boolean
  converting: boolean
  error: string | null
}

export function useImageSession() {
  const items = ref<QueueItem[]>([])
  const activeId = ref<string | null>(null)
  const error = ref<string | null>(null)
  const previewMode = ref<PreviewMode>('original')
  const targetFormat = ref<ImageConvertRequest['targetFormat']>('jpeg')
  const quality = ref(85)
  const maxSide = ref<number | null>(null)
  const stripMetadata = ref(true)
  const applyOrientation = ref(true)
  const convertingAll = ref(false)
  const history = ref<ImageHistoryVO[]>([])

  const active = computed(() => items.value.find((item) => item.localId === activeId.value) ?? null)
  const localFile = computed(() => active.value?.file ?? null)
  const session = computed(() => active.value?.session ?? null)
  const metadata = computed(() => active.value?.metadata ?? null)
  const result = computed(() => active.value?.result ?? null)
  const probing = computed(() => active.value?.probing ?? false)
  const converting = computed(() => active.value?.converting ?? convertingAll.value)
  const geometry = computed(() => active.value?.geometry ?? { rotate: 0, flipX: false, flipY: false, crop: null })

  const displayUrl = computed(() => {
    const item = active.value
    if (!item) return null
    if (previewMode.value === 'result' && item.resultPreviewUrl) return item.resultPreviewUrl
    if (item.session?.needsServerPreview && item.serverPreviewUrl) return item.serverPreviewUrl
    return item.objectUrl
  })

  const needsServerPreviewHint = computed(
    () => !!active.value?.session?.needsServerPreview && !active.value.serverPreviewUrl,
  )

  const hasFile = computed(() => items.value.length > 0)

  async function refreshHistory() {
    try {
      history.value = await fetchImageHistory(20)
    } catch {
      history.value = []
    }
  }

  async function probeItem(item: QueueItem) {
    item.probing = true
    item.error = null
    try {
      const created = await createImageSession(item.file)
      item.session = created
      if (created.needsServerPreview) {
        try {
          const blob = await fetchImagePreviewBlob(created.sessionId)
          item.serverPreviewUrl = URL.createObjectURL(blob)
        } catch (e) {
          item.error = e instanceof Error ? e.message : '服务端预览失败'
        }
      }
      try {
        item.metadata = await fetchImageMetadata(created.sessionId)
        if (item.metadata.privacy?.hasGps) {
          stripMetadata.value = true
        }
      } catch (e) {
        item.error = e instanceof Error ? e.message : '读取元数据失败'
      }
    } catch (e) {
      item.error = isApiError(e) || e instanceof Error ? e.message : '上传失败'
    } finally {
      item.probing = false
    }
  }

  async function addFiles(files: File[]) {
    const accepted: QueueItem[] = []
    for (const file of files) {
      if (file.size > MAX_BYTES) {
        error.value = `${file.name} 超过 50MB 限制`
        continue
      }
      const dup = items.value.some(
        (item) =>
          item.file.name === file.name &&
          item.file.size === file.size &&
          item.file.lastModified === file.lastModified,
      )
      if (dup) continue
      const item = reactive<QueueItem>({
        localId: nextLocalId(),
        file,
        objectUrl: URL.createObjectURL(file),
        serverPreviewUrl: null,
        session: null,
        metadata: null,
        result: null,
        resultPreviewUrl: null,
        geometry: { rotate: 0, flipX: false, flipY: false, crop: null },
        displayedWidth: 0,
        displayedHeight: 0,
        probing: true,
        converting: false,
        error: null,
      })
      items.value.push(item)
      accepted.push(item)
    }
    if (accepted.length === 0) return
    if (!activeId.value) {
      activeId.value = accepted[0].localId
      previewMode.value = 'original'
    }
    error.value = null
    await mapPool(accepted, 2, (item) => probeItem(item))
    const firstError = items.value.find((item) => item.error)?.error
    if (firstError) error.value = firstError
  }

  async function selectFile(file: File) {
    await addFiles([file])
  }

  function activate(localId: string) {
    activeId.value = localId
    previewMode.value = 'original'
    error.value = active.value?.error ?? null
  }

  function revokeItemUrls(item: QueueItem) {
    URL.revokeObjectURL(item.objectUrl)
    if (item.serverPreviewUrl) URL.revokeObjectURL(item.serverPreviewUrl)
    if (item.resultPreviewUrl) URL.revokeObjectURL(item.resultPreviewUrl)
  }

  async function removeItem(localId: string) {
    const index = items.value.findIndex((item) => item.localId === localId)
    if (index < 0) return
    const [removed] = items.value.splice(index, 1)
    revokeItemUrls(removed)
    if (removed.session) {
      await deleteImageSession(removed.session.sessionId)
    }
    if (activeId.value === localId) {
      activeId.value = items.value[0]?.localId ?? null
      previewMode.value = 'original'
    }
  }

  function rotate(delta: number) {
    if (!active.value) return
    active.value.geometry.rotate = (((active.value.geometry.rotate + delta) % 360) + 360) % 360
  }

  function toggleFlipX() {
    if (!active.value) return
    active.value.geometry.flipX = !active.value.geometry.flipX
  }

  function resetGeometry() {
    if (!active.value) return
    active.value.geometry.rotate = 0
    active.value.geometry.flipX = false
    active.value.geometry.flipY = false
    active.value.geometry.crop = null
  }

  function setCrop(event: ImageCropEvent | null) {
    if (!active.value) return
    if (!event) {
      active.value.geometry.crop = null
      active.value.displayedWidth = 0
      active.value.displayedHeight = 0
      return
    }
    active.value.geometry.crop = event.crop
    active.value.displayedWidth = event.displayedWidth
    active.value.displayedHeight = event.displayedHeight
  }

  function buildRequest(item: QueueItem): ImageConvertRequest {
    const orientedWidth = item.session?.orientedWidth || item.session?.width || 0
    const orientedHeight = item.session?.orientedHeight || item.session?.height || 0
    const crop = cropForConvert({
      crop: item.geometry.crop,
      displayedWidth: item.displayedWidth,
      displayedHeight: item.displayedHeight,
      orientedWidth,
      orientedHeight,
      rotate: item.geometry.rotate,
    })
    return {
      targetFormat: targetFormat.value,
      quality: targetFormat.value === 'png' ? undefined : quality.value,
      maxSide: maxSide.value || null,
      stripMetadata: stripMetadata.value,
      applyOrientation: applyOrientation.value,
      geometry: {
        rotate: item.geometry.rotate,
        flipX: item.geometry.flipX,
        flipY: item.geometry.flipY,
        crop,
      },
    }
  }

  async function convertItem(item: QueueItem, replacePreview: boolean) {
    if (!item.session) {
      item.error = '请先上传图片'
      return
    }
    item.converting = true
    item.error = null
    try {
      const converted = await waitForConvert(
        item.session.sessionId,
        await convertImage(item.session.sessionId, buildRequest(item)),
        fetchConvertJob,
      )
      item.result = converted
      const downloaded = await downloadImageResult(item.session.sessionId, converted.resultFileName)
      if (replacePreview) {
        if (item.resultPreviewUrl) URL.revokeObjectURL(item.resultPreviewUrl)
        item.resultPreviewUrl = URL.createObjectURL(downloaded.blob)
        if (item.localId === activeId.value) previewMode.value = 'result'
      } else {
        triggerDownload(downloaded.blob, downloaded.filename)
      }
      await refreshHistory()
    } catch (e) {
      item.error = e instanceof Error ? e.message : '转换失败'
    } finally {
      item.converting = false
    }
  }

  async function convert(replacePreview: boolean) {
    if (!active.value) {
      error.value = '请先上传图片'
      return
    }
    error.value = null
    await convertItem(active.value, replacePreview)
    error.value = active.value.error
  }

  async function convertAll(replacePreview: boolean) {
    const ready = items.value.filter((item) => item.session)
    if (ready.length === 0) {
      error.value = '请先上传图片'
      return
    }
    convertingAll.value = true
    error.value = null
    try {
      for (const item of ready) {
        await convertItem(item, replacePreview)
      }
      const failed = items.value.find((item) => item.error)
      error.value = failed?.error ?? null
    } finally {
      convertingAll.value = false
    }
  }

  async function reset(revokeSession = true) {
    const snapshot = [...items.value]
    items.value = []
    activeId.value = null
    previewMode.value = 'original'
    error.value = null
    for (const item of snapshot) {
      revokeItemUrls(item)
      if (revokeSession && item.session) {
        await deleteImageSession(item.session.sessionId)
      }
    }
  }

  onMounted(() => {
    void refreshHistory()
  })

  onUnmounted(() => {
    void reset(true)
  })

  return {
    ACCEPT,
    items,
    activeId,
    active,
    localFile,
    session,
    metadata,
    result,
    probing,
    converting,
    convertingAll,
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
    hasFile,
    history,
    addFiles,
    selectFile,
    activate,
    removeItem,
    rotate,
    toggleFlipX,
    resetGeometry,
    setCrop,
    convert,
    convertAll,
    reset,
    refreshHistory,
  }
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
