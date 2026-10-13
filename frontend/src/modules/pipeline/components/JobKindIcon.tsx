import {
  Bell,
  Boxes,
  Bug,
  CloudUpload,
  Container,
  FlaskConical,
  GitBranch,
  Hammer,
  Network,
  Package,
  Paintbrush,
  Rocket,
  SearchCode,
  Shield,
  ShieldAlert,
  SquareTerminal,
  Upload,
  UserCheck,
  type LucideIcon,
} from 'lucide-react'

/**
 * 任务类型 → 图标：画布、添加面板、任务市场卡片共用同一张表，避免同一类型在不同界面图标不一致。
 * 未配图标的类型回落到 Boxes。
 */
export const JOB_KIND_ICONS: Record<string, LucideIcon> = {
  CLONE: GitBranch,
  BUILD: Hammer,
  IMAGE: Container,
  LINT_SEMGREP: Shield,
  LINT_SONAR: SearchCode,
  FORMAT: Paintbrush,
  DEPENDENCY_ANALYSIS: Network,
  SCAN: ShieldAlert,
  PACKAGE: Package,
  PUBLISH: Upload,
  UPLOAD: CloudUpload,
  DEPLOY: Rocket,
  TEST: FlaskConical,
  CUSTOM: SquareTerminal,
  APPROVAL: UserCheck,
  NOTIFY: Bell,
  DEPENDENCY_TRACK: Bug,
  KUBECTL: SquareTerminal,
}

export function jobKindIcon(kind?: string | null): LucideIcon {
  return (kind ? JOB_KIND_ICONS[kind] : undefined) ?? Boxes
}

/** 与泳道编辑器相同的任务图标。 */
export function JobKindIcon({ kind, className }: { kind: string; className?: string }) {
  const Icon = jobKindIcon(kind)
  return <Icon className={className} aria-hidden />
}