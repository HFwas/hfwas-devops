import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { helmApi } from '@/modules/container/api/helm'
import { YamlEditor } from '@/modules/container/components/YamlEditor'
import { dnsLabelError, defaultReleaseName, valuesYamlError } from '@/modules/container/helm/yaml'
import { helmReleasePath } from '@/modules/container/helm/paths'
import type { HelmValuesResponse } from '@/modules/container/types/helm'
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

function valuesQueryKey(chart: HelmInstallTarget) {
  return ['helm-chart-values', chart.repositoryId, chart.chartName, chart.version] as const
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
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[85vh] w-[calc(100vw-2rem)] max-w-3xl flex-col overflow-hidden">
        {open && chart ? (
          <HelmInstallForm
            key={`${chart.artifactId}:${defaultNamespace ?? ''}`}
            clusterId={clusterId}
            chart={chart}
            defaultNamespace={defaultNamespace}
            onClose={() => onOpenChange(false)}
          />
        ) : (
          <DialogHeader>
            <DialogTitle>安装</DialogTitle>
            <p className="text-sm text-muted-foreground">选择 Chart 版本</p>
          </DialogHeader>
        )}
      </DialogContent>
    </Dialog>
  )
}

function HelmInstallForm({
  clusterId,
  chart,
  defaultNamespace,
  onClose,
}: {
  clusterId: string | null
  chart: HelmInstallTarget
  defaultNamespace?: string
  onClose: () => void
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const queryKey = valuesQueryKey(chart)
  const cached = queryClient.getQueryData<HelmValuesResponse>(queryKey)
  const touched = useRef(false)
  const [releaseName, setReleaseName] = useState(() => defaultReleaseName(chart.chartName))
  const [namespace, setNamespace] = useState(defaultNamespace?.trim() || 'default')
  const [createNamespace, setCreateNamespace] = useState(true)
  const [wait, setWait] = useState(false)
  const [valuesYaml, setValuesYaml] = useState(cached?.valuesYaml ?? '')
  const [defaultsApplied, setDefaultsApplied] = useState(cached !== undefined)
  const [error, setError] = useState('')
  const [dryRunManifest, setDryRunManifest] = useState<string | null>(null)

  const defaults = useQuery({
    queryKey,
    queryFn: () => helmApi.getValues(chart.repositoryId, chart.chartName, chart.version),
  })

  useEffect(() => {
    if (!defaults.isFetched) return
    if (!touched.current && defaults.isSuccess) {
      setValuesYaml(defaults.data?.valuesYaml ?? '')
    }
    setDefaultsApplied(true)
  }, [defaults.data, defaults.isFetched, defaults.isSuccess])

  const yamlError = valuesYamlError(valuesYaml)
  const valuesDescription = defaults.isLoading
    ? '正在加载 Chart 默认 values.yaml…'
    : defaults.isError
      ? '默认 Values 加载失败，可手动填写。留空则安装时只使用 Chart 默认值。'
      : '已填入该版本的默认 values.yaml，可直接修改。留空则安装时只使用 Chart 默认值。'

  const buildRequest = () => {
    if (!clusterId) {
      const message = '请先选择集群'
      setError(message)
      return { ok: false as const, message }
    }
    const message = dnsLabelError(releaseName, 'Release 名称') || dnsLabelError(namespace, '命名空间') || yamlError
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
      onClose()
      void navigate(helmReleasePath(release.namespace, release.name))
    },
    onError: (err: unknown) => setError(errorMessage(err, '安装失败')),
  })

  const busy = dryRun.isPending || install.isPending
  const actionsDisabled = busy || !clusterId || !defaultsApplied

  return (
    <>
      <DialogHeader>
        <DialogTitle>安装</DialogTitle>
        <p className="text-sm text-muted-foreground">
          {chart.chartName}:{chart.version}
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
          <YamlEditor
            id="helm-install-values"
            label="Values"
            description={valuesDescription}
            value={valuesYaml}
            error={yamlError}
            onChange={(value) => {
              touched.current = true
              setValuesYaml(value)
              setError('')
              setDryRunManifest(null)
            }}
          />
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
            <Button type="button" variant="outline" disabled={busy} onClick={onClose}>
              取消
            </Button>
          )}
          {!dryRunManifest ? (
            <Button type="button" variant="outline" disabled={actionsDisabled} onClick={() => dryRun.mutate()}>
              {dryRun.isPending ? <Loader2 className="animate-spin" /> : null}
              试运行
            </Button>
          ) : null}
          <Button type="button" disabled={actionsDisabled} onClick={() => install.mutate()}>
            {install.isPending ? <Loader2 className="animate-spin" /> : null}
            安装
          </Button>
        </div>
      </DialogFooter>
    </>
  )
}
