import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">镜像仓库</h1>
          <p className="text-sm text-muted-foreground">接入 Harbor 或 Registry，供镜像检索使用。</p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus />
          添加仓库
        </Button>
      </div>
      {query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {query.isError && <p className="text-sm text-destructive">仓库列表加载失败</p>}
      {query.isSuccess && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>类型</TableHead>
              <TableHead>地址</TableHead>
              <TableHead>状态</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {records.map((item) => (
              <TableRow key={item.id}>
                <TableCell className="font-medium">
                  <Link className="text-primary hover:underline" to={`/container/registries/${item.id}`}>
                    {item.alias || item.name}
                  </Link>
                </TableCell>
                <TableCell>{item.type}</TableCell>
                <TableCell className="max-w-xs truncate">{item.url}</TableCell>
                <TableCell>
                  <StatusBadge status={item.status} />
                </TableCell>
                <TableCell className="space-x-1 text-right">
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
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
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
