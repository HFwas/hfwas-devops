export interface ImageSessionVO {
  sessionId: string
  fileName: string
  fileSize: number
  mimeType: string
  width: number
  height: number
  needsServerPreview: boolean
  previewUrl: string
  expiresAt: string
}

export interface ImageMetadataVO {
  pixel: {
    width: number
    height: number
    colorSpace?: string | null
    hasAlpha: boolean
    frames: number
    hasIcc?: boolean
  }
  format: {
    mime: string
    ext: string
  }
  capture?: {
    make?: string | null
    model?: string | null
    takenAt?: string | null
    orientation?: number | null
  } | null
  gps?: {
    lat: number
    lng: number
  } | null
  copyright?: {
    artist?: string | null
    copyright?: string | null
  } | null
  privacy: {
    hasGps: boolean
    hasFaceRegions: boolean
  }
  rawTags?: Record<string, string>
  orientationApplied?: boolean
}

export interface ImageCrop {
  x: number
  y: number
  width: number
  height: number
}

export interface ImageGeometry {
  rotate: number
  flipX: boolean
  flipY: boolean
  crop?: ImageCrop | null
}

export interface ImageConvertRequest {
  targetFormat: 'jpeg' | 'png' | 'webp' | 'tiff'
  quality?: number
  maxSide?: number | null
  stripMetadata: boolean
  applyOrientation: boolean
  geometry?: ImageGeometry
}

export interface ImageConvertVO {
  sessionId: string
  resultFileName: string
  resultSize: number
  mimeType: string
  width: number
  height: number
  downloadUrl: string
  strippedGps: boolean
  status?: 'completed' | 'queued' | 'running' | 'failed' | string
  jobId?: string | null
  errorMessage?: string | null
}

export interface ImageHistoryVO {
  id: number | string
  sessionId: string
  fileName: string
  sourceMime: string
  targetFormat: string
  resultFileName: string
  resultSize: number
  width: number
  height: number
  strippedGps: boolean
  status: string
  createTime: string
}

export interface ImageHealthVO {
  magick: boolean
  exiftool: boolean
  heicDelegate: boolean
}

export type CropAspect = 'free' | '1:1' | '4:3' | '16:9'
export type PreviewMode = 'original' | 'result'
