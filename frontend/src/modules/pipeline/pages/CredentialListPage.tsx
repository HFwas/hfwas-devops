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
import { pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import { formatDateTime } from '@/modules/pipeline/status'
import type { CredentialKind, PipelineCredential } from '@/modules/pipeline/types/pipeline'
import { asId } from '@/modules/pm/utils/id'

const KIND_LABEL: Record<string, string> = {
  PASSWORD: '用户名密码',
  TOKEN: '令牌',
  KUBECONFIG: 'Kubeconfig',
}

const schema = z.object({
  name: z.string().trim().min(1, '请填写名称'),
  kind: z.enum(['PASSWORD', 'TOKEN', 'KUBECONFIG']),
  username: z.string().optional(),
  secret: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

export function CredentialListPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['pipeline-credentials'],
    queryFn: () => pipelineCredentialApi.list(),
  })
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', kind: 'PASSWORD', username: '', secret: '' },
  })

  useEffect(() => {
    if (!open) form.reset({ name: '', kind: 'PASSWORD', username: '', secret: '' })
  }, [form, open])

  const create = useMutation({
    mutationFn: (values: FormValues) =>
      pipelineCredentialApi.save({
        name: values.name,
        kind: values.kind,
        username: values.username || undefined,
        secret: values.secret || undefined,
      }),
    onSuccess: async () => {
      toast.success('凭证已保存')
      setOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['pipeline-credentials'] })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })
  const remove = useMutation({
    mutationFn: (id: PipelineCredential['id']) => pipelineCredentialApi.delete(id),
    onSuccess: async () => {
      toast.success('凭证已删除')
      await queryClient.invalidateQueries({ queryKey: ['pipeline-credentials'] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const needle = keyword.trim().toLowerCase()
  const rows = (query.data ?? []).filter((item) => !needle || item.name.toLowerCase().includes(needle))

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="凭证"
        description={query.isSuccess ? `共 ${rows.length} 条凭证` : '仓库拉取与集群访问使用的凭据'}
        actions={
          <>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索名称"
              aria-label="搜索凭证"
              className="h-8 w-56"
            />
            <Button onClick={() => setOpen(true)}>
              <Plus />
              新建凭证
            </Button>
          </>
        }
      />
      <DataTable<PipelineCredential>
        columns={[
          {
            id: 'name',
            header: '名称',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={`/pipeline/credentials/${asId(row.id)}`}>
                {row.name}
              </Link>
            ),
          },
          { id: 'kind', header: '类型', cell: (row) => KIND_LABEL[row.kind] ?? row.kind },
          { id: 'username', header: '用户名', cell: (row) => row.username || '—' },
          { id: 'updated', header: '更新时间', cell: (row) => formatDateTime(row.updateTime) },
          {
            id: 'actions',
            header: '',
            className: 'text-right',
            cell: (row) => (
              <Button
                variant="ghost"
                size="sm"
                className="text-destructive"
                onClick={() => {
                  if (window.confirm(`删除凭证「${row.name}」？`)) remove.mutate(row.id)
                }}
              >
                删除
              </Button>
            ),
          },
        ]}
        data={rows}
        getRowId={(row) => asId(row.id)}
        loading={query.isFetching}
        error={query.isError ? '凭证列表加载失败' : undefined}
        empty="还没有凭证"
      />
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建凭证</DialogTitle>
          </DialogHeader>
          <form className="flex flex-col gap-3" onSubmit={form.handleSubmit((values) => create.mutate(values))}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cred-name">名称</Label>
              <Input id="cred-name" {...form.register('name')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cred-kind">类型</Label>
              <select
                id="cred-kind"
                className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                {...form.register('kind')}
              >
                {(Object.keys(KIND_LABEL) as CredentialKind[]).map((kind) => (
                  <option key={kind} value={kind}>
                    {KIND_LABEL[kind]}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cred-user">用户名</Label>
              <Input id="cred-user" {...form.register('username')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cred-secret">密钥</Label>
              <Input id="cred-secret" type="password" autoComplete="new-password" {...form.register('secret')} />
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
