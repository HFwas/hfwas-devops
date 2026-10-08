import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { helmApi } from '@/modules/container/api/helm'
import { YamlEditor } from '@/modules/container/components/YamlEditor'
import { dnsLabelError, defaultReleaseName, valuesYamlError } from '@/modules/container/helm/yaml'
import { helmReleasePath } from '@/modules/container/helm/paths'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { errorMessage } from '@/shared/errors/apiError'

export interface HelmInstallTarget {
  repositoryId: string
  chartName: string
  version: string
  chartRef: string
  artifactId: string
}

export function HelmInstallDialog({
  open,
  onOpenChange,
  clusterId,
  chart,
  defaultNamespace,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  clusterId: string | null
  chart: HelmInstallTarget | null
  defaultNamespace?: string
}) {
  const navigate = useNavigate()
  const [releaseName, setReleaseName] = useState('')
  const [namespace, setNamespace] = useState('default')
  const [createNamespace, setCreateNamespace] = useState(true)
  const [wait, setWait] = useState(false)
  const [valuesYaml, setValuesYaml] = useState('')
  const [error, setError] = useState('')
  const [dryRunManifest, setDryRunManifest] = useState<string | null>(null)

  const defaults = useQuery({
    queryKey: ['helm-chart-values', chart?.repositoryId, chart?.chartName, chart?.version],
    queryFn: () => helmApi.getValues(chart!.repositoryId, chart!.chartName, chart!.version),
    enabled: open && !!chart,
  })

  useEffect(() => {
    if (!open || !chart) return
    setReleaseName(defaultReleaseName(chart.chartName))
    setNamespace(defaultNamespace?.trim() || 'default')
    setCreateNamespace(true)
    setWait(false)
    setValuesYaml('')
    setError('')
    setDryRunManifest(null)
  }, [chart?.artifactId, chart?.chartName, defaultNamespace, open])

  const buildRequest = () => {
    if (!chart || !clusterId) {
      const message = clusterId ? '未选择 Chart' : '请先选择集群'
      setError(message)
      return { ok: false as const, message }
    }
    const message = dnsLabelError(releaseName, 'Release 名称') || dnsLabelError(namespace, '命名空间') || valuesYamlError(valuesYaml)
    if (message) {
      setError(message)
      return { ok: false as const, message }
    }
    setError('')
    return {
      ok: true as const,
      namespace: namespace.trim(),
      body: {
        name: releaseName.trim(),
        chartRef: chart.chartRef,
        artifactId: chart.artifactId,
        valuesYaml,
        createNamespace,
        wait,
      },
    }
  }

  const dryRun = useMutation({
    mutationFn: async () => {
      const payload = buildRequest()
      if (!payload.ok || !clusterId) throw new Error(payload.ok ? '请先选择集群' : payload.message)
      return helmApi.dryRunInstall(clusterId, payload.namespace, payload.body)
    },
    onSuccess: (result) => setDryRunManifest(result.manifest),
    onError: (err: unknown) => setError(errorMessage(err, '试运行失败')),
  })

  const install = useMutation({
    mutationFn: async () => {
      const payload = buildRequest()
      if (!payload.ok || !clusterId) throw new Error(payload.ok ? '请先选择集群' : payload.message)
      return helmApi.install(clusterId, payload.namespace, payload.body)
    },
    onSuccess: (release) => {
      toast.success('Helm Release 已安装')
      onOpenChange(false)
      void navigate(helmReleasePath(release.namespace, release.name))
    },
    onError: (err: unknown) => setError(errorMessage(err, '安装失败')),
  })

  const busy = dryRun.isPending || install.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[85vh] w-[calc(100vw-2rem)] max-w-6xl flex-col overflow-hidden">
        <DialogHeader>
          <DialogTitle>安装</DialogTitle>
          <p className="text-sm text-muted-foreground">
            {chart ? `${chart.chartName}:${chart.version}` : '选择 Chart 版本'}
          </p>
        </DialogHeader>
        {error ? (
          <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm">
            <p className="font-medium text-destructive">错误</p>
            <pre className="mt-1 font-mono text-xs whitespace-pre-wrap">{error}</pre>
          </div>
        ) : null}
        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="grid gap-2">
              <Label htmlFor="helm-release-name">Release 名称</Label>
              <Input
                id="helm-release-name"
                value={releaseName}
                disabled={busy || !!dryRunManifest}
                onChange={(event) => {
                  setReleaseName(event.target.value)
                  setDryRunManifest(null)
                }}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="helm-release-namespace">命名空间</Label>
              <Input
                id="helm-release-namespace"
                value={namespace}
                disabled={busy || !!dryRunManifest}
                onChange={(event) => {
                  setNamespace(event.target.value)
                  setDryRunManifest(null)
                }}
              />
            </div>
          </div>
          {dryRunManifest ? (
            <YamlEditor id="helm-install-dry-run" label="试运行清单" value={dryRunManifest} readOnly minHeightClass="min-h-80" />
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              <YamlEditor
                id="helm-install-defaults"
                label="默认 Values"
                value={defaults.isLoading ? '加载中…' : (defaults.data?.valuesYaml ?? '')}
                readOnly
              />
              <YamlEditor
                id="helm-install-custom"
                label="自定义 Values"
                value={valuesYaml}
                onChange={(value) => {
                  setValuesYaml(value)
                  setDryRunManifest(null)
                }}
              />
            </div>
          )}
          {defaults.isError ? <p className="text-sm text-destructive">{errorMessage(defaults.error, '默认 Values 加载失败')}</p> : null}
        </div>
        <DialogFooter className="items-center sm:justify-between">
          <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                className="size-4"
                checked={createNamespace}
                disabled={busy}
                onChange={(event) => setCreateNamespace(event.target.checked)}
              />
              创建命名空间
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                className="size-4"
                checked={wait}
                disabled={busy}
                onChange={(event) => setWait(event.target.checked)}
              />
              等待就绪
            </label>
          </div>
          <div className="flex flex-wrap justify-end gap-2">
            {dryRunManifest ? (
              <Button type="button" variant="outline" disabled={busy} onClick={() => setDryRunManifest(null)}>
                返回 Values
              </Button>
            ) : (
              <Button type="button" variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>
                取消
              </Button>
            )}
            {!dryRunManifest ? (
              <Button type="button" variant="outline" disabled={busy || !chart || !clusterId} onClick={() => dryRun.mutate()}>
                {dryRun.isPending ? <Loader2 className="animate-spin" /> : null}
                试运行
              </Button>
            ) : null}
            <Button type="button" disabled={busy || !chart || !clusterId} onClick={() => install.mutate()}>
              {install.isPending ? <Loader2 className="animate-spin" /> : null}
              安装
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
