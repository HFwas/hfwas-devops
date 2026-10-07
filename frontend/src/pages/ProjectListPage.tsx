import { useEffect, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, FolderKanban, Plus, Trash2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
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
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 md:gap-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold tracking-tight">项目</h1>
            <p className="text-sm text-muted-foreground">
              {query.isSuccess ? `共 ${total} 个项目` : '选择或创建项目管理事项与配置'}
            </p>
          </div>
          <Button onClick={() => setOpen(true)}>
            <Plus />
            新建项目
          </Button>
        </div>

        {query.isLoading && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 3 }, (_, index) => (
              <div key={index} className="h-40 animate-pulse rounded-xl border bg-card" />
            ))}
          </div>
        )}
        {query.isError && (
          <Card>
            <CardHeader>
              <CardTitle>项目列表加载失败</CardTitle>
              <CardDescription>请稍后刷新页面重试。</CardDescription>
            </CardHeader>
          </Card>
        )}
        {query.isSuccess && projects.length === 0 && (
          <Card className="border-dashed">
            <CardHeader className="items-center py-12 text-center">
              <div className="mb-2 flex size-12 items-center justify-center rounded-full bg-muted">
                <FolderKanban className="size-5 text-muted-foreground" />
              </div>
              <CardTitle>还没有项目</CardTitle>
              <CardDescription>创建一个项目，开始管理事项和配置。</CardDescription>
            </CardHeader>
            <CardFooter className="justify-center pb-12">
              <Button variant="outline" onClick={() => setOpen(true)}>
                <Plus />
                新建项目
              </Button>
            </CardFooter>
          </Card>
        )}

        {projects.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {projects.map((project) => (
              <Card
                key={asId(project.id)}
                className="group flex h-full flex-col transition-shadow hover:shadow-md"
              >
                <button
                  type="button"
                  className="flex flex-1 flex-col text-left"
                  onClick={() => void navigate(`/pm/projects/${asId(project.id)}/items/task`)}
                >
                  <CardHeader className="flex-row items-start gap-3 space-y-0">
                    <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <FolderKanban className="size-5" />
                    </div>
                    <div className="min-w-0 flex-1 space-y-1.5">
                      <CardTitle className="truncate text-base">{project.name}</CardTitle>
                      <Badge variant="outline" className="font-mono">
                        {project.code}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="flex-1">
                    <p className="line-clamp-2 text-sm text-muted-foreground">
                      {project.description?.trim() || '暂无描述'}
                    </p>
                  </CardContent>
                </button>
                <CardFooter className="mt-auto justify-between border-t px-4 py-3">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => void navigate(`/pm/projects/${asId(project.id)}/items/task`)}
                  >
                    进入项目
                    <ArrowRight />
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
                </CardFooter>
              </Card>
            ))}
          </div>
        )}

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
