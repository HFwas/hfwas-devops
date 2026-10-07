import { containerBreadcrumbs, type PathCrumb } from '@/modules/container/nav'
import { resolveActiveProduct } from '@/shared/console/products'

export type { PathCrumb }

const SECTION_LABELS: Record<string, string> = {
  projects: '项目',
  monitor: '项目监控',
  pipelines: '流水线',
  credentials: '凭证',
  'task-kinds': '任务市场',
  workflows: '工作流',
  templates: '模板',
  cron: '定时任务',
  collections: '集合',
  environments: '环境',
  definitions: '接口',
  overview: '概览',
  settings: '账号设置',
  accounts: '用户管理',
}

export function resolveBreadcrumbs(pathname: string): PathCrumb[] {
  if (pathname === '/' || pathname === '/workbench') return [{ label: '工作台' }]

  if (pathname === '/user/settings' || pathname.startsWith('/user/settings/')) {
    return [{ label: '用户中心', to: '/user/settings' }, { label: '账号设置' }]
  }
  if (pathname === '/user/accounts' || pathname.startsWith('/user/accounts/')) {
    return [{ label: '用户中心', to: '/user/accounts' }, { label: '用户管理' }]
  }
  if (pathname === '/user' || pathname.startsWith('/user/')) {
    return [{ label: '用户中心' }]
  }

  if (pathname === '/container' || pathname.startsWith('/container/')) {
    return containerBreadcrumbs(pathname)
  }

  const product = resolveActiveProduct(pathname)
  if (!product) return [{ label: '页面' }]
  if (pathname === product.path || pathname === `/${product.key}`) return [{ label: product.name }]

  const parts = pathname.split('/').filter(Boolean)
  const last = parts[parts.length - 1] ?? ''
  const leaf = SECTION_LABELS[last] ?? (/^\d+$/.test(last) ? '详情' : decodeURIComponent(last) || '详情')
  return [{ label: product.name, to: product.path }, { label: leaf }]
}
