export function runStatusLabel(status?: string | null): string {
  if (!status) return '未运行'
  const labels: Record<string, string> = {
    QUEUED: '排队中',
    RUNNING: '运行中',
    WAITING_APPROVAL: '待审批',
    SUCCEEDED: '成功',
    FAILED: '失败',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}

export function runStatusTagType(status?: string | null): 'success' | 'error' | 'info' | 'warning' | 'default' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'RUNNING') return 'info'
  if (status === 'WAITING_APPROVAL' || status === 'CANCELLED' || status === 'QUEUED') return 'warning'
  return 'default'
}

export function stackTone(stack?: string | null): string {
  if (stack === 'JAVA_MAVEN') return 'blue'
  if (stack === 'NODE') return 'green'
  if (stack === 'GO') return 'cyan'
  if (stack === 'PYTHON') return 'amber'
  return 'violet'
}

export function jobKindTone(kind?: string | null): string {
  if (kind === 'CLONE') return 'cyan'
  if (kind === 'BUILD' || kind === 'PACKAGE') return 'blue'
  if (kind === 'TEST') return 'green'
  if (kind === 'LINT' || kind === 'SCAN') return 'amber'
  if (kind === 'APPROVAL' || kind === 'NOTIFY') return 'violet'
  if (kind === 'IMAGE' || kind === 'DEPLOY' || kind === 'PUBLISH' || kind === 'UPLOAD') return 'rose'
  return 'blue'
}

export function parseInstant(value: string): number {
  const raw = value.trim().replace(' ', 'T')
  if (/[zZ]$|[+-]\d{2}:?\d{2}$/.test(raw)) {
    return Date.parse(raw)
  }
  return Date.parse(`${raw}Z`)
}

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const ms = parseInstant(String(value))
  if (Number.isNaN(ms)) return String(value).replace('T', ' ').slice(0, 19)
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

export function formatGitRef(ref?: string | null): string {
  if (!ref) return '—'
  return ref.replace(/^refs\/heads\//, '').replace(/^refs\/tags\//, '')
}

export function formatCommit(sha?: string | null): string {
  if (!sha) return '—'
  return sha.slice(0, 8)
}

export function jobRunDuration(
  job: { status?: string | null; startedAt?: string | null; finishedAt?: string | null },
  now = Date.now(),
): string {
  if (!job.status || job.status === 'QUEUED' || job.status === 'WAITING_APPROVAL') return ''
  if (!job.startedAt) return ''
  return formatDuration(job.startedAt, job.finishedAt, now)
}

export function formatDuration(startedAt?: string | null, finishedAt?: string | null, now = Date.now()): string {
  if (!startedAt) return '—'
  const start = parseInstant(startedAt)
  if (Number.isNaN(start)) return '—'
  const end = finishedAt ? parseInstant(finishedAt) : now
  if (Number.isNaN(end) || end < start) return '—'
  const total = Math.floor((end - start) / 1000)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  if (hours > 0) return `${hours}时${minutes}分${seconds}秒`
  if (minutes > 0) return `${minutes}分${seconds}秒`
  return `${seconds}秒`
}
