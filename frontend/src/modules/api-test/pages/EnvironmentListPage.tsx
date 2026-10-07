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
import { environmentApi } from '@/modules/api-test/environment/api/environment'
import type { EnvironmentVO } from '@/modules/api-test/environment/types/environment'
import { ProjectSelect } from '@/modules/api-test/pages/ProjectSelect'
import { useAuthStore } from '@/stores/auth'

const schema = z.object({
  name: z.string().trim().min(1, '请填写名称'),
  description: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

export function EnvironmentListPage() {
  const userId = useAuthStore((s) => s.user?.id)
  const queryClient = useQueryClient()
  const [projectId, setProjectId] = useState('')
  const [keyword, setKeyword] = useState('')
  const [open, setOpen] = useState(false)
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', description: '' },
  })

  useEffect(() => {
    if (!open) form.reset({ name: '', description: '' })
  }, [form, open])

  const query = useQuery({
    queryKey: ['api-environments', projectId, keyword],
    queryFn: () =>
      environmentApi.page({
        projectId,
        keyword: keyword.trim() || undefined,
        pageNo: 1,
        pageSize: 50,
      }),
    enabled: !!projectId,
  })
  const create = useMutation({
    mutationFn: (values: FormValues) => environmentApi.create(values, projectId, userId!),
    onSuccess: async () => {
      toast.success('环境已创建')
      setOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['api-environments', projectId] })
    },
    onError: (error: Error) => toast.error(error.message || '创建失败'),
  })

  const rows = query.data?.records ?? []
  const total = query.data?.total ?? rows.length

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="环境"
        description={projectId && query.isSuccess ? `共 ${total} 个环境` : '按项目管理变量环境'}
        actions={
          <>
            <ProjectSelect value={projectId} onChange={setProjectId} />
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索名称"
              aria-label="搜索环境"
              className="h-8 w-56"
            />
            <Button disabled={!projectId || userId == null || userId === ''} onClick={() => setOpen(true)}>
              <Plus />
              新建环境
            </Button>
          </>
        }
      />
      <DataTable<EnvironmentVO>
        columns={[
          {
            id: 'name',
            header: '名称',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={`/api-test/environments/${row.id}`}>
                {row.name}
              </Link>
            ),
          },
          { id: 'vars', header: '变量', cell: (row) => String(row.variableCount) },
          { id: 'desc', header: '说明', className: 'max-w-sm truncate', cell: (row) => row.description || '—' },
          { id: 'updated', header: '更新时间', cell: (row) => formatTime(row.updateTime) },
        ]}
        data={rows}
        getRowId={(row) => String(row.id)}
        loading={!!projectId && query.isFetching}
        error={query.isError ? '环境列表加载失败' : undefined}
        empty={projectId ? '这个项目还没有环境' : '先选择项目'}
      />
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建环境</DialogTitle>
          </DialogHeader>
          <form className="flex flex-col gap-3" onSubmit={form.handleSubmit((values) => create.mutate(values))}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="env-name">名称</Label>
              <Input id="env-name" className="h-8" {...form.register('name')} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="env-desc">说明</Label>
              <Input id="env-desc" className="h-8" {...form.register('description')} />
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
