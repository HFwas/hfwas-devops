import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { helmApi } from '@/modules/container/api/helm'
import { YamlEditor } from '@/modules/container/components/YamlEditor'
import { valuesYamlError } from '@/modules/container/helm/yaml'
import type { HelmRelease } from '@/modules/container/types/helm'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { errorMessage } from '@/shared/errors/apiError'

export function HelmUpgradeDrawer({
  open,
  onOpenChange,
  release,
  onUpgraded,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  release: HelmRelease | null
  onUpgraded: () => void
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-3xl">
        {open && release ? (
          <HelmUpgradeForm
            key={`${release.namespace}/${release.name}/${release.revision}`}
            release={release}
            onClose={() => onOpenChange(false)}
            onUpgraded={onUpgraded}
          />
        ) : (
          <SheetHeader>
            <SheetTitle>升级</SheetTitle>
            <SheetDescription>Helm Release</SheetDescription>
          </SheetHeader>
        )}
      </SheetContent>
    </Sheet>
  )
}

function HelmUpgradeForm({
  release,
  onClose,
  onUpgraded,
}: {
  release: HelmRelease
  onClose: () => void
  onUpgraded: () => void
}) {
  const [version, setVersion] = useState(release.chartVersion)
  const [strategy, setStrategy] = useState<'keep' | 'reset'>('keep')
  const [valuesYaml, setValuesYaml] = useState(release.valuesYaml)
  const [resetReadyVersion, setResetReadyVersion] = useState<string | null>(null)
  const [wait, setWait] = useState(false)
  const [rollbackOnFailure, setRollbackOnFailure] = useState(false)
  const [error, setError] = useState('')
  const [dryRunManifest, setDryRunManifest] = useState<string | null>(null)
  const resetAppliedRef = useRef<string | null>(null)
  const valuesTouchedRef = useRef(false)

  const chart = useQuery({
    queryKey: ['helm-chart', release.repositoryId, release.chartName],
    queryFn: () => helmApi.getChart(release.repositoryId!, release.chartName),
    enabled: !!release.repositoryId,
  })

  const versions = chart.data?.versions ?? []
  const activeVersion = version || release.chartVersion
  const selected = versions.find((item) => item.version === activeVersion)

  const defaults = useQuery({
    queryKey: ['helm-chart-values', release.repositoryId, release.chartName, activeVersion],
    queryFn: () => helmApi.getValues(release.repositoryId!, release.chartName, activeVersion),
    enabled: !!release.repositoryId && !!activeVersion,
  })

  useEffect(() => {
    if (strategy !== 'reset') return
    if (!release.repositoryId) {
      setResetReadyVersion(activeVersion)
      return
    }
    if (!defaults.isFetched) return
    if (resetAppliedRef.current === activeVersion) return
    resetAppliedRef.current = activeVersion
    if (defaults.isSuccess && !valuesTouchedRef.current) {
      setValuesYaml(defaults.data?.valuesYaml ?? '')
      setDryRunManifest(null)
    }
    setResetReadyVersion(activeVersion)
  }, [activeVersion, defaults.data, defaults.isFetched, defaults.isSuccess, release.repositoryId, strategy])

  const yamlError = valuesYamlError(valuesYaml)
  const valuesDescription =
    strategy === 'reset'
      ? !release.repositoryId
        ? '当前 Release 没有关联 Chart 仓库，无法载入默认 values.yaml。可手动填写。留空则升级时只使用 Chart 默认值。'
        : defaults.isLoading || !defaults.isFetched
          ? '正在加载所选版本的默认 values.yaml…'
          : defaults.isError
            ? '默认 Values 加载失败，可手动填写。留空则升级时只使用 Chart 默认值。'
            : '已填入所选版本的默认 values.yaml，可直接修改。留空则升级时只使用 Chart 默认值。'
      : '已填入当前 Release 的 Values，可直接修改。留空则升级时只使用 Chart 默认值。'

  const buildBody = () => {
    if (yamlError) {
      setError(yamlError)
      return { ok: false as const, message: yamlError }
    }
    setError('')
    return {
      ok: true as const,
      body: {
        chartRef: selected?.chartRef ?? release.chartRef,
        artifactId: selected?.artifactId ?? release.artifactId,
        version: activeVersion,
        valuesYaml,
        valuesStrategy: strategy,
        wait,
        rollbackOnFailure,
      },
    }
  }

  const dryRun = useMutation({
    mutationFn: async () => {
      const payload = buildBody()
      if (!payload.ok) throw new Error(payload.message)
      return helmApi.dryRunUpgrade(release.clusterId, release.namespace, release.name, payload.body)
    },
    onSuccess: (result) => setDryRunManifest(result.manifest),
    onError: (err: unknown) => setError(errorMessage(err, '试运行失败')),
  })

  const upgrade = useMutation({
    mutationFn: async () => {
      const payload = buildBody()
      if (!payload.ok) throw new Error(payload.message)
      return helmApi.upgrade(release.clusterId, release.namespace, release.name, payload.body)
    },
    onSuccess: () => {
      toast.success('Release 已升级')
      onClose()
      onUpgraded()
    },
    onError: (err: unknown) => setError(errorMessage(err, '升级失败')),
  })

  const busy = dryRun.isPending || upgrade.isPending
  const waitingForReset = strategy === 'reset' && resetReadyVersion !== activeVersion
  const actionsDisabled = busy || waitingForReset

  const selectStrategy = (next: 'keep' | 'reset') => {
    valuesTouchedRef.current = false
    resetAppliedRef.current = null
    setStrategy(next)
    setResetReadyVersion(null)
    setDryRunManifest(null)
    setError('')
    if (next === 'keep') setValuesYaml(release.valuesYaml)
  }

  return (
    <>
      <SheetHeader>
        <SheetTitle>升级</SheetTitle>
        <SheetDescription>
          {release.namespace}/{release.name}
        </SheetDescription>
      </SheetHeader>
      <div className="flex flex-col gap-4 px-4">
        {error ? (
          <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm">
            <p className="font-medium text-destructive">错误</p>
            <pre className="mt-1 font-mono text-xs whitespace-pre-wrap">{error}</pre>
          </div>
        ) : null}
        {chart.isError ? <p className="text-sm text-destructive">{errorMessage(chart.error, 'Chart 版本加载失败')}</p> : null}
        {defaults.isError ? <p className="text-sm text-destructive">{errorMessage(defaults.error, '默认 Values 加载失败')}</p> : null}
        <div className="grid gap-4 md:grid-cols-2">
          <div className="grid gap-2">
            <Label htmlFor="helm-upgrade-version">版本</Label>
            <select
              id="helm-upgrade-version"
              className="h-9 rounded-md border border-input bg-background px-2 text-sm"
              value={activeVersion}
              disabled={busy || !!dryRunManifest}
              onChange={(event) => {
                valuesTouchedRef.current = false
                resetAppliedRef.current = null
                setResetReadyVersion(null)
                setVersion(event.target.value)
                setDryRunManifest(null)
              }}
            >
              {(versions.length > 0 ? versions : [{ version: release.chartVersion, appVersion: release.appVersion }]).map((item) => (
                <option key={item.version} value={item.version}>
                  {item.version}
                  {item.version === release.chartVersion ? '（当前）' : ''}
                  {item.appVersion ? ` · ${item.appVersion}` : ''}
                </option>
              ))}
            </select>
          </div>
          <fieldset className="grid gap-2">
            <legend className="text-sm font-medium">Values 策略</legend>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="helm-values-strategy"
                checked={strategy === 'keep'}
                disabled={busy || !!dryRunManifest}
                onChange={() => selectStrategy('keep')}
              />
              保留当前自定义 Values
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="helm-values-strategy"
                checked={strategy === 'reset'}
                disabled={busy || !!dryRunManifest}
                onChange={() => selectStrategy('reset')}
              />
              重置为所选版本的默认 Values
            </label>
          </fieldset>
        </div>
        {dryRunManifest ? (
          <YamlEditor id="helm-upgrade-dry-run" label="试运行清单" value={dryRunManifest} readOnly minHeightClass="min-h-72" />
        ) : (
          <div className="grid gap-4">
            <YamlEditor
              id="helm-upgrade-values"
              label="Values"
              description={valuesDescription}
              value={valuesYaml}
              error={yamlError}
              onChange={(value) => {
                valuesTouchedRef.current = true
                setValuesYaml(value)
                setError('')
                setDryRunManifest(null)
              }}
            />
            <YamlEditor
              id="helm-upgrade-defaults"
              label="Chart 默认 Values（只读）"
              description={
                defaults.isLoading
                  ? '正在加载 Chart 默认 values.yaml…'
                  : '只读对照，不会提交。提交内容以上方 Values 为准。'
              }
              value={defaults.isLoading ? '' : (defaults.data?.valuesYaml ?? '')}
              readOnly
              minHeightClass="min-h-40"
            />
          </div>
        )}
      </div>
      <SheetFooter className="gap-3 sm:flex-col sm:items-stretch">
        <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
          <label className="flex items-center gap-2">
            <input type="checkbox" className="size-4" checked={wait} disabled={busy} onChange={(event) => setWait(event.target.checked)} />
            等待就绪
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              className="size-4"
              checked={rollbackOnFailure}
              disabled={busy}
              onChange={(event) => setRollbackOnFailure(event.target.checked)}
            />
            失败时回滚
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
          <Button type="button" disabled={actionsDisabled} onClick={() => upgrade.mutate()}>
            {upgrade.isPending ? <Loader2 className="animate-spin" /> : null}
            升级
          </Button>
        </div>
      </SheetFooter>
    </>
  )
}
