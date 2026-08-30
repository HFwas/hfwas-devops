import request from '@/shared/api/request'
import type { DocgenFormat } from '@/modules/docgen/types/docgen'

/**
 * 生成文档到指定目录（不下载）
 */
export async function generateToDir(req: {
  format: string
  filename: string
  data: Record<string, unknown>
  directory: string
}): Promise<{ success: boolean; directory: string; filename: string; message: string }> {
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
  columnCount?: number
  rowSize?: number
  rowCount?: number
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