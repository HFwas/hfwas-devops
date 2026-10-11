import type { TaskKindParam, TaskKindParamVisibility, TaskKindUpdatePayload, TaskKindVO } from '@/modules/pipeline/types/pipeline'

const GROUP_TONES = [
  'bg-primary/15 text-primary',
  'bg-accent text-accent-foreground',
  'bg-secondary text-secondary-foreground',
  'bg-muted text-foreground',
]

export function displayVersion(version?: string | null): string {
  const value = version?.trim()
  return value ? value : '—'
}

export function taskKindGroups(items: TaskKindVO[]): string[] {
  const seen = new Set<string>()
  const sorted = items.slice().sort(compareTaskKinds)
  for (const item of sorted) {
    if (item.taskGroup) seen.add(item.taskGroup)
  }
  return [...seen]
}

export function filterTaskKinds(items: TaskKindVO[], keyword: string, group: string): TaskKindVO[] {
  const needle = keyword.trim().toLowerCase()
  return items
    .filter((item) => group === '全部' || item.taskGroup === group)
    .filter((item) => {
      if (!needle) return true
      return [item.label, item.kindValue, item.taskGroup, item.description, item.hint].some((value) =>
        (value || '').toLowerCase().includes(needle),
      )
    })
    .slice()
    .sort(compareTaskKinds)
}

export function groupTone(group: string): string {
  let hash = 0
  for (const char of group) hash = (hash + char.charCodeAt(0)) % GROUP_TONES.length
  return GROUP_TONES[hash] ?? GROUP_TONES[0]
}

export function paramTypeLabel(type?: string | null): string {
  if (type === 'select') return '枚举'
  if (type === 'api_select') return '远程接口'
  return '静态值'
}

export function visibilityLabel(visibility?: string | null): string {
  return visibility === 'hidden' ? '仅管理员' : '对用户开放'
}

export function normalizeVisibility(visibility?: string | null): TaskKindParamVisibility {
  return visibility === 'hidden' ? 'hidden' : 'user'
}

export interface TaskKindOverviewInput {
  label: string
  taskGroup: string
  description: string
  hint: string
  toolImage: string
  commandTemplate: string
  sortOrder: number
  cpuRequest: string
  cpuLimit: string
  memoryRequest: string
  memoryLimit: string
}

export function overviewFromKind(item: TaskKindVO): TaskKindOverviewInput {
  return {
    label: item.label ?? '',
    taskGroup: item.taskGroup ?? '',
    description: item.description ?? '',
    hint: item.hint ?? '',
    toolImage: item.toolImage ?? '',
    commandTemplate: item.commandTemplate ?? '',
    sortOrder: item.sortOrder ?? 0,
    cpuRequest: item.cpuRequest ?? '',
    cpuLimit: item.cpuLimit ?? '',
    memoryRequest: item.memoryRequest ?? '',
    memoryLimit: item.memoryLimit ?? '',
  }
}

/** 保存只写工作副本。概览保存不带 params，避免无意义地重写参数行。 */
export function taskKindUpdatePayload(
  item: TaskKindVO,
  overrides: { overview?: TaskKindOverviewInput; params?: TaskKindParam[] },
): TaskKindUpdatePayload {
  const overview = overrides.overview
  const payload: TaskKindUpdatePayload = {
    label: overview?.label ?? item.label,
    taskGroup: overview?.taskGroup ?? item.taskGroup,
    description: overview?.description ?? item.description ?? '',
    hint: overview?.hint ?? item.hint ?? '',
    defaultCommand: item.defaultCommand ?? '',
    toolImage: overview?.toolImage ?? item.toolImage ?? '',
    commandTemplate: overview?.commandTemplate ?? item.commandTemplate ?? '',
    sortOrder: overview?.sortOrder ?? item.sortOrder ?? 0,
    cpuRequest: overview?.cpuRequest ?? item.cpuRequest ?? '',
    cpuLimit: overview?.cpuLimit ?? item.cpuLimit ?? '',
    memoryRequest: overview?.memoryRequest ?? item.memoryRequest ?? '',
    memoryLimit: overview?.memoryLimit ?? item.memoryLimit ?? '',
  }
  if (overrides.params) payload.params = overrides.params
  return payload
}

function compareTaskKinds(a: TaskKindVO, b: TaskKindVO): number {
  return a.sortOrder - b.sortOrder || a.label.localeCompare(b.label, 'zh')
}
