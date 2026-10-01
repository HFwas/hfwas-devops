import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { clusterApi } from '@/modules/container/api/cluster'
import { deployApi } from '@/modules/container/api/deploy'
import { imageApi } from '@/modules/container/api/image'
import { registryApi } from '@/modules/container/api/registry'
import { StatusBadge } from '@/modules/container/components/StatusBadge'

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
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link to="/container/registries">返回</Link>
        </Button>
        <h1 className="text-xl font-semibold">{info?.alias || info?.name || '仓库'}</h1>
        {info && <StatusBadge status={info.status} />}
      </div>
      {info && <p className="text-sm text-muted-foreground">{info.url}</p>}
      {projects.isLoading && <p className="text-sm text-muted-foreground">加载项目…</p>}
      {projects.isError && <p className="text-sm text-destructive">项目加载失败</p>}
      {projects.isSuccess && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>项目</TableHead>
              <TableHead>镜像数</TableHead>
              <TableHead>更新时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {projects.data.map((item) => (
              <TableRow key={item.name}>
                <TableCell className="font-medium">
                  <Link
                    className="text-primary hover:underline"
                    to={`/container/registries/${registryId}/projects/${encodeURIComponent(item.name)}/repos`}
                  >
                    {item.name}
                  </Link>
                </TableCell>
                <TableCell>{item.repoCount}</TableCell>
                <TableCell>{item.updateTime || '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  )
}

export function RepoListPage() {
  const { registryId = '', project = '' } = useParams()
  const query = useQuery({
    queryKey: ['container-repos', registryId, project],
    queryFn: () => imageApi.listRepositories(registryId, project),
    enabled: !!registryId && !!project,
  })
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/registries/${registryId}`}>返回</Link>
        </Button>
        <h1 className="text-xl font-semibold">{project}</h1>
      </div>
      {query.isLoading && <p className="text-sm text-muted-foreground">加载镜像…</p>}
      {query.isError && <p className="text-sm text-destructive">镜像列表加载失败</p>}
      {query.isSuccess && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>镜像</TableHead>
              <TableHead>制品数</TableHead>
              <TableHead>拉取次数</TableHead>
              <TableHead>更新时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.map((item) => (
              <TableRow key={item.name}>
                <TableCell className="font-medium">
                  <Link className="text-primary hover:underline" to={repoPath(registryId, project, item.name)}>
                    {item.name}
                  </Link>
                </TableCell>
                <TableCell>{item.artifactCount}</TableCell>
                <TableCell>{item.pullCount}</TableCell>
                <TableCell>{item.updateTime || '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
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

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repos`}>返回</Link>
        </Button>
        <h1 className="text-xl font-semibold">{repo}</h1>
      </div>
      {artifacts.isLoading && <p className="text-sm text-muted-foreground">加载制品…</p>}
      {artifacts.isError && <p className="text-sm text-destructive">制品加载失败</p>}
      {artifacts.isSuccess && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>标签</TableHead>
              <TableHead>摘要</TableHead>
              <TableHead>大小</TableHead>
              <TableHead>扫描</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {artifacts.data.map((item) => {
              const tag = item.tags[0]?.name
              return (
                <TableRow key={item.digest}>
                  <TableCell>{item.tags.map((entry) => entry.name).join(', ') || '—'}</TableCell>
                  <TableCell className="max-w-xs truncate font-mono text-xs">{item.digest}</TableCell>
                  <TableCell>{item.size}</TableCell>
                  <TableCell>
                    {item.scanOverview
                      ? `${item.scanOverview.severity || item.scanOverview.status} (${item.scanOverview.totalVulnerabilities})`
                      : '—'}
                  </TableCell>
                  <TableCell className="space-x-1 text-right">
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
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}
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
    </div>
  )
}

export { repoPath }
