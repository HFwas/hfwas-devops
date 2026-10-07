import { useState } from 'react'
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
import { registryApi } from '@/modules/container/api/registry'
import { StatusBadge } from '@/modules/container/components/StatusBadge'

const schema = z.object({
  name: z.string().trim().min(1, '请填写名称'),
  url: z.string().trim().min(1, '请填写地址'),
  type: z.enum(['harbor', 'registry_v2']),
  credentialUsername: z.string().optional(),
  credentialPassword: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

export function RegistryListPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const query = useQuery({
    queryKey: ['container-registries'],
    queryFn: () => registryApi.page({ pageNo: 1, pageSize: 100 }),
  })
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', url: '', type: 'harbor', credentialUsername: '', credentialPassword: '' },
  })

  const create = useMutation({
    mutationFn: (values: FormValues) =>
      registryApi.create({
        name: values.name,
        url: values.url,
        type: values.type,
        credentialUsername: values.credentialUsername || undefined,
        credentialPassword: values.credentialPassword || undefined,
      }),
    onSuccess: async () => {
      toast.success('仓库已添加')
      setOpen(false)
      form.reset()
      await queryClient.invalidateQueries({ queryKey: ['container-registries'] })
    },
    onError: (error: Error) => toast.error(error.message || '添加失败'),
  })

  const remove = useMutation({
    mutationFn: (id: string) => registryApi.delete(id),
    onSuccess: async () => {
      toast.success('仓库已删除')
      await queryClient.invalidateQueries({ queryKey: ['container-registries'] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const test = useMutation({
    mutationFn: (id: string) => registryApi.test(id),
    onSuccess: async (ok) => {
      toast.success(ok ? '连接成功' : '连接失败')
      await queryClient.invalidateQueries({ queryKey: ['container-registries'] })
    },
    onError: (error: Error) => toast.error(error.message || '测试失败'),
  })

  const records = query.data?.records ?? []

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="镜像仓库"
        description={query.isSuccess ? `共 ${query.data?.total ?? records.length} 个仓库` : '接入 Harbor 或 Registry，供镜像检索使用。'}
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus />
            添加仓库
          </Button>
        }
      />
      <DataTable
        columns={[
          {
            id: 'name',
            header: '名称',
            cell: (item) => (
              <Link className="font-medium text-primary hover:underline" to={`/container/registries/${item.id}`}>
                {item.alias || item.name}
              </Link>
            ),
          },
          { id: 'type', header: '类型', cell: (item) => item.type },
          { id: 'url', header: '地址', className: 'max-w-xs truncate', cell: (item) => item.url },
          { id: 'status', header: '状态', cell: (item) => <StatusBadge status={item.status} /> },
          {
            id: 'actions',
            header: '',
            className: 'text-right',
            cell: (item) => (
              <div className="space-x-1">
                <Button variant="ghost" size="sm" onClick={() => test.mutate(item.id)}>
                  测试
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-destructive"
                  onClick={() => {
                    if (window.confirm(`删除仓库「${item.name}」？`)) remove.mutate(item.id)
                  }}
                >
                  删除
                </Button>
              </div>
            ),
          },
        ]}
        data={records}
        getRowId={(item) => item.id}
        loading={query.isFetching}
        error={query.isError ? '仓库列表加载失败' : undefined}
        empty="还没有镜像仓库"
      />
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>添加仓库</DialogTitle>
          </DialogHeader>
          <form className="flex flex-col gap-3" onSubmit={form.handleSubmit((values) => create.mutate(values))}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reg-name">名称</Label>
              <Input id="reg-name" {...form.register('name')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reg-url">地址</Label>
              <Input id="reg-url" placeholder="https://harbor.example.com" {...form.register('url')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reg-type">类型</Label>
              <select id="reg-type" className="h-9 rounded-md border border-input bg-background px-3 text-sm" {...form.register('type')}>
                <option value="harbor">harbor</option>
                <option value="registry_v2">registry_v2</option>
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reg-user">用户名</Label>
              <Input id="reg-user" {...form.register('credentialUsername')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reg-pass">密码</Label>
              <Input id="reg-pass" type="password" {...form.register('credentialPassword')} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>取消</Button>
              <Button type="submit" disabled={create.isPending}>保存</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
