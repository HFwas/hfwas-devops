/** IDs may exceed JS safe integer; always keep as string in URLs and route params. */
export type EntityId = number | string

export function asId(value: EntityId | null | undefined): string {
  return value == null || value === '' ? '' : String(value)
}

export function routeId(value: unknown): string {
  const raw = Array.isArray(value) ? value[0] : value
  return raw == null || raw === '' ? '' : String(raw)
}
