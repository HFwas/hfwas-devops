import { isValidElement, type ReactElement } from 'react'
import { matchRoutes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { router } from '@/router'

function leafName(pathname: string) {
  const matches = matchRoutes(router.routes, pathname)
  const leaf = matches?.at(-1)
  const element = leaf?.route.element
  const name =
    isValidElement(element) && typeof (element as ReactElement).type === 'function'
      ? ((element as ReactElement).type as { name?: string }).name
      : undefined
  return { path: leaf?.route.path, name }
}

describe('pm routes', () => {
  it('resolves project pages to the real pages', () => {
    expect(leafName('/pm/projects')).toEqual({ path: 'pm/projects', name: 'ProjectListPage' })
    expect(leafName('/pm/projects/12/items/task')).toEqual({
      path: 'pm/projects/:projectId/items/:typeCode',
      name: 'WorkItemListPage',
    })
    expect(leafName('/pm/projects/12/items/all')).toEqual({
      path: 'pm/projects/:projectId/items/:typeCode',
      name: 'WorkItemListPage',
    })
    expect(leafName('/pm/projects/12/board/task')).toEqual({
      path: 'pm/projects/:projectId/board/:typeCode',
      name: 'BoardPage',
    })
    expect(leafName('/pm/projects/12/settings/workflow/task')).toEqual({
      path: 'pm/projects/:projectId/settings/workflow/:typeCode',
      name: 'WorkflowPage',
    })
  })

  it('no longer exposes the project monitor route', () => {
    expect(leafName('/pm/monitor')).toEqual({ path: '*', name: 'RouteScreen' })
  })
})
