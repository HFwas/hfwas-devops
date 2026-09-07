import type { KeyValuePair } from '@/modules/api-test/shared/types/keyValue'

export type AuthType = 'none' | 'bearer' | 'basic' | 'apikey'

export interface AuthConfig {
  type: AuthType
  token: string
  username: string
  password: string
  apiKey: string
  apiValue: string
  addTo: 'header' | 'query'
}

export function emptyAuth(): AuthConfig {
  return {
    type: 'none',
    token: '',
    username: '',
    password: '',
    apiKey: '',
    apiValue: '',
    addTo: 'header',
  }
}

export function applyAuth(
  auth: AuthConfig,
  headers: Record<string, string>,
  query: KeyValuePair[],
): { headers: Record<string, string>; query: KeyValuePair[] } {
  const nextHeaders = { ...headers }
  let nextQuery = [...query]
  if (auth.type === 'bearer' && auth.token) {
    nextHeaders.Authorization = `Bearer ${auth.token}`
  } else if (auth.type === 'basic' && (auth.username || auth.password)) {
    nextHeaders.Authorization = `Basic ${btoa(`${auth.username}:${auth.password}`)}`
  } else if (auth.type === 'apikey' && auth.apiKey) {
    if (auth.addTo === 'query') {
      nextQuery = [...nextQuery, { enabled: true, key: auth.apiKey, value: auth.apiValue }]
    } else {
      nextHeaders[auth.apiKey] = auth.apiValue
    }
  }
  return { headers: nextHeaders, query: nextQuery }
}
