import { beforeEach, describe, expect, it } from 'vitest'
import { ApiError } from '@/shared/errors/apiError'
import { ResultCode } from '@/shared/errors/resultCode'
import { createHelmMockStore } from '@/modules/container/api/helmMock'
import { valuesYamlError } from '@/modules/container/helm/yaml'

describe('helm mock session', () => {
  const store = createHelmMockStore()

  beforeEach(() => {
    store.reset()
  })

  it('rejects a duplicate chart version and keeps the catalog usable', async () => {
    const charts = await store.listCharts()
    expect(charts.map((item) => item.name).sort()).toEqual(['nginx', 'redis'])

    const duplicate = new File(['chart'], 'nginx-1.2.0.tgz', { type: 'application/gzip' })
    await expect(store.uploadChart(duplicate, 'repo-harbor')).rejects.toMatchObject({
      code: ResultCode.DUPLICATE,
    })

    const uploaded = await store.uploadChart(new File(['chart'], 'demo-app-1.0.0.tgz'), 'repo-harbor')
    expect(uploaded.chartRef).toBe('oci://harbor.local/charts/demo-app:1.0.0')
    expect((await store.listCharts({ name: 'demo-app' })).map((item) => item.name)).toEqual(['demo-app'])
  })

  it('installs, upgrades, and rolls back by appending revisions', async () => {
    const chart = await store.getChart('repo-harbor', 'nginx', '1.2.0')
    const installed = await store.install('demo-cluster', 'edge', {
      name: 'edge-nginx',
      artifactId: chart.artifactId,
      chartRef: chart.chartRef,
      valuesYaml: 'replicaCount: 3\n',
      createNamespace: true,
    })
    expect(installed.revision).toBe(1)
    expect(installed.resources.map((item) => item.kind).sort()).toEqual(['Deployment', 'Service'])

    const preview = await store.dryRunUpgrade('demo-cluster', 'edge', 'edge-nginx', {
      version: '1.1.0',
      valuesYaml: 'replicaCount: 3\n',
      valuesStrategy: 'keep',
    })
    expect(preview.manifest).toContain('kind: Deployment')

    const upgraded = await store.upgrade('demo-cluster', 'edge', 'edge-nginx', {
      version: '1.1.0',
      valuesYaml: 'replicaCount: 3\n',
      valuesStrategy: 'keep',
    })
    expect(upgraded.revision).toBe(2)
    expect(upgraded.chartVersion).toBe('1.1.0')
    expect(upgraded.history.map((item) => item.status)).toEqual(['superseded', 'deployed'])

    const rolled = await store.rollback('demo-cluster', 'edge', 'edge-nginx', { revision: 1 })
    expect(rolled.revision).toBe(3)
    expect(rolled.valuesYaml).toBe('replicaCount: 3\n')
    expect(rolled.history.at(-1)?.description).toBe('Rollback to 1')

    await store.uninstall('demo-cluster', 'edge', 'edge-nginx')
    await expect(store.getRelease('demo-cluster', 'edge', 'edge-nginx')).rejects.toBeInstanceOf(ApiError)
  })
})

describe('valuesYamlError', () => {
  it('rejects a YAML list and accepts an object', () => {
    expect(valuesYamlError('- a\n')).toBe('Values 必须是 YAML 对象')
    expect(valuesYamlError('replicaCount: 1\n')).toBeNull()
    expect(valuesYamlError('')).toBeNull()
  })
})
