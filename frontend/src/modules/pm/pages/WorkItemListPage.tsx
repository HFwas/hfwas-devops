import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
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
import { pmProjectApi, pmProjectIssueTypeApi, pmStatusApi, pmWorkItemApi } from '@/modules/pm/api'
import { priorityLabel, workflowStatusTone } from '@/modules/pm/statusTone'
import { typeLabel } from '@/modules/pm/types'
import type { PmWorkItem, StatusDefinition } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'
import { resolveRouteProjectId } from '@/modules/pm/utils/projectRoute'

const createSchema = z.object({
  title: z.string().trim().min(1, '请填写标题'),
})

type CreateForm = z.infer<typeof createSchema>

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

export function WorkItemListPage() {
  const { projectId: projectParam = '', typeCode = 'task' } = useParams()
  const projectId = resolveRouteProjectId(projectParam)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [open, setOpen] = useState(false)
  const form = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { title: '' },
  })

  useEffect(() => {
    if (!open) form.reset({ title: '' })
  }, [form, open])

  const projectQuery = useQuery({
    queryKey: ['pm-project', projectId],
    queryFn: () => pmProjectApi.getById(projectId!),
    enabled: !!projectId,
  })
  const typesQuery = useQuery({
    queryKey: ['pm-issue-types', projectId],
    queryFn: () => pmProjectIssueTypeApi.list(projectId!),
    enabled: !!projectId,
  })
  const statusQuery = useQuery({
    queryKey: ['pm-status-options', projectId, typeCode],
    queryFn: () => pmStatusApi.options(projectId!, typeCode),
    enabled: !!projectId && !!typeCode,
  })
  const itemsQuery = useQuery({
    queryKey: ['pm-work-items', projectId, typeCode, keyword],
    queryFn: () =>
      pmWorkItemApi.page({
        projectId: projectId!,
        typeCode,
        pageNo: 1,
        pageSize: 50,
        logic: 'AND',
        conditions: keyword.trim()
          ? [{ field: 'title', operator: 'LIKE', value: keyword.trim() }]
          : undefined,
        sort: [{ field: 'update_time', order: 'DESC' }],
      }),
    enabled: !!projectId,
  })

  const create = useMutation({
    mutationFn: (values: CreateForm) =>
      pmWorkItemApi.save({ projectId: projectId!, typeCode, title: values.title }),
    onSuccess: async () => {
      toast.success('事项已创建')
      setOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['pm-work-items', projectId, typeCode] })
    },
    onError: (error: Error) => toast.error(error.message || '创建失败'),
  })

  if (!projectId) {
    return <p className="text-sm text-destructive">项目地址无效</p>
  }

  const statuses = new Map((statusQuery.data ?? []).map((item) => [item.statusCode, item]))
  const types = (typesQuery.data ?? []).filter((item) => item.enabled !== 0)
  const rows = itemsQuery.data?.records ?? []
  const total = itemsQuery.data?.total ?? rows.length
  const projectName = projectQuery.data?.name

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title={projectName ? `${projectName} · ${typeLabel(typeCode, types)}` : typeLabel(typeCode, types)}
        description={itemsQuery.isSuccess ? `共 ${total} 条事项` : '按类型查看项目事项'}
        actions={
          <>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索标题"
              aria-label="搜索事项"
              className="h-8 w-56"
            />
            <Button onClick={() => setOpen(true)}>
              <Plus />
              新建事项
            </Button>
          </>
        }
      />
      {types.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {types.map((type) => {
            const selected = type.code === typeCode
            return (
              <Button key={type.code} variant={selected ? 'secondary' : 'ghost'} size="sm" asChild>
                <Link to={`/pm/projects/${projectId}/items/${type.code}`}>{type.name || type.code}</Link>
              </Button>
            )
          })}
        </div>
      )}
      <DataTable<PmWorkItem>
        columns={[
          {
            id: 'key',
            header: '编号',
            cell: (item) => <span className="font-mono text-xs">{item.itemKey || '—'}</span>,
          },
          {
            id: 'title',
            header: '标题',
            cell: (item) => (
              <button
                type="button"
                className="font-medium text-primary hover:underline"
                onClick={() => void navigate(`/pm/projects/${projectId}/items/${typeCode}/${asId(item.id)}`)}
              >
                {item.title}
              </button>
            ),
          },
          {
            id: 'status',
            header: '状态',
            cell: (item) => (
              <WorkItemStatus status={item.status} definition={statuses.get(item.status ?? '')} />
            ),
          },
          {
            id: 'priority',
            header: '优先级',
            cell: (item) => priorityLabel(item.priority),
          },
          {
            id: 'updated',
            header: '更新时间',
            cell: (item) => formatTime(item.updateTime),
          },
        ]}
        data={rows}
        getRowId={(item) => asId(item.id)}
        loading={itemsQuery.isFetching}
        error={itemsQuery.isError ? '事项列表加载失败' : undefined}
        empty="这个类型下还没有事项"
      />
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建{typeLabel(typeCode, types)}</DialogTitle>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={form.handleSubmit((values) => create.mutate(values))}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="title">标题</Label>
              <Input id="title" placeholder="简要说明" {...form.register('title')} />
              {form.formState.errors.title && (
                <p className="text-xs text-destructive">{form.formState.errors.title.message}</p>
              )}
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                取消
              </Button>
              <Button type="submit" disabled={create.isPending}>
                创建
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function WorkItemStatus({
  status,
  definition,
}: {
  status?: string
  definition?: StatusDefinition
}) {
  return (
    <StatusIcon
      status={status}
      label={definition?.statusName || status || '未设置'}
      tone={workflowStatusTone(definition)}
    />
  )
}
