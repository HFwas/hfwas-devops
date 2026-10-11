import type { RunParamValue } from '@/modules/pipeline/types/pipeline'

export function initialRunParamValues(defs: RunParamValue[]): Record<string, string> {
  const values: Record<string, string> = {}
  for (const def of defs) values[def.paramKey] = def.defaultValue ?? ''
  return values
}

export function missingRunParam(defs: RunParamValue[], values: Record<string, string>): string | null {
  for (const def of defs) {
    if (!def.required) continue
    if ((values[def.paramKey] ?? '').trim()) continue
    return `请填写${def.paramLabel || def.paramKey}`
  }
  return null
}
