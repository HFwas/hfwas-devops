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

describe('container helm routes', () => {
  it('resolves catalog, upload, and releases to the real pages', () => {
    expect(leafName('/container/helm/charts')).toEqual({ path: 'helm/charts', name: 'HelmChartListPage' })
    expect(leafName('/container/helm/charts/repo/nginx')).toEqual({
      path: 'helm/charts/:repositoryId/:name',
      name: 'HelmChartDetailPage',
    })
    expect(leafName('/container/helm/upload')).toEqual({ path: 'helm/upload', name: 'HelmUploadPage' })
    expect(leafName('/container/helm/releases')).toEqual({ path: 'helm/releases', name: 'HelmReleaseListPage' })
    expect(leafName('/container/helm/releases/default/demo')).toEqual({
      path: 'helm/releases/:namespace/:name',
      name: 'HelmReleaseDetailPage',
    })
  })

  it('does not keep a cluster helm stub in front of the catalog', () => {
    expect(leafName('/container/clusters/c1/helm').name).not.toBe('ResourceStubPage')
    expect(leafName('/container/helm/charts').name).not.toBe('ResourceStubPage')
  })
})
