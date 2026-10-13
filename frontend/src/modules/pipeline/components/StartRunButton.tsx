import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Play } from 'lucide-react'
import { toast } from 'sonner'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { paramChoices } from '@/modules/pipeline/pipelineDraft'
import type { EntityId, RunParamValue } from '@/modules/pipeline/types/pipeline'

/** Radix Select 不接受空字符串；用哨兵代表「请选择」。 */
const NONE = '__none__'

export function StartRunButton({
  pipelineId,
  label = '运行',
  variant = 'outline',
  resolvePipelineId,
  disabled,
}: {
  pipelineId?: EntityId
  label?: string
  variant?: 'outline' | 'default' | 'secondary'
  /** 保存后再运行。返回空表示校验未通过。 */
  resolvePipelineId?: () => Promise<EntityId | null>
  disabled?: boolean
}) {
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const [open, setOpen] = useState(false)
  const [runId, setRunId] = useState<EntityId | null>(null)
  const [defs, setDefs] = useState<RunParamValue[]>([])
  const [values, setValues] = useState<Record<string, string>>({})

  async function launch(id: EntityId, params?: Record<string, string>) {
    const run = await pipelineApi.start(id, params)
    toast.success('已开始运行')
    await navigate(`/pipeline/pipelines/${id}/runs/${run.id}`)
  }

  async function onClick() {
    setPending(true)
    try {
      const id = resolvePipelineId ? await resolvePipelineId() : pipelineId
      if (id == null || id === '') return
      const params = (await pipelineApi.getDefaultParams(id)) ?? []
      if (params.length > 0) {
        setRunId(id)
        setDefs(params)
        setValues(Object.fromEntries(params.map((item) => [item.paramKey, item.defaultValue ?? ''])))
        setOpen(true)
        return
      }
      await launch(id)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '运行失败')
    } finally {
      setPending(false)
    }
  }

  async function confirm() {
    if (runId == null) return
    for (const param of defs) {
      if (param.required && !values[param.paramKey]?.trim()) {
        toast.error(`请填写${param.paramLabel || param.paramKey}`)
        return
      }
    }
    setPending(true)
    try {
      setOpen(false)
      await launch(runId, values)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '运行失败')
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <Button type="button" variant={variant} size="sm" disabled={disabled || pending} onClick={() => void onClick()}>
        <Play />
        {label}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>运行参数</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            {defs.map((param) => {
              const choices = paramChoices(param.options)
              const value = values[param.paramKey] ?? ''
              return (
                <div key={param.paramKey} className="flex flex-col gap-1.5">
                  <Label htmlFor={`run-param-${param.paramKey}`}>
                    {param.paramLabel || param.paramKey}
                    {param.required ? ' *' : ''}
                  </Label>
                  {param.paramType === 'select' && choices.length > 0 ? (
                    <Select
                      value={value === '' ? NONE : value}
                      onValueChange={(next) =>
                        setValues((current) => ({ ...current, [param.paramKey]: next === NONE ? '' : next }))
                      }
                    >
                      <SelectTrigger id={`run-param-${param.paramKey}`} aria-label={param.paramLabel || param.paramKey} className="w-full">
                        <SelectValue placeholder="请选择" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>请选择</SelectItem>
                        {choices.map((choice) => (
                          <SelectItem key={choice.value} value={choice.value}>
                            {choice.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : (
                    <Input
                      id={`run-param-${param.paramKey}`}
                      value={value}
                      placeholder={param.placeholder || undefined}
                      onChange={(event) => setValues((current) => ({ ...current, [param.paramKey]: event.target.value }))}
                    />
                  )}
                </div>
              )
            })}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" size="sm" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button type="button" size="sm" disabled={pending} onClick={() => void confirm()}>
              运行
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}