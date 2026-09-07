import type {
  ApiDebugAssertionDTO,
  ApiDebugExtractDTO,
  ApiDebugResultVO,
} from '@/modules/api-test/debug/types/debug'
import { emptyAuth, type AuthConfig } from '@/modules/api-test/debug/utils/auth'
import type { BodyMode } from '@/modules/api-test/debug/utils/bodyMode'
import { emptyPair, type KeyValuePair } from '@/modules/api-test/shared/types/keyValue'
import { recordToPairs } from '@/modules/api-test/shared/utils/keyValue'

export type ShellModule = 'apis' | 'collections' | 'environments' | 'docs' | 'specs' | 'mocks'

export type TabSource = 'definition' | 'collection' | 'collectionOverview' | 'scratch'

export interface RequestDraft {
  url: string
  method: string
  headers: KeyValuePair[]
  queryParams: KeyValuePair[]
  pathParams: KeyValuePair[]
  formFields: KeyValuePair[]
  body: string
  bodyMode: BodyMode
  contentType: string
  description: string
  preRequestScript: string
  postResponseScript: string
  assertions: ApiDebugAssertionDTO[]
  extracts: ApiDebugExtractDTO[]
  auth: AuthConfig
  timeoutMs: number
  followRedirects: boolean
}

export interface RequestTab {
  id: string
  source: TabSource
  refId?: number
  definitionId?: number
  collectionId?: number
  folderId?: number | null
  title: string
  method: string
  dirty: boolean
  draft: RequestDraft
  result: ApiDebugResultVO | null
  loadError?: string
}

export function emptyDraft(partial?: Partial<RequestDraft>): RequestDraft {
  return {
    url: '',
    method: 'GET',
    headers: [emptyPair()],
    queryParams: [emptyPair()],
    pathParams: [emptyPair()],
    formFields: [emptyPair()],
    body: '',
    bodyMode: 'none',
    contentType: 'application/json',
    description: '',
    preRequestScript: '',
    postResponseScript: '',
    assertions: [],
    extracts: [],
    auth: emptyAuth(),
    timeoutMs: 30000,
    followRedirects: true,
    ...partial,
  }
}

export function pairsFromRecord(record?: Record<string, string> | null): KeyValuePair[] {
  return recordToPairs(record ?? {})
}

export interface OpenTabInput {
  source: TabSource
  refId?: number
  definitionId?: number
  collectionId?: number
  folderId?: number | null
  title: string
  method: string
  draft: RequestDraft
  loadError?: string
}
