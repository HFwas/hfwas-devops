import {
  Bell,
  Box,
  FlaskConical,
  GitBranch,
  Hammer,
  Rocket,
  Shield,
  SquareTerminal,
  UserCheck,
  type LucideIcon,
} from 'lucide-react'

const KIND_ICONS: Record<string, LucideIcon> = {
  CLONE: GitBranch,
  BUILD: Hammer,
  IMAGE: Box,
  SCAN: Shield,
  DEPLOY: Rocket,
  TEST: FlaskConical,
  APPROVAL: UserCheck,
  NOTIFY: Bell,
}

/** 与泳道编辑器相同的任务图标。未单独配图标的类型用终端图标。 */
export function JobKindIcon({ kind, className }: { kind: string; className?: string }) {
  const Icon = KIND_ICONS[kind] ?? SquareTerminal
  return <Icon className={className} aria-hidden />
}
