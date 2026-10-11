import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
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
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { initialRunParamValues, missingRunParam } from '@/modules/pipeline/runParams'
import type { RunParamValue } from '@/modules/pipeline/types/pipeline'

const controlClass =
  'flex h-8 w-full rounded-md border border-input bg-background px-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring'

export function StartRunButton({
  pipelineId,
  label = '运行',
  variant = 'default',
}: {
  pipelineId: string
  label?: string
  variant?: 'default' | 'outline' | 'ghost'
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [pending, setPending] = useState(false)
  const [open, setOpen] = useState(false)
  const [defs, setDefs] = useState<RunParamValue[]>([])
  const [values, setValues] = useState<Record<string, string>>({})

  async function begin() {
    setPending(true)
    try {
      const params = await pipelineApi.getDefaultParams(pipelineId)
      if (params.length === 0) {
        await launch({})
        return
      }
      setDefs(params)
      setValues(initialRunParamValues(params))
      setOpen(true)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '读取运行参数失败')
    } finally {
      setPending(false)
    }
  }

  async function confirm() {
    const missing = missingRunParam(defs, values)
    if (missing) {
      toast.error(missing)
      return
    }
    setPending(true)
    try {
      await launch(values)
      setOpen(false)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '启动失败')
    } finally {
      setPending(false)
    }
  }

  async function launch(params: Record<string, string>) {
    const run = await pipelineApi.start(pipelineId, Object.keys(params).length > 0 ? params : undefined)
    toast.success('已开始运行')
    await queryClient.invalidateQueries({ queryKey: ['pipeline-runs', pipelineId] })
    await queryClient.invalidateQueries({ queryKey: ['pipelines'] })
    void navigate(`/pipeline/pipelines/${pipelineId}/runs/${run.id}`)
  }

  return (
    <>
      <Button type="button" size="sm" variant={variant} disabled={pending || !pipelineId} onClick={() => void begin()}>
        {label}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>运行参数</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">这些参数在流水线里设成了运行时填写。确认后进入本次运行详情。</p>
          <div className="flex flex-col gap-3">
            {defs.map((def) => (
              <div key={def.paramKey} className="flex flex-col gap-1.5">
                <Label htmlFor={`run-param-${def.paramKey}`}>
                  {def.paramLabel || def.paramKey}
                  {def.required ? ' *' : ''}
                </Label>
                <ParamControl
                  def={def}
                  value={values[def.paramKey] ?? ''}
                  onChange={(value) => setValues((current) => ({ ...current, [def.paramKey]: value }))}
                />
              </div>
            ))}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button type="button" disabled={pending} onClick={() => void confirm()}>
              开始运行
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function ParamControl({
  def,
  value,
  onChange,
}: {
  def: RunParamValue
  value: string
  onChange: (value: string) => void
}) {
  const options = def.options ?? []
  if ((def.paramType === 'select' || def.paramType === 'api_select') && options.length > 0) {
    return (
      <select
        id={`run-param-${def.paramKey}`}
        className={controlClass}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">请选择</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    )
  }
  return (
    <Input
      id={`run-param-${def.paramKey}`}
      className="h-8"
      placeholder={def.placeholder || undefined}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}
