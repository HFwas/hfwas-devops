import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router'
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
import { pmProjectApi } from '@/modules/pm/api'
import type { PmProject } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'
import { useAuthStore } from '@/stores/auth'

const projectSchema = z.object({
  code: z.string().trim().min(1, '请填写编码'),
  name: z.string().trim().min(1, '请填写名称'),
  description: z.string().optional(),
})

type ProjectForm = z.infer<typeof projectSchema>

export function ProjectListPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  const tenantVersion = useAuthStore((s) => s.tenantVersion)
  const activeTenantId = useAuthStore((s) => s.activeTenantId)
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)

  const query = useQuery({
    queryKey: ['pm-projects', activeTenantId, tenantVersion, keyword],
    queryFn: () => pmProjectApi.page({ pageNo: 1, pageSize: 18, keyword: keyword.trim() || undefined }),
  })

  const form = useForm<ProjectForm>({
    resolver: zodResolver(projectSchema),
    defaultValues: { code: '', name: '', description: '' },
  })

  useEffect(() => {
    if (!open) form.reset({ code: '', name: '', description: '' })
  }, [form, open])

  const save = useMutation({
    mutationFn: (values: ProjectForm) => pmProjectApi.save(values),
    onSuccess: async () => {
      toast.success('项目已保存')
      setOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['pm-projects'] })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })

  const remove = useMutation({
    mutationFn: (project: PmProject) => pmProjectApi.delete(project.id!),
    onSuccess: async () => {
      toast.success('项目已删除')
      await queryClient.invalidateQueries({ queryKey: ['pm-projects'] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const projects = query.data?.records ?? []
  const total = query.data?.total ?? projects.length

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
        <PageHeader
          title="项目"
          description={query.isSuccess ? `共 ${total} 个项目` : '选择或创建项目管理事项与配置'}
          actions={
            <Button onClick={() => setOpen(true)}>
              <Plus />
              新建项目
            </Button>
          }
        />
        <DataTable
          columns={[
            {
              id: 'name',
              header: '名称',
              cell: (project) => (
                <button
                  type="button"
                  className="font-medium text-primary hover:underline"
                  onClick={() => void navigate(`/pm/projects/${asId(project.id)}/items/task`)}
                >
                  {project.name}
                </button>
              ),
            },
            {
              id: 'code',
              header: '编码',
              cell: (project) => <span className="font-mono text-xs">{project.code}</span>,
            },
            {
              id: 'description',
              header: '描述',
              className: 'max-w-md truncate text-muted-foreground',
              cell: (project) => project.description?.trim() || '—',
            },
            {
              id: 'actions',
              header: <span className="sr-only">操作</span>,
              headClassName: 'w-28',
              className: 'text-right',
              cell: (project) => (
                <div className="flex justify-end gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => void navigate(`/pm/projects/${asId(project.id)}/items/task`)}
                  >
                    进入
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-muted-foreground hover:text-destructive"
                    aria-label={`删除项目 ${project.name}`}
                    onClick={() => {
                      if (window.confirm(`确认删除项目「${project.name}」？删除后不可恢复。`)) {
                        remove.mutate(project)
                      }
                    }}
                  >
                    <Trash2 />
                  </Button>
                </div>
              ),
            },
          ]}
          data={projects}
          getRowId={(project) => asId(project.id)}
          loading={query.isFetching}
          error={query.isError ? '项目列表加载失败' : undefined}
          empty={
            <div className="flex flex-col items-center gap-3 py-4">
              <p>还没有项目。创建一个项目，开始管理事项和配置。</p>
              <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
                <Plus />
                新建项目
              </Button>
            </div>
          }
        />

        <Dialog open={open} onOpenChange={setOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>新建项目</DialogTitle>
            </DialogHeader>
            <form className="flex flex-col gap-4" onSubmit={form.handleSubmit((values) => save.mutate(values))}>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="code">编码</Label>
                <Input id="code" placeholder="例如 PO" {...form.register('code')} />
                {form.formState.errors.code && (
                  <p className="text-xs text-destructive">{form.formState.errors.code.message}</p>
                )}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="name">名称</Label>
                <Input id="name" placeholder="项目名称" {...form.register('name')} />
                {form.formState.errors.name && (
                  <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
                )}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="description">描述</Label>
                <Input id="description" placeholder="可选" {...form.register('description')} />
              </div>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                  取消
                </Button>
                <Button type="submit" disabled={save.isPending}>
                  保存
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
    </div>
  )
}
