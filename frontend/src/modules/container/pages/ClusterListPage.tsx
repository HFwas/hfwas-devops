import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { DataTable } from '@/components/console/DataTable'
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
import { StatusBadge } from '@/modules/container/components/StatusBadge'
import { useContainerCluster } from '@/modules/container/clusterStore'

const schema = z.object({
  name: z.string().trim().min(1, '请填写名称'),
  alias: z.string().optional(),
  kubeconfig: z.string().trim().min(1, '请粘贴 kubeconfig'),
  mode: z.enum(['proxy', 'direct']),
})

type FormValues = z.infer<typeof schema>

export function ClusterListPage() {
  const queryClient = useQueryClient()
  const setCurrentId = useContainerCluster((s) => s.setCurrentId)
  const [open, setOpen] = useState(false)
  const query = useQuery({
    queryKey: ['container-clusters'],
    queryFn: () => clusterApi.page({ pageNo: 1, pageSize: 100 }),
  })
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', alias: '', kubeconfig: '', mode: 'proxy' },
  })

  useEffect(() => {
    if (!open) form.reset({ name: '', alias: '', kubeconfig: '', mode: 'proxy' })
  }, [form, open])

  const create = useMutation({
    mutationFn: (values: FormValues) =>
      clusterApi.create({
        name: values.name,
        alias: values.alias || undefined,
        kubeconfig: values.kubeconfig,
        mode: values.mode,
      }),
    onSuccess: async () => {
      toast.success('集群已接入')
      setOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['container-clusters'] })
    },
    onError: (error: Error) => toast.error(error.message || '接入失败'),
  })

  const remove = useMutation({
    mutationFn: (id: string) => clusterApi.delete(id),
    onSuccess: async () => {
      toast.success('集群已删除')
      await queryClient.invalidateQueries({ queryKey: ['container-clusters'] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const test = useMutation({
    mutationFn: (id: string) => clusterApi.test(id),
    onSuccess: async (ok) => {
      toast.success(ok ? '连接成功' : '连接失败')
      await queryClient.invalidateQueries({ queryKey: ['container-clusters'] })
    },
    onError: (error: Error) => toast.error(error.message || '测试失败'),
  })

  const records = query.data?.records ?? []

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="集群纳管"
        description="接入 kubeconfig，查看节点和工作负载。"
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus />
            接入集群
          </Button>
        }
      />
      <DataTable
        columns={[
          {
            id: 'name',
            header: '名称',
            cell: (cluster) => (
              <Link
                className="font-medium text-primary hover:underline"
                to={`/container/clusters/${cluster.id}`}
                onClick={() => setCurrentId(cluster.id)}
              >
                {cluster.name}
              </Link>
            ),
          },
          { id: 'alias', header: '别名', cell: (cluster) => cluster.alias || '—' },
          {
            id: 'status',
            header: '状态',
            cell: (cluster) => <StatusBadge status={cluster.status} />,
          },
          { id: 'version', header: '版本', cell: (cluster) => cluster.version || '—' },
          { id: 'nodes', header: '节点', cell: (cluster) => cluster.nodeCount ?? '—' },
          { id: 'pods', header: 'Pod', cell: (cluster) => cluster.podCount ?? '—' },
          {
            id: 'actions',
            header: <span className="sr-only">操作</span>,
            className: 'text-right',
            cell: (cluster) => (
              <div className="flex justify-end gap-1">
                <Button variant="ghost" size="sm" onClick={() => test.mutate(cluster.id)}>
                  测试
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-destructive"
                  onClick={() => {
                    if (window.confirm(`删除集群「${cluster.alias || cluster.name}」？`)) {
                      remove.mutate(cluster.id)
                    }
                  }}
                >
                  删除
                </Button>
              </div>
            ),
          },
        ]}
        data={records}
        getRowId={(cluster) => cluster.id}
        loading={query.isFetching}
        error={query.isError ? '集群列表加载失败' : undefined}
        empty="还没有集群"
      />

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>接入集群</DialogTitle>
          </DialogHeader>
          <form className="flex flex-col gap-3" onSubmit={form.handleSubmit((values) => create.mutate(values))}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cluster-name">名称</Label>
              <Input id="cluster-name" {...form.register('name')} />
              {form.formState.errors.name && (
                <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cluster-alias">别名</Label>
              <Input id="cluster-alias" {...form.register('alias')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cluster-mode">模式</Label>
              <select
                id="cluster-mode"
                className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                {...form.register('mode')}
              >
                <option value="proxy">proxy</option>
                <option value="direct">direct</option>
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cluster-kubeconfig">kubeconfig</Label>
              <textarea
                id="cluster-kubeconfig"
                rows={8}
                className="rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
                {...form.register('kubeconfig')}
              />
              {form.formState.errors.kubeconfig && (
                <p className="text-xs text-destructive">{form.formState.errors.kubeconfig.message}</p>
              )}
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                取消
              </Button>
              <Button type="submit" disabled={create.isPending}>
                保存
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
