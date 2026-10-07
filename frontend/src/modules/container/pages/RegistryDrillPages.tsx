import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { PageHeader } from '@/components/console/PageHeader'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { clusterApi } from '@/modules/container/api/cluster'
import { deployApi } from '@/modules/container/api/deploy'
import { imageApi } from '@/modules/container/api/image'
import { registryApi } from '@/modules/container/api/registry'
import { StatusIcon } from '@/components/console/StatusIcon'

function shortRepo(project: string, name: string) {
  const prefix = `${project}/`
  return name.startsWith(prefix) ? name.slice(prefix.length) : name
}

function repoPath(registryId: string, project: string, repo: string) {
  const name = shortRepo(project, repo)
  return `/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repos/${name.split('/').map(encodeURIComponent).join('/')}`
}

export function RegistryDetailPage() {
  const { registryId = '' } = useParams()
  const registry = useQuery({
    queryKey: ['container-registry', registryId],
    queryFn: () => registryApi.get(registryId),
    enabled: !!registryId,
  })
  const projects = useQuery({
    queryKey: ['container-registry-projects', registryId],
    queryFn: () => imageApi.listProjects(registryId),
    enabled: !!registryId,
  })
  const info = registry.data
  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/container/registries">返回</Link>
        </Button>
      }
      title={info?.alias || info?.name || '仓库'}
      description={info?.url}
      meta={info ? <StatusIcon status={info.status} /> : registry.isLoading ? '加载中…' : registry.isError ? '加载失败' : null}
    >
      <DataTable
        columns={[
          {
            id: 'name',
            header: '项目',
            cell: (item) => (
              <Link
                className="font-medium text-primary hover:underline"
                to={`/container/registries/${registryId}/projects/${encodeURIComponent(item.name)}/repos`}
              >
                {item.name}
              </Link>
            ),
          },
          { id: 'repos', header: '镜像数', cell: (item) => String(item.repoCount) },
          { id: 'updated', header: '更新时间', cell: (item) => item.updateTime || '—' },
        ]}
        data={projects.data ?? []}
        getRowId={(item) => item.name}
        loading={projects.isFetching}
        error={projects.isError ? '项目加载失败' : undefined}
        empty="这个仓库还没有项目"
      />
    </DetailShell>
  )
}

export function RepoListPage() {
  const { registryId = '', project = '' } = useParams()
  const query = useQuery({
    queryKey: ['container-repos', registryId, project],
    queryFn: () => imageApi.listRepositories(registryId, project),
    enabled: !!registryId && !!project,
  })
  const rows = query.data ?? []
  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={project || '镜像'}
        description={query.isSuccess ? `共 ${rows.length} 个镜像` : '仓库项目中的镜像'}
        actions={
          <Button variant="ghost" size="sm" asChild>
            <Link to={`/container/registries/${registryId}`}>返回</Link>
          </Button>
        }
      />
      <DataTable
        columns={[
          {
            id: 'name',
            header: '镜像',
            cell: (item) => (
              <Link className="font-medium text-primary hover:underline" to={repoPath(registryId, project, item.name)}>
                {item.name}
              </Link>
            ),
          },
          { id: 'artifacts', header: '制品数', cell: (item) => String(item.artifactCount) },
          { id: 'pulls', header: '拉取次数', cell: (item) => String(item.pullCount) },
          { id: 'updated', header: '更新时间', cell: (item) => item.updateTime || '—' },
        ]}
        data={rows}
        getRowId={(item) => item.name}
        loading={query.isFetching}
        error={query.isError ? '镜像列表加载失败' : undefined}
        empty="这个项目还没有镜像"
      />
    </div>
  )
}

const deploySchema = z.object({
  clusterId: z.string().min(1, '请选择集群'),
  namespace: z.string().trim().min(1, '请填写命名空间'),
  name: z.string().trim().min(1, '请填写名称'),
  replicas: z.string().trim().min(1),
  containerPort: z.string().optional(),
})

type DeployValues = z.infer<typeof deploySchema>

export function RepoDetailPage() {
  const params = useParams()
  const registryId = params.registryId ?? ''
  const project = params.project ?? ''
  const repo = params['*'] ?? ''
  const queryClient = useQueryClient()
  const [imageTag, setImageTag] = useState('')
  const [open, setOpen] = useState(false)
  const registry = useQuery({
    queryKey: ['container-registry', registryId],
    queryFn: () => registryApi.get(registryId),
    enabled: !!registryId,
  })
  const artifacts = useQuery({
    queryKey: ['container-artifacts', registryId, project, repo],
    queryFn: () => imageApi.listArtifacts(registryId, project, repo),
    enabled: !!registryId && !!project && !!repo,
  })
  const clusters = useQuery({
    queryKey: ['container-clusters'],
    queryFn: () => clusterApi.page({ pageNo: 1, pageSize: 100 }),
    enabled: open,
  })
  const form = useForm<DeployValues>({
    resolver: zodResolver(deploySchema),
    defaultValues: { clusterId: '', namespace: 'default', name: '', replicas: '1', containerPort: '' },
  })
  const remove = useMutation({
    mutationFn: (reference: string) => imageApi.deleteArtifact(registryId, project, repo, reference),
    onSuccess: async () => {
      toast.success('制品已删除')
      await queryClient.invalidateQueries({ queryKey: ['container-artifacts', registryId, project, repo] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
  const deploy = useMutation({
    mutationFn: (values: DeployValues) => {
      const host = (registry.data?.url ?? '').replace(/^https?:\/\//, '').replace(/\/$/, '')
      const image = `${host}/${repo}:${imageTag}`
      return deployApi.deployFromImage(registryId, {
        clusterId: values.clusterId,
        namespace: values.namespace,
        name: values.name,
        image,
        replicas: Number(values.replicas),
        containerPort: values.containerPort ? Number(values.containerPort) : undefined,
      })
    },
    onSuccess: (result) => {
      toast.success(`已部署 ${result.namespace}/${result.deploymentName}`)
      setOpen(false)
    },
    onError: (error: Error) => toast.error(error.message || '部署失败'),
  })

  const rows = artifacts.data ?? []
  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repos`}>返回</Link>
        </Button>
      }
      title={repo || '镜像'}
      description={artifacts.isSuccess ? `${rows.length} 个制品` : undefined}
      meta={artifacts.isLoading ? '加载中…' : artifacts.isError ? '加载失败' : null}
    >
      <DataTable
        columns={[
          {
            id: 'tags',
            header: '标签',
            cell: (item) => item.tags.map((entry) => entry.name).join(', ') || '—',
          },
          {
            id: 'digest',
            header: '摘要',
            className: 'max-w-xs truncate font-mono text-xs',
            cell: (item) => item.digest,
          },
          { id: 'size', header: '大小', cell: (item) => item.size },
          {
            id: 'scan',
            header: '扫描',
            cell: (item) =>
              item.scanOverview ? (
                <StatusIcon
                  status={item.scanOverview.severity || item.scanOverview.status}
                  label={`${item.scanOverview.severity || item.scanOverview.status} (${item.scanOverview.totalVulnerabilities})`}
                />
              ) : (
                '—'
              ),
          },
          {
            id: 'actions',
            header: '',
            className: 'text-right',
            cell: (item) => {
              const tag = item.tags[0]?.name
              return (
                <div className="space-x-1">
                  {tag && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setImageTag(tag)
                        form.setValue('name', repo.split('/').pop() || repo)
                        setOpen(true)
                      }}
                    >
                      部署
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                    onClick={() => {
                      const reference = tag || item.digest
                      if (window.confirm(`删除制品「${reference}」？`)) remove.mutate(reference)
                    }}
                  >
                    删除
                  </Button>
                </div>
              )
            },
          },
        ]}
        data={rows}
        getRowId={(item) => item.digest}
        loading={artifacts.isFetching}
        error={artifacts.isError ? '制品加载失败' : undefined}
        empty="还没有制品"
      />
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>部署 {repo}:{imageTag}</DialogTitle>
          </DialogHeader>
          <form className="flex flex-col gap-3" onSubmit={form.handleSubmit((values) => deploy.mutate(values))}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="deploy-cluster">集群</Label>
              <select
                id="deploy-cluster"
                className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                {...form.register('clusterId')}
              >
                <option value="">选择集群</option>
                {(clusters.data?.records ?? []).map((cluster) => (
                  <option key={cluster.id} value={cluster.id}>
                    {cluster.alias || cluster.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="deploy-ns">命名空间</Label>
              <Input id="deploy-ns" {...form.register('namespace')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="deploy-name">Deployment 名称</Label>
              <Input id="deploy-name" {...form.register('name')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="deploy-replicas">副本</Label>
              <Input id="deploy-replicas" type="number" {...form.register('replicas')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="deploy-port">容器端口</Label>
              <Input id="deploy-port" {...form.register('containerPort')} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                取消
              </Button>
              <Button type="submit" disabled={deploy.isPending}>
                部署
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </DetailShell>
  )
}

export { repoPath }
