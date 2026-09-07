export interface KeyValuePair {
  enabled: boolean
  key: string
  value: string
  /** form-data only; omitted elsewhere */
  type?: 'text' | 'file'
}

export function emptyPair(partial?: Partial<KeyValuePair>): KeyValuePair {
  return {
    enabled: true,
    key: '',
    value: '',
    ...partial,
  }
}
