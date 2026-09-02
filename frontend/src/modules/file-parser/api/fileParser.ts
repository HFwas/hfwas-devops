import { postFormData } from '@/shared/api/request'
import type { FileParseResult } from '@/modules/file-parser/types/fileParser'

/**
 * 上传并解析文件
 */
export async function uploadFile(file: File): Promise<FileParseResult> {
  const formData = new FormData()
  formData.append('file', file)

  return postFormData<FileParseResult>('/api/file-parser/upload', formData, 120000)
}