import { useEffect } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { DetailShell } from '@/components/console/DetailShell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import { formatDateTime } from '@/modules/pipeline/status'
import type { CredentialKind } from '@/modules/pipeline/types/pipeline'

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

export function CredentialDetailPage() {
  const { credentialId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['pipeline-credential', credentialId],
    queryFn: () => pipelineCredentialApi.get(credentialId),
    enabled: !!credentialId,
  })
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', kind: 'PASSWORD', username: '', secret: '' },
  })

  useEffect(() => {
    if (!query.data) return
    form.reset({
      name: query.data.name,
      kind: (query.data.kind as CredentialKind) || 'PASSWORD',
      username: query.data.username || '',
      secret: '',
    })
  }, [form, query.data])

  const save = useMutation({
    mutationFn: (values: FormValues) =>
      pipelineCredentialApi.save({
        id: credentialId,
        name: values.name,
        kind: values.kind,
        username: values.username || undefined,
        secret: values.secret || undefined,
      }),
    onSuccess: async () => {
      toast.success('凭证已更新')
      await queryClient.invalidateQueries({ queryKey: ['pipeline-credential', credentialId] })
      await queryClient.invalidateQueries({ queryKey: ['pipeline-credentials'] })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })
  const remove = useMutation({
    mutationFn: () => pipelineCredentialApi.delete(credentialId),
    onSuccess: async () => {
      toast.success('凭证已删除')
      await queryClient.invalidateQueries({ queryKey: ['pipeline-credentials'] })
      void navigate('/pipeline/credentials')
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
  const item = query.data

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/pipeline/credentials">返回</Link>
        </Button>
      }
      title={item?.name || '凭证'}
      description={item ? KIND_LABEL[item.kind] ?? item.kind : undefined}
      meta={item ? formatDateTime(item.updateTime) : query.isLoading ? '加载中…' : query.isError ? '加载失败' : null}
      actions={
        <Button
          variant="outline"
          size="sm"
          className="text-destructive"
          onClick={() => {
            if (window.confirm(`删除凭证「${item?.name || credentialId}」？`)) remove.mutate()
          }}
        >
          删除
        </Button>
      }
    >
      {query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {query.isError && <p className="text-sm text-destructive">凭证加载失败</p>}
      {item && (
        <form className="grid max-w-lg gap-3" onSubmit={form.handleSubmit((values) => save.mutate(values))}>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="cred-name">名称</Label>
            <Input id="cred-name" className="h-8" {...form.register('name')} />
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
            <Input id="cred-user" className="h-8" {...form.register('username')} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="cred-secret">新密钥</Label>
            <Input
              id="cred-secret"
              type="password"
              autoComplete="new-password"
              placeholder="留空则不修改"
              className="h-8"
              {...form.register('secret')}
            />
          </div>
          <div>
            <Button type="submit" size="sm" disabled={save.isPending}>
              保存
            </Button>
          </div>
        </form>
      )}
    </DetailShell>
  )
}
