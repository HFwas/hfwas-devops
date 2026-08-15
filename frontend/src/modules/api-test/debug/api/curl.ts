import { post } from '@/shared/api/request'
import type { CurlParseResultVO } from '@/modules/api-test/debug/types/curl'

export const curlApi = {
  /** 解析 cURL 命令 */
  parse: (curl: string) => post<CurlParseResultVO>('/apitest/curl/parse', { curl }),
}