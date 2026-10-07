import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { PageHeader } from '@/components/console/PageHeader'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { helmApi } from '@/modules/container/api/helm'
import { HelmMockNotice } from '@/modules/container/components/HelmMockNotice'
import { helmChartPath } from '@/modules/container/helm/paths'
import type { HelmChartArtifact } from '@/modules/container/types/helm'

const MAX_BYTES = 50 * 1024 * 1024

export function HelmUploadPage() {
  const queryClient = useQueryClient()
  const repos = useQuery({ queryKey: ['helm-repositories'], queryFn: () => helmApi.listRepositories() })
  const [repositoryId, setRepositoryId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState('')
  const [artifact, setArtifact] = useState<HelmChartArtifact | null>(null)

  const selectedRepo = repositoryId || repos.data?.[0]?.id || ''

  const upload = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('请选择 .tgz 文件')
      if (!selectedRepo) throw new Error('请选择仓库')
      if (!file.name.toLowerCase().endsWith('.tgz')) throw new Error('只接受 .tgz Helm chart 包')
      if (file.size > MAX_BYTES) throw new Error('文件超过 50 MB')
      return helmApi.uploadChart(file, selectedRepo)
    },
    onSuccess: async (result) => {
      setError('')
      setArtifact(result)
      toast.success(`${result.chartName}@${result.version} 已登记`)
      await queryClient.invalidateQueries({ queryKey: ['helm-charts'] })
    },
    onError: (err: Error) => {
      setArtifact(null)
      setError(err.message || '上传失败')
    },
  })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="上传 Chart"
        description="选择 Harbor OCI 仓库并上传 .tgz。同名同版本会被拒绝，不会覆盖已有制品。"
      />
      <HelmMockNotice />
      <ol className="grid gap-3 md:grid-cols-3">
        <Step n={1} title="仓库" active={!artifact && !file} />
        <Step n={2} title="文件" active={!artifact && !!file} />
        <Step n={3} title="结果" active={!!artifact} />
      </ol>
      <form
        className="grid max-w-xl gap-4 rounded-lg border p-4"
        onSubmit={(event) => {
          event.preventDefault()
          upload.mutate()
        }}
      >
        <div className="grid gap-2">
          <Label htmlFor="helm-upload-repo">仓库</Label>
          <select
            id="helm-upload-repo"
            className="h-9 rounded-md border border-input bg-background px-2 text-sm"
            value={selectedRepo}
            onChange={(event) => setRepositoryId(event.target.value)}
          >
            {(repos.data ?? []).map((repo) => (
              <option key={repo.id} value={repo.id}>
                {repo.name}（{repo.url}）
              </option>
            ))}
          </select>
          {repos.isError ? <p className="text-sm text-destructive">仓库列表加载失败</p> : null}
        </div>
        <div className="grid gap-2">
          <Label htmlFor="helm-upload-file">Chart 包</Label>
          <input
            id="helm-upload-file"
            type="file"
            accept=".tgz,application/gzip"
            className="text-sm"
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null)
              setArtifact(null)
              setError('')
            }}
          />
          <p className="text-xs text-muted-foreground">仅 .tgz，最大 50 MB。文件名建议为 name-version.tgz，正式环境以 Chart.yaml 为准。</p>
        </div>
        {error ? (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        ) : null}
        <div>
          <Button type="submit" disabled={upload.isPending || !file || !selectedRepo}>
            {upload.isPending ? '上传中…' : '上传并登记'}
          </Button>
        </div>
      </form>
      {artifact ? (
        <section className="grid max-w-xl gap-2 rounded-lg border p-4 text-sm">
          <h2 className="font-medium">已登记</h2>
          <p>
            {artifact.chartName} <span className="font-mono text-xs">{artifact.version}</span>
          </p>
          <p className="font-mono text-xs break-all">{artifact.chartRef}</p>
          <div className="flex gap-2">
            <Button size="sm" asChild>
              <Link to={`${helmChartPath(artifact.repositoryId, artifact.chartName, artifact.version)}&install=1`}>
                去安装
              </Link>
            </Button>
            <Button size="sm" variant="outline" asChild>
              <Link to={helmChartPath(artifact.repositoryId, artifact.chartName, artifact.version)}>查看 Chart</Link>
            </Button>
          </div>
        </section>
      ) : null}
    </div>
  )
}

function Step({ n, title, active }: { n: number; title: string; active: boolean }) {
  return (
    <li className={`rounded-md border px-3 py-2 text-sm ${active ? 'border-primary text-primary' : 'text-muted-foreground'}`}>
      <span className="mr-2 font-mono text-xs">{n}</span>
      {title}
    </li>
  )
}
