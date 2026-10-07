import type { LucideIcon } from 'lucide-react'
import {
  Activity,
  Clock,
  FileCode,
  FileDown,
  FileText,
  FolderKanban,
  GitBranch,
  Image,
  KeyRound,
  Layers,
  LayoutDashboard,
  Package,
  Settings,
  Users,
  Variable,
  Workflow,
} from 'lucide-react'
import { resolveActiveProductKey } from '@/shared/console/products'

export interface MenuMatch {
  prefix: string
  end?: boolean
}

export interface ProductMenuItem {
  key: string
  label: string
  icon: LucideIcon
  to: string
  matches: MenuMatch[]
  adminOnly?: boolean
}

export interface ProductMenuGroup {
  key: string
  label?: string
  items: ProductMenuItem[]
}

/** 侧栏只服务当前范围：工作台、用户中心，或某一个产品。 */
export type ShellScope = 'workbench' | 'user' | string

const MENUS: Record<string, ProductMenuGroup[]> = {
  workbench: [
    {
      key: 'home',
      items: [
        {
          key: 'workbench',
          label: '工作台',
          icon: LayoutDashboard,
          to: '/workbench',
          matches: [{ prefix: '/workbench', end: true }],
        },
      ],
    },
  ],
  user: [
    {
      key: 'user',
      items: [
        {
          key: 'accounts',
          label: '用户管理',
          icon: Users,
          to: '/user/accounts',
          matches: [{ prefix: '/user/accounts' }],
          adminOnly: true,
        },
        {
          key: 'settings',
          label: '账号设置',
          icon: Settings,
          to: '/user/settings',
          matches: [{ prefix: '/user/settings', end: true }],
        },
      ],
    },
  ],
  pm: [
    {
      key: 'pm',
      items: [
        {
          key: 'projects',
          label: '项目',
          icon: FolderKanban,
          to: '/pm/projects',
          matches: [{ prefix: '/pm/projects' }],
        },
        {
          key: 'monitor',
          label: '项目监控',
          icon: Activity,
          to: '/pm/monitor',
          matches: [{ prefix: '/pm/monitor' }],
        },
      ],
    },
  ],
  pipeline: [
    {
      key: 'pipeline',
      items: [
        {
          key: 'pipelines',
          label: '流水线',
          icon: GitBranch,
          to: '/pipeline/pipelines',
          matches: [{ prefix: '/pipeline/pipelines' }],
        },
        {
          key: 'task-kinds',
          label: '任务市场',
          icon: Package,
          to: '/pipeline/task-kinds',
          matches: [{ prefix: '/pipeline/task-kinds' }],
        },
        {
          key: 'credentials',
          label: '凭证',
          icon: KeyRound,
          to: '/pipeline/credentials',
          matches: [{ prefix: '/pipeline/credentials' }],
        },
      ],
    },
  ],
  'api-test': [
    {
      key: 'api-test',
      items: [
        {
          key: 'definitions',
          label: '接口',
          icon: FileCode,
          to: '/api-test',
          matches: [
            { prefix: '/api-test', end: true },
            { prefix: '/api-test/definitions' },
          ],
        },
        {
          key: 'collections',
          label: '集合',
          icon: Layers,
          to: '/api-test/collections',
          matches: [{ prefix: '/api-test/collections' }],
        },
        {
          key: 'environments',
          label: '环境',
          icon: Variable,
          to: '/api-test/environments',
          matches: [{ prefix: '/api-test/environments' }],
        },
      ],
    },
  ],
  'file-parser': [
    {
      key: 'file-parser',
      items: [
        {
          key: 'parse',
          label: '文件解析',
          icon: FileText,
          to: '/file-parser',
          matches: [{ prefix: '/file-parser' }],
        },
      ],
    },
  ],
  docgen: [
    {
      key: 'docgen',
      items: [
        {
          key: 'docgen',
          label: '文档生成',
          icon: FileDown,
          to: '/docgen',
          matches: [{ prefix: '/docgen' }],
        },
      ],
    },
  ],
  image: [
    {
      key: 'image',
      items: [
        {
          key: 'image',
          label: '图片处理',
          icon: Image,
          to: '/image',
          matches: [{ prefix: '/image' }],
        },
      ],
    },
  ],
  argo: [
    {
      key: 'argo',
      items: [
        {
          key: 'workflows',
          label: '工作流',
          icon: Workflow,
          to: '/argo/workflows',
          matches: [{ prefix: '/argo/workflows' }, { prefix: '/argo', end: true }],
        },
        {
          key: 'templates',
          label: '模板',
          icon: Layers,
          to: '/argo/templates',
          matches: [{ prefix: '/argo/templates' }],
        },
        {
          key: 'cron',
          label: '定时任务',
          icon: Clock,
          to: '/argo/cron',
          matches: [{ prefix: '/argo/cron' }],
        },
      ],
    },
  ],
}

export function resolveShellScope(pathname: string): ShellScope {
  if (pathname === '/' || pathname === '/workbench') return 'workbench'
  if (pathname === '/user' || pathname.startsWith('/user/')) return 'user'
  return resolveActiveProductKey(pathname) ?? 'workbench'
}

export function menuForScope(scope: ShellScope, options: { isAdmin: boolean }): ProductMenuGroup[] {
  const groups = MENUS[scope] ?? MENUS.workbench
  return groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.adminOnly || options.isAdmin),
    }))
    .filter((group) => group.items.length > 0)
}

export function isMenuItemActive(pathname: string, item: ProductMenuItem): boolean {
  return item.matches.some((match) =>
    match.end ? pathname === match.prefix : pathname === match.prefix || pathname.startsWith(`${match.prefix}/`),
  )
}
