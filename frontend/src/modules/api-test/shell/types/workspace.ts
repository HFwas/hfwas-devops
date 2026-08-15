import type {
  ApiDebugAssertionDTO,
  ApiDebugExtractDTO,
  ApiDebugResultVO,
} from '@/modules/api-test/debug/types/debug'

export type ShellModule = 'apis' | 'collections' | 'environments' | 'docs' | 'specs' | 'mocks'

export type TabSource = 'definition' | 'collection' | 'collectionOverview' | 'scratch'

export interface RequestDraft {
  url: string
  method: string
  headers: Record<string, string>
  queryParams: Record<string, string>
  body: string
  contentType: string
  description: string
  preRequestScript: string
  postResponseScript: string
  assertions: ApiDebugAssertionDTO[]
  extracts: ApiDebugExtractDTO[]
}

export interface RequestTab {
  id: string
  source: TabSource
  refId?: number
  definitionId?: number
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
    headers: {},
    queryParams: {},
    body: '',
    contentType: 'application/json',
    description: '',
    preRequestScript: '',
    postResponseScript: '',
    assertions: [],
    extracts: [],
    ...partial,
  }
}

export interface OpenTabInput {
  source: TabSource
  refId?: number
  definitionId?: number
  title: string
  method: string
  draft: RequestDraft
  loadError?: string
}
