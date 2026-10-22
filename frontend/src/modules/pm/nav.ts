import type { LucideIcon } from 'lucide-react'
import { BookOpen, Bug, CircleDot, FlaskConical, IterationCw, Layers, ListTodo } from 'lucide-react'

/** 全部工作项哨兵：路由使用 all，查询时必须省略 typeCode。 */
export const ALL_ITEMS_CODE = 'all'

export function isAllItemsType(typeCode?: string) {
  return typeCode === ALL_ITEMS_CODE
}

export interface ProjectRouteContext {
  projectId: string
  typeCode?: string
}

/** `/pm/projects/:projectId/...` 下的项目上下文；项目列表页返回 null。 */
export function projectContextFromPath(pathname: string): ProjectRouteContext | null {
  const segments = pathname.split('/').filter(Boolean)
  if (segments[0] !== 'pm' || segments[1] !== 'projects') return null
  const projectId = segments[2]
  if (!projectId || !/^\d+$/.test(projectId)) return null

  const rest = segments.slice(3)
  let typeCode: string | undefined
  if (rest[0] === 'items' || rest[0] === 'board') {
    typeCode = rest[1]
  } else if (rest[0] === 'settings' && rest[1] === 'workflow') {
    typeCode = rest[2]
  }
  return { projectId, typeCode }
}

export function projectItemsPath(projectId: string, typeCode: string) {
  return `/pm/projects/${projectId}/items/${typeCode}`
}

export function projectBoardPath(projectId: string, typeCode: string) {
  return `/pm/projects/${projectId}/board/${typeCode}`
}

export function projectWorkflowPath(projectId: string, typeCode: string) {
  return `/pm/projects/${projectId}/settings/workflow/${typeCode}`
}

export function isProjectItemActive(pathname: string, projectId: string, typeCode: string) {
  return pathname === projectItemsPath(projectId, typeCode) || pathname.startsWith(`${projectItemsPath(projectId, typeCode)}/`)
}

/** 事项类型图标；未识别的自定义类型用通用圆点。 */
export function projectTypeIcon(code: string): LucideIcon {
  switch (code) {
    case 'requirement':
      return BookOpen
    case 'task':
      return ListTodo
    case 'bug':
      return Bug
    case 'test_case':
      return FlaskConical
    case 'iteration':
      return IterationCw
    case 'all':
      return Layers
    default:
      return CircleDot
  }
}
