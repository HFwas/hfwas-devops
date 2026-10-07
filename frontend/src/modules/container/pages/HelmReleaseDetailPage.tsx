import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { LogPanel } from '@/components/console/LogPanel'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { helmApi } from '@/modules/container/api/helm'
import { HelmMockNotice } from '@/modules/container/components/HelmMockNotice'
import { HelmUpgradeDrawer } from '@/modules/container/components/HelmUpgradeDrawer'
import { YamlEditor } from '@/modules/container/components/YamlEditor'
import { useHelmCluster } from '@/modules/container/helm/useHelmCluster'
import { formatHelmTime } from '@/modules/container/helm/yaml'
import type { HelmReleaseHistoryItem } from '@/modules/container/types/helm'

const TABS = [
  { value: 'overview', label: '概览' },
  { value: 'values', label: 'Values' },
  { value: 'resources', label: '资源' },
  { value: 'history', label: '历史' },
  { value: 'logs', label: '日志' },
  { value: 'manifest', label: '清单' },
]

export function HelmReleaseDetailPage() {
  const { namespace = '', name = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { clusterId, synthetic } = useHelmCluster()
  const [tab, setTab] = useState('overview')
  const [upgradeOpen, setUpgradeOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [historyValues, setHistoryValues] = useState<HelmReleaseHistoryItem | null>(null)

  const query = useQuery({
    queryKey: ['helm-release', clusterId, namespace, name],
    queryFn: () => helmApi.getRelease(clusterId!, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const release = query.data

  const rollback = useMutation({
    mutationFn: (revision: number) => helmApi.rollback(clusterId!, namespace, name, { revision }),
    onSuccess: async () => {
      toast.success('已回滚，并生成新的 revision')
      await queryClient.invalidateQueries({ queryKey: ['helm-release', clusterId, namespace, name] })
      await queryClient.invalidateQueries({ queryKey: ['helm-releases', clusterId] })
    },
    onError: (error: Error) => toast.error(error.message || '回滚失败'),
  })

  const remove = useMutation({
    mutationFn: () => helmApi.uninstall(clusterId!, namespace, name),
    onSuccess: async () => {
      toast.success('Release 已卸载')
      await queryClient.invalidateQueries({ queryKey: ['helm-releases', clusterId] })
      void navigate('/container/helm/releases')
    },
    onError: (error: Error) => toast.error(error.message || '卸载失败'),
  })

  return (
    <div className="flex flex-col gap-4">
      <HelmMockNotice syntheticCluster={synthetic} />
      <DetailShell
        title={name}
        description={release ? `${release.chartName}:${release.chartVersion}` : undefined}
        meta={namespace}
        actions={
          <>
            <Button size="sm" variant="outline" disabled={!release} onClick={() => setUpgradeOpen(true)}>
              升级
            </Button>
            <Button size="sm" variant="destructive" disabled={!release} onClick={() => setDeleteOpen(true)}>
              卸载
            </Button>
          </>
        }
        tabs={TABS}
        value={tab}
        onValueChange={setTab}
      >
        {!clusterId ? <p className="text-sm text-muted-foreground">请先选择集群。</p> : null}
        {query.isLoading ? <p className="text-sm text-muted-foreground">加载中…</p> : null}
        {query.isError ? <p className="text-sm text-destructive">Release 加载失败</p> : null}
        {release && tab === 'overview' ? (
          <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Info label="状态" value={<StatusIcon status={release.status} />} />
            <Info label="Chart" value={release.chartName} />
            <Info label="版本" value={release.chartVersion} mono />
            <Info label="Revision" value={String(release.revision)} />
            <Info label="命名空间" value={release.namespace} />
            <Info label="更新时间" value={formatHelmTime(release.updatedAt)} />
            <div className="sm:col-span-2">
              <Info label="chartRef" value={release.chartRef} mono />
            </div>
            {release.notes ? (
              <div className="sm:col-span-2 lg:col-span-4">
                <Info label="说明" value={release.notes} />
              </div>
            ) : null}
          </dl>
        ) : null}
        {release && tab === 'values' ? (
          <YamlEditor id="helm-release-values" label="当前 Values" value={release.valuesYaml} readOnly minHeightClass="min-h-96" />
        ) : null}
        {release && tab === 'resources' ? (
          <DataTable
            data={release.resources}
            getRowId={(row) => `${row.kind}/${row.namespace ?? ''}/${row.name}`}
            empty="没有关联资源"
            columns={[
              { id: 'kind', header: 'Kind', cell: (row) => row.kind },
              { id: 'name', header: '名称', cell: (row) => row.name },
              { id: 'namespace', header: '命名空间', cell: (row) => row.namespace || '—' },
              { id: 'api', header: 'API Version', cell: (row) => <span className="font-mono text-xs">{row.apiVersion}</span> },
              { id: 'status', header: '状态', cell: (row) => <StatusIcon status={row.status} /> },
            ]}
          />
        ) : null}
        {release && tab === 'history' ? (
          <DataTable
            data={[...release.history].sort((a, b) => b.revision - a.revision)}
            getRowId={(row) => String(row.revision)}
            empty="没有历史"
            columns={[
              { id: 'rev', header: 'Revision', cell: (row) => row.revision },
              { id: 'status', header: '状态', cell: (row) => <StatusIcon status={row.status} /> },
              {
                id: 'chart',
                header: 'Chart',
                cell: (row) => (
                  <span className="font-mono text-xs">
                    {row.chartName}:{row.chartVersion}
                  </span>
                ),
              },
              { id: 'desc', header: '说明', cell: (row) => row.description },
              { id: 'time', header: '时间', cell: (row) => formatHelmTime(row.updatedAt) },
              {
                id: 'actions',
                header: '',
                cell: (row) => (
                  <div className="flex gap-2">
                    <Button size="sm" variant="ghost" onClick={() => setHistoryValues(row)}>
                      Values
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={rollback.isPending || row.revision === release.revision}
                      onClick={() => rollback.mutate(row.revision)}
                    >
                      回滚
                    </Button>
                  </div>
                ),
              },
            ]}
          />
        ) : null}
        {tab === 'logs' ? (
          <LogPanel title="Release 日志" connection="idle">
            <p className="p-3 text-sm text-muted-foreground">
              工作负载日志等 Helm 后端聚合 Pod 后再接入。现在可以在
              <Link className="mx-1 text-primary hover:underline" to="/container/clusters">
                集群
              </Link>
              的 Pod 页查看。
            </p>
          </LogPanel>
        ) : null}
        {release && tab === 'manifest' ? (
          <YamlEditor id="helm-release-manifest" label="清单" value={release.manifest} readOnly minHeightClass="min-h-96" />
        ) : null}
      </DetailShell>
      <HelmUpgradeDrawer
        open={upgradeOpen}
        onOpenChange={setUpgradeOpen}
        release={release ?? null}
        onUpgraded={() => {
          void queryClient.invalidateQueries({ queryKey: ['helm-release', clusterId, namespace, name] })
          void queryClient.invalidateQueries({ queryKey: ['helm-releases', clusterId] })
        }}
      />
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>卸载 {namespace}/{name}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">这会从集群移除该 Helm Release。</p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              取消
            </Button>
            <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate()}>
              卸载
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={!!historyValues} onOpenChange={(open) => !open && setHistoryValues(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>Revision {historyValues?.revision} Values</DialogTitle>
          </DialogHeader>
          <YamlEditor id="helm-history-values" label="Values" value={historyValues?.valuesYaml ?? ''} readOnly />
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Info({ label, value, mono = false }: { label: string; value: ReactNode; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={mono ? 'font-mono text-xs break-all' : 'text-sm'}>{value}</dd>
    </div>
  )
}
