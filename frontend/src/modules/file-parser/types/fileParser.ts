/** 文件解析结果 */
export interface FileParseResult {
  success: boolean
  errorMessage?: string
  fileName: string
  fileSize: number
  mimeType: string
  parseMethod: 'tika' | 'ocr' | 'plain'
  parseTimeMs: number
  content?: FileContent
  warnings?: string[]
  ocrInfo?: OcrInfo
}

/** 文件内容 */
export interface FileContent {
  text: string
  pages?: PageContent[]
  tables?: TableContent[]
  slides?: SlideContent[]
  metadata?: Record<string, string>
}

/** 分页内容（PDF 专用） */
export interface PageContent {
  pageNum: number
  text: string
}

/** 表格数据（Excel 专用） */
export interface TableContent {
  sheetName: string
  rows: string[][]
}

/** 幻灯片内容（PPTX 专用） */
export interface SlideContent {
  slideNum: number
  text: string
}

/** OCR 信息 */
export interface OcrInfo {
  engine: string
  pagesProcessed: number
  confidence: number
}

/** 解析历史记录 */
export interface ParseHistory {
  id: string
  fileName: string
  fileSize: number
  mimeType: string
  parseMethod: string
  success: boolean
  createdAt: string
}

/** 文件上传状态 */
export type UploadStatus = 'idle' | 'uploading' | 'parsing' | 'success' | 'error'

/** 文件上传项 */
export interface UploadItem {
  id: string
  file: File
  status: UploadStatus
  progress: number
  result?: FileParseResult
  error?: string
}