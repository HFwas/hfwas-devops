import { describe, expect, it } from 'vitest'
import { resolveBreadcrumbs } from '@/components/console/breadcrumbs'
import { isContainerNavActive } from '@/modules/container/nav'
import { buildNavGroups, isNavItemActive } from '@/shared/console/navigation'

describe('buildNavGroups', () => {
  it('keeps workbench, user center, and product modules in one model', () => {
    const labels = buildNavGroups({ isAdmin: true }).map((group) => group.label)
    expect(labels).toEqual(['平台', '用户中心', '研发协同', '质量保障', '效率工具', '基础设施', '安全治理'])

    const items = buildNavGroups({ isAdmin: true }).flatMap((group) => group.items.map((item) => item.label))
    expect(items).toEqual(
      expect.arrayContaining(['工作台', '用户管理', '账号设置', '项目管理', '流水线', '容器管理', '接口测试']),
    )
  })

  it('hides user management from non-admins', () => {
    const user = buildNavGroups({ isAdmin: false }).find((group) => group.key === 'user')
    expect(user?.items.map((item) => item.key)).toEqual(['user-settings'])
  })
})

describe('isNavItemActive', () => {
  const groups = buildNavGroups({ isAdmin: true })
  const item = (key: string) => groups.flatMap((group) => group.items).find((entry) => entry.key === key)!

  it('activates a product across its child routes', () => {
    expect(isNavItemActive('/pipeline/credentials', item('pipeline'))).toBe(true)
    expect(isNavItemActive('/pm/projects/12/items/task', item('pm'))).toBe(true)
    expect(isNavItemActive('/api-test', item('api-test'))).toBe(true)
    expect(isNavItemActive('/container/clusters/c1/pods', item('container'))).toBe(true)
  })

  it('does not mark coming-soon items active', () => {
    expect(isNavItemActive('/artifact/overview', item('artifact'))).toBe(false)
  })

  it('matches workbench only on its own path', () => {
    expect(isNavItemActive('/workbench', item('workbench'))).toBe(true)
    expect(isNavItemActive('/workbench/extra', item('workbench'))).toBe(false)
  })
})

describe('resolveBreadcrumbs', () => {
  it('follows the current route', () => {
    expect(resolveBreadcrumbs('/workbench')).toEqual([{ label: '工作台' }])
    expect(resolveBreadcrumbs('/pm/projects')).toEqual([{ label: '项目管理' }])
    expect(resolveBreadcrumbs('/pipeline/credentials')).toEqual([
      { label: '流水线', to: '/pipeline/pipelines' },
      { label: '凭证' },
    ])
    expect(resolveBreadcrumbs('/user/settings')).toEqual([
      { label: '用户中心', to: '/user/settings' },
      { label: '账号设置' },
    ])
    expect(resolveBreadcrumbs('/container/clusters/c1/pods/ns/api')).toEqual([
      { label: '容器管理', to: '/container/clusters' },
      { label: 'Pod', to: '/container/clusters/c1/pods' },
      { label: 'api' },
    ])
  })
})

describe('isContainerNavActive', () => {
  it('highlights the resource segment without highlighting 集群', () => {
    expect(isContainerNavActive('/container/clusters/c1/nodes/node-a', { key: 'nodes', label: '节点', icon: () => null, suffix: '/nodes', needsCluster: true })).toBe(true)
    expect(isContainerNavActive('/container/clusters/c1/nodes/node-a', { key: 'clusters', label: '集群', icon: () => null, to: '/container/clusters', needsCluster: false })).toBe(false)
    expect(isContainerNavActive('/container/clusters/c1', { key: 'clusters', label: '集群', icon: () => null, to: '/container/clusters', needsCluster: false })).toBe(true)
    expect(isContainerNavActive('/container/images', { key: 'images', label: '镜像', icon: () => null, to: '/container/images', needsCluster: false })).toBe(true)
  })
})
