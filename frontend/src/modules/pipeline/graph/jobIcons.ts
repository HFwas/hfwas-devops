import type { Component } from 'vue'
import {
  Bell,
  Box,
  CloudUpload,
  FolderTree,
  GitBranch,
  Hammer,
  Package,
  Paintbrush,
  Rocket,
  Search,
  Shield,
  Terminal,
  UserRound,
  CheckCircle,
  Container,
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
  FORMAT: Paintbrush,
  DEPENDENCY_ANALYSIS: FolderTree,
  KUBECTL: Container,
}

export function jobKindIcon(kind?: string | null): Component {
  return JOB_KIND_ICONS[(kind as JobKind) || 'CUSTOM'] ?? Terminal
}
