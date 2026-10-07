import { describe, expect, it } from 'vitest'
import { CONTAINER_NAV_GROUPS, containerNavHref, isContainerNavActive } from '@/modules/container/nav'
import { isMenuItemActive, menuForScope, resolveShellScope } from '@/shared/console/navigation'
import { resolveBreadcrumbs } from '@/components/console/breadcrumbs'

describe('resolveShellScope', () => {
  it('keeps workbench, user center, and each product separate', () => {
    expect(resolveShellScope('/workbench')).toBe('workbench')
    expect(resolveShellScope('/user/accounts')).toBe('user')
    expect(resolveShellScope('/pm/projects/12/items/task')).toBe('pm')
    expect(resolveShellScope('/pipeline/credentials')).toBe('pipeline')
    expect(resolveShellScope('/container/clusters/c1/pods')).toBe('container')
    expect(resolveShellScope('/api-test/collections')).toBe('api-test')
  })
})

describe('menuForScope', () => {
  it('shows only the current product menu', () => {
    expect(menuForScope('pm', { isAdmin: true }).flatMap((group) => group.items.map((item) => item.label))).toEqual([
      '项目',
      '项目监控',
    ])
    expect(menuForScope('pipeline', { isAdmin: true }).flatMap((group) => group.items.map((item) => item.label))).toEqual([
      '流水线',
      '任务市场',
      '凭证',
    ])
    expect(menuForScope('api-test', { isAdmin: false }).flatMap((group) => group.items.map((item) => item.label))).toEqual([
      '接口',
      '集合',
      '环境',
    ])
    expect(menuForScope('workbench', { isAdmin: true }).flatMap((group) => group.items.map((item) => item.label))).toEqual([
      '工作台',
    ])
  })

  it('hides user management from non-admins', () => {
    expect(menuForScope('user', { isAdmin: false }).flatMap((group) => group.items.map((item) => item.key))).toEqual([
      'settings',
    ])
    expect(menuForScope('user', { isAdmin: true }).flatMap((group) => group.items.map((item) => item.key))).toEqual([
      'accounts',
      'settings',
    ])
  })
})

describe('isMenuItemActive', () => {
  const item = (scope: string, key: string) =>
    menuForScope(scope, { isAdmin: true })
      .flatMap((group) => group.items)
      .find((entry) => entry.key === key)!

  it('highlights the section inside the product', () => {
    expect(isMenuItemActive('/pipeline/credentials', item('pipeline', 'credentials'))).toBe(true)
    expect(isMenuItemActive('/pipeline/credentials', item('pipeline', 'pipelines'))).toBe(false)
    expect(isMenuItemActive('/pm/projects/12/items/task', item('pm', 'projects'))).toBe(true)
    expect(isMenuItemActive('/pm/projects/12/items/task', item('pm', 'monitor'))).toBe(false)
    expect(isMenuItemActive('/api-test', item('api-test', 'definitions'))).toBe(true)
    expect(isMenuItemActive('/api-test/collections/1', item('api-test', 'definitions'))).toBe(false)
    expect(isMenuItemActive('/api-test/collections/1', item('api-test', 'collections'))).toBe(true)
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
    expect(resolveBreadcrumbs('/container/clusters/c1')).toEqual([
      { label: '容器管理', to: '/container/clusters' },
      { label: '概览' },
    ])
    expect(resolveBreadcrumbs('/container/clusters/c1/daemonsets')).toEqual([
      { label: '容器管理', to: '/container/clusters' },
      { label: 'DaemonSet' },
    ])
    expect(resolveBreadcrumbs('/container/helm/charts')).toEqual([
      { label: '容器管理', to: '/container/clusters' },
      { label: 'Helm Charts' },
    ])
  })
})

describe('container navigation groups', () => {
  it('follows kite group order and keeps platform items', () => {
    expect(CONTAINER_NAV_GROUPS.map((group) => group.label)).toEqual([
      '概览',
      '应用',
      '工作负载',
      '网络',
      '存储',
      '配置',
      '安全',
      '集群',
      '平台',
    ])
    expect(CONTAINER_NAV_GROUPS.find((group) => group.key === 'workloads')?.items.map((item) => item.label)).toEqual([
      'Pod',
      'Deployment',
      'StatefulSet',
      'DaemonSet',
      'Job',
      'CronJob',
    ])
    expect(containerNavHref({ key: 'overview', label: '概览', icon: () => null, needsCluster: false }, 'c1')).toBe(
      '/container/clusters/c1',
    )
    expect(containerNavHref({ key: 'overview', label: '概览', icon: () => null, needsCluster: false }, null)).toBe(
      '/container/clusters',
    )
  })
})

describe('isContainerNavActive', () => {
  it('highlights the resource segment without highlighting 集群管理', () => {
    expect(isContainerNavActive('/container/clusters/c1/nodes/node-a', { key: 'nodes', label: 'Node', icon: () => null, suffix: '/nodes', needsCluster: true })).toBe(true)
    expect(isContainerNavActive('/container/clusters/c1/nodes/node-a', { key: 'clusters', label: '集群管理', icon: () => null, to: '/container/clusters', needsCluster: false })).toBe(false)
    expect(isContainerNavActive('/container/clusters/c1', { key: 'overview', label: '概览', icon: () => null, needsCluster: false })).toBe(true)
    expect(isContainerNavActive('/container/clusters/c1', { key: 'clusters', label: '集群管理', icon: () => null, to: '/container/clusters', needsCluster: false })).toBe(false)
    expect(isContainerNavActive('/container/clusters', { key: 'clusters', label: '集群管理', icon: () => null, to: '/container/clusters', needsCluster: false })).toBe(true)
    expect(isContainerNavActive('/container/clusters/c1/cronjobs', { key: 'jobs', label: 'Job', icon: () => null, suffix: '/jobs', needsCluster: true })).toBe(false)
    expect(isContainerNavActive('/container/helm/charts', { key: 'helm-releases', label: 'Helm Releases', icon: () => null, suffix: '/helm', needsCluster: true })).toBe(false)
    expect(isContainerNavActive('/container/images', { key: 'images', label: '镜像', icon: () => null, to: '/container/images', needsCluster: false })).toBe(true)
  })
})
