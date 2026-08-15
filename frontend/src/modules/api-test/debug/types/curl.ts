/** cURL 解析结果 */
export interface CurlParseResultVO {
  url: string
  method: string
  headers: Record<string, string>
  body: string
  contentType: string
  followRedirects: boolean
  timeoutMs: number
  warnings: string[]
}