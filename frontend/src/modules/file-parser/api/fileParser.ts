import request from '@/shared/api/request'
import type { FileParseResult } from '@/modules/file-parser/types/fileParser'

/**
 * 上传并解析文件
 * 使用自定义请求，因为文件解析返回的是直接数据而非 BaseResult 包装
 */
export async function uploadFile(file: File): Promise<FileParseResult> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await request.post<FileParseResult>('/api/file-parser/upload', formData, {
    timeout: 120000,
  })
  return res.data
}