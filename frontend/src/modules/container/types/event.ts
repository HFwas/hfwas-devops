export interface EventInfo {
  type: string
  reason: string
  message: string
  count: number | null
  firstTimestamp: string | null
  lastTimestamp: string | null
  involvedKind: string | null
  involvedName: string | null
  involvedUid: string | null
  source: string | null
}