import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { Pagination } from '@/components/console/Pagination'
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
import { formatDateTime } from '@/modules/pm/utils/format'
import { asId } from '@/modules/pm/utils/id'
import { resolveRouteProjectId } from '@/modules/pm/utils/projectRoute'
import { DEFAULT_PAGE_SIZE, pageCountOf } from '@/shared/types/pagination'

const createSchema = z.object({
  title: z.string().trim().min(1, '请填写标题'),
})

type CreateForm = z.infer<typeof createSchema>

export function WorkItemListPage() {
  const { projectId: projectParam = '', typeCode = 'task' } = useParams()
  const projectId = resolveRouteProjectId(projectParam)
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [open, setOpen] = useState(false)
  const form = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { title: '' },
  })

  useEffect(() => {
    if (!open) form.reset({ title: '' })
  }, [form, open])

  useEffect(() => {
    setPage(1)
  }, [keyword, typeCode, projectId])

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
    queryKey: ['pm-work-items', projectId, typeCode, keyword, page],
    queryFn: () =>
      pmWorkItemApi.page({
        projectId: projectId!,
        typeCode,
        pageNo: page,
        pageSize: DEFAULT_PAGE_SIZE,
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
  const pageCount = pageCountOf(itemsQuery.data, DEFAULT_PAGE_SIZE)
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
              <Link
                className="font-medium text-primary hover:underline"
                to={`/pm/projects/${projectId}/items/${typeCode}/${asId(item.id)}`}
              >
                {item.title}
              </Link>
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
            cell: (item) => formatDateTime(item.updateTime),
          },
        ]}
        data={rows}
        getRowId={(item) => asId(item.id)}
        loading={itemsQuery.isFetching}
        error={itemsQuery.isError ? '事项列表加载失败' : undefined}
        empty="这个类型下还没有事项"
      />
      <Pagination page={Math.min(page, pageCount)} pageCount={pageCount} onPageChange={setPage} />
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
