import type { Component } from 'vue'
import {
  Bell,
  Box,
  CloudUpload,
  GitBranch,
  Hammer,
  Package,
  Rocket,
  Search,
  Shield,
  Terminal,
  UserRound,
  CheckCircle,
} from '@lucide/vue'
import type { JobKind } from '@/modules/pipeline/types/pipeline'

export const JOB_KIND_ICONS: Record<JobKind, Component> = {
  CLONE: GitBranch,
  LINT_SEMGREP: Search,
  LINT_SONAR: Search,
  BUILD: Hammer,
  TEST: CheckCircle,
  SCAN: Shield,
  PACKAGE: Package,
  CUSTOM: Terminal,
  IMAGE: Box,
  PUBLISH: Package,
  UPLOAD: CloudUpload,
  DEPLOY: Rocket,
  APPROVAL: UserRound,
  NOTIFY: Bell,
}

export function jobKindIcon(kind?: string | null): Component {
  return JOB_KIND_ICONS[(kind as JobKind) || 'CUSTOM'] ?? Terminal
}
