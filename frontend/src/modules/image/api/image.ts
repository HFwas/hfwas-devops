import { del, get, getBlob, post, postFormData } from '@/shared/api/request'
import type {
  ImageConvertRequest,
  ImageConvertVO,
  ImageHealthVO,
  ImageHistoryVO,
  ImageMetadataVO,
  ImageSessionVO,
} from '@/modules/image/types/image'

const PREFIX = '/api/image'

export async function fetchImageHealth(): Promise<ImageHealthVO> {
  return get<ImageHealthVO>(`${PREFIX}/health`)
}

export async function fetchImageHistory(limit = 20): Promise<ImageHistoryVO[]> {
  return get<ImageHistoryVO[]>(`${PREFIX}/history`, { limit })
}

export async function createImageSession(file: File): Promise<ImageSessionVO> {
  const form = new FormData()
  form.append('file', file)
  return postFormData<ImageSessionVO>(`${PREFIX}/sessions`, form, 120000)
}

export async function fetchImageSession(id: string): Promise<ImageSessionVO> {
  return get<ImageSessionVO>(`${PREFIX}/sessions/${id}`)
}

export async function fetchImageMetadata(id: string): Promise<ImageMetadataVO> {
  return get<ImageMetadataVO>(`${PREFIX}/sessions/${id}/metadata`)
}

export async function convertImage(id: string, body: ImageConvertRequest): Promise<ImageConvertVO> {
  return post<ImageConvertVO>(`${PREFIX}/sessions/${id}/convert`, body, 120000)
}

export async function fetchConvertJob(sessionId: string, jobId: string): Promise<ImageConvertVO> {
  return get<ImageConvertVO>(`${PREFIX}/sessions/${sessionId}/jobs/${jobId}`)
}

export async function fetchImagePreviewBlob(id: string): Promise<Blob> {
  const { blob } = await getBlob(`${PREFIX}/sessions/${id}/preview`, 'preview.jpg')
  return blob
}

export async function downloadImageResult(id: string, defaultName: string): Promise<{ blob: Blob; filename: string }> {
  return getBlob(`${PREFIX}/sessions/${id}/result`, defaultName)
}

export async function deleteImageSession(id: string): Promise<void> {
  try {
    await del(`${PREFIX}/sessions/${id}`)
  } catch {
    // TTL will collect; ignore
  }
}
