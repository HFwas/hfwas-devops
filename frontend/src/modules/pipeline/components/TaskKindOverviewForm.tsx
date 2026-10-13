import { type ReactNode } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { overviewFromKind } from '@/modules/pipeline/taskKindView'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'

const schema = z.object({
  label: z.string().trim().min(1, '请填写名称'),
  taskGroup: z.string().trim().min(1, '请填写分组'),
  description: z.string(),
  hint: z.string(),
  toolImage: z.string(),
  commandTemplate: z.string(),
  sortOrder: z.coerce.number().int('排序须为整数'),
  cpuRequest: z.string(),
  cpuLimit: z.string(),
  memoryRequest: z.string(),
  memoryLimit: z.string(),
})

type FormValues = z.output<typeof schema>

export function TaskKindOverviewForm({
  item,
  saving,
  onSave,
  onCancel,
}: {
  item: TaskKindVO
  saving: boolean
  onSave: (values: FormValues) => void
  onCancel: () => void
}) {
  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues: overviewFromKind(item),
  })

  return (
    <form className="flex max-w-3xl flex-col gap-4" onSubmit={form.handleSubmit(onSave)}>
      <p className="text-sm text-muted-foreground">保存只更新当前配置，不会发布新版本。</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField id="task-kind-label" label="名称" error={form.formState.errors.label?.message}>
          <Input id="task-kind-label" className="h-8" {...form.register('label')} />
        </TextField>
        <TextField id="task-kind-group" label="分组" error={form.formState.errors.taskGroup?.message}>
          <Input id="task-kind-group" className="h-8" {...form.register('taskGroup')} />
        </TextField>
        <TextField id="task-kind-sort" label="排序" error={form.formState.errors.sortOrder?.message}>
          <Input id="task-kind-sort" className="h-8" inputMode="numeric" {...form.register('sortOrder')} />
        </TextField>
        <TextField id="task-kind-image" label="镜像" error={form.formState.errors.toolImage?.message}>
          <Input id="task-kind-image" className="h-8 font-mono text-xs" {...form.register('toolImage')} />
        </TextField>
      </div>
      <TextField id="task-kind-description" label="描述" error={form.formState.errors.description?.message}>
        <Textarea id="task-kind-description" className="min-h-20" {...form.register('description')} />
      </TextField>
      <TextField id="task-kind-hint" label="提示" error={form.formState.errors.hint?.message}>
        <Textarea id="task-kind-hint" className="min-h-20" {...form.register('hint')} />
      </TextField>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <TextField id="task-kind-cpu-request" label="CPU 请求">
          <Input id="task-kind-cpu-request" className="h-8" placeholder="500m" {...form.register('cpuRequest')} />
        </TextField>
        <TextField id="task-kind-cpu-limit" label="CPU 上限">
          <Input id="task-kind-cpu-limit" className="h-8" placeholder="1" {...form.register('cpuLimit')} />
        </TextField>
        <TextField id="task-kind-memory-request" label="内存请求">
          <Input id="task-kind-memory-request" className="h-8" placeholder="512Mi" {...form.register('memoryRequest')} />
        </TextField>
        <TextField id="task-kind-memory-limit" label="内存上限">
          <Input id="task-kind-memory-limit" className="h-8" placeholder="1Gi" {...form.register('memoryLimit')} />
        </TextField>
      </div>
      <TextField id="task-kind-command" label="命令模板">
        <Textarea
          id="task-kind-command"
          className="min-h-36 font-mono text-xs"
          {...form.register('commandTemplate')}
        />
      </TextField>
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={saving}>
          保存
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={onCancel}>
          取消
        </Button>
      </div>
    </form>
  )
}

function TextField({
  id,
  label,
  error,
  children,
}: {
  id: string
  label: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  )
}
