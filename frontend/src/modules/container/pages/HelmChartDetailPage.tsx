import { useEffect, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { helmApi } from '@/modules/container/api/helm'
import { HelmInstallDialog, type HelmInstallTarget } from '@/modules/container/components/HelmInstallDialog'
import { HelmMockNotice } from '@/modules/container/components/HelmMockNotice'
import { YamlEditor } from '@/modules/container/components/YamlEditor'
import { useHelmCluster } from '@/modules/container/helm/useHelmCluster'
import { formatHelmTime } from '@/modules/container/helm/yaml'
import { errorMessage } from '@/shared/errors/apiError'

const TABS = [
  { value: 'overview', label: '概览' },
  { value: 'values', label: 'Values' },
  { value: 'versions', label: '版本' },
  { value: 'readme', label: 'README' },
]

export function HelmChartDetailPage() {
  const { repositoryId = '', name = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const version = params.get('version') || undefined
  const [tab, setTab] = useState('overview')
  const [installOpen, setInstallOpen] = useState(params.get('install') === '1')
  const [installTarget, setInstallTarget] = useState<HelmInstallTarget | null>(null)
  const { clusterId, namespace } = useHelmCluster()

  const chart = useQuery({
    queryKey: ['helm-chart', repositoryId, name, version],
    queryFn: () => helmApi.getChart(repositoryId, name, version),
    enabled: !!repositoryId && !!name,
  })
  const values = useQuery({
    queryKey: ['helm-chart-values', repositoryId, name, chart.data?.version],
    queryFn: () => helmApi.getValues(repositoryId, name, chart.data!.version),
    enabled: !!chart.data,
  })

  const detail = chart.data
  const installFromQuery = params.get('install') === '1'

  useEffect(() => {
    if (!installFromQuery || !detail) return
    setInstallTarget({
      repositoryId: detail.repositoryId,
      chartName: detail.chartName,
      version: detail.version,
      chartRef: detail.chartRef,
      artifactId: detail.artifactId,
    })
  }, [detail, installFromQuery])

  function openInstall(versionRow?: { version: string; chartRef: string; artifactId: string }) {
    if (!detail) return
    const row = versionRow ?? {
      version: detail.version,
      chartRef: detail.chartRef,
      artifactId: detail.artifactId,
    }
    setInstallTarget({
      repositoryId: detail.repositoryId,
      chartName: detail.chartName,
      version: row.version,
      chartRef: row.chartRef,
      artifactId: row.artifactId,
    })
    setInstallOpen(true)
  }

  return (
    <div className="flex flex-col gap-4">
      <HelmMockNotice />
      <DetailShell
        title={name}
        description={detail?.description}
        meta={detail ? `${detail.repositoryName} · ${detail.version}` : undefined}
        actions={
          <Button size="sm" disabled={!detail} onClick={() => openInstall()}>
            安装
          </Button>
        }
        tabs={TABS}
        value={tab}
        onValueChange={setTab}
      >
        {chart.isLoading ? <p className="text-sm text-muted-foreground">加载中…</p> : null}
        {chart.isError ? <p className="text-sm text-destructive">{errorMessage(chart.error, 'Chart 加载失败')}</p> : null}
        {detail && tab === 'overview' ? (
          <dl className="grid gap-3 sm:grid-cols-2">
            <Field label="仓库" value={detail.repositoryName} />
            <Field label="版本" value={detail.version} mono />
            <Field label="App 版本" value={detail.appVersion || '—'} mono />
            <Field label="chartRef" value={detail.chartRef} mono />
            <div className="sm:col-span-2">
              <Field
                label="关键词"
                value={
                  detail.keywords.length ? (
                    <span className="flex flex-wrap gap-1">
                      {detail.keywords.map((keyword) => (
                        <Badge key={keyword} variant="outline">
                          {keyword}
                        </Badge>
                      ))}
                    </span>
                  ) : (
                    '—'
                  )
                }
              />
            </div>
          </dl>
        ) : null}
        {detail && tab === 'values' ? (
          <>
            {values.isError ? <p className="mb-2 text-sm text-destructive">{errorMessage(values.error, '默认 Values 加载失败')}</p> : null}
            <YamlEditor
              id="helm-chart-default-values"
              label="默认 Values"
              value={values.isLoading ? '加载中…' : (values.data?.valuesYaml ?? '')}
              readOnly
              minHeightClass="min-h-96"
            />
          </>
        ) : null}
        {detail && tab === 'versions' ? (
          <DataTable
            data={detail.versions}
            getRowId={(row) => row.version}
            empty="没有版本"
            columns={[
              {
                id: 'version',
                header: '版本',
                cell: (row) => (
                  <button
                    type="button"
                    className="font-mono text-xs text-primary hover:underline"
                    onClick={() => {
                      const next = new URLSearchParams(params)
                      next.set('version', row.version)
                      setParams(next)
                    }}
                  >
                    {row.version}
                  </button>
                ),
              },
              { id: 'app', header: 'App 版本', cell: (row) => row.appVersion || '—' },
              { id: 'ref', header: 'chartRef', cell: (row) => <span className="font-mono text-xs">{row.chartRef}</span> },
              { id: 'time', header: '时间', cell: (row) => formatHelmTime(row.createdAt) },
              {
                id: 'install',
                header: '',
                cell: (row) => (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      const next = new URLSearchParams(params)
                      next.set('version', row.version)
                      setParams(next)
                      openInstall(row)
                    }}
                  >
                    安装此版本
                  </Button>
                ),
              },
            ]}
          />
        ) : null}
        {detail && tab === 'readme' ? (
          <pre className="overflow-auto rounded-lg border bg-muted/30 p-4 font-mono text-xs whitespace-pre-wrap">{detail.readme || '没有 README'}</pre>
        ) : null}
        <p className="mt-4 text-sm">
          <Link className="text-primary hover:underline" to="/container/helm/charts">
            返回目录
          </Link>
        </p>
      </DetailShell>
      <HelmInstallDialog
        open={installOpen && !!detail}
        onOpenChange={setInstallOpen}
        clusterId={clusterId}
        defaultNamespace={namespace}
        chart={installTarget}
      />
    </div>
  )
}

function Field({ label, value, mono = false }: { label: string; value: ReactNode; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={mono ? 'font-mono text-xs break-all' : 'text-sm'}>{value}</dd>
    </div>
  )
}
