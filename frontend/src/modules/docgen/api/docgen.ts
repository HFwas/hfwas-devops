import request from '@/shared/api/request'
import type { DocgenRequest, DocgenFormat } from '@/modules/docgen/types/docgen'

/**
 * 生成并下载文档
 *
 * 注意：
 * - baseURL 是 /api，proxy 配置会剥离 /api 前缀再转发给后端
 * - 所以路径写 /api/docgen/generate，最终发给后端的是 /api/docgen/generate
 * - 使用 responseType: 'blob' 以绕过拦截器的 JSON 解析
 */
export async function generateDocument(req: DocgenRequest): Promise<Blob> {
  const res = await request.post('/api/docgen/generate', req, {
    responseType: 'blob',
    timeout: 120000,
  })
  return res.data as Blob
}

/**
 * 生成文档到指定目录（不下载）
 */
export async function generateToDir(req: DocgenRequest & { directory: string }): Promise<{ success: boolean; directory: string; filename: string; message: string }> {
  const res = await request.post('/api/docgen/generate-to-dir', req, {
    timeout: 120000,
  })
  return res.data
}

/**
 * 批量生成文档（多格式 × 多文件大小）
 * 生成到指定目录或临时目录
 */
export async function batchGenerate(req: {
  formats: DocgenFormat[]
  filename: string
  sizes: number[]
  fileCount: number
  directory?: string
}): Promise<{
  success: boolean
  directory: string
  files: Array<{ filename: string; size: number }>
  total: number
  message: string
}> {
  const res = await request.post('/api/docgen/batch-generate', req, {
    timeout: 600000, // 10 分钟超时
  })
  return res.data
}

/**
 * 下载文件
 */
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}