import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { pmWorkItemApi } from '@/modules/pm/api'
import type { BatchOperationResult, PmWorkItem, StatusDefinition } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'
import { errorMessage } from '@/shared/errors/apiError'
import { BatchResultList } from '@/modules/pm/components/BatchResultList'

/**
 * 批量流转：目标状态为所选事项类型的并集；不可流转的记录由后端返回 SKIPPED 并展示原因。
 */
export function TransitionDialog({
  open,
  onOpenChange,
  items,
  statuses,
  onFinished,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  items: PmWorkItem[]
  statuses: StatusDefinition[]
  onFinished?: (result: BatchOperationResult) => void
}) {
  const [toStatus, setToStatus] = useState('')
  const [result, setResult] = useState<BatchOperationResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const commonStatus = items.length > 0 && items.every((item) => item.status === items[0]?.status)
    ? items[0]?.status
    : undefined
  const targetStatuses = statuses.filter((status) => status.statusCode !== commonStatus)

  useEffect(() => {
    if (!open) return
    setToStatus(targetStatuses[0]?.statusCode ?? '')
    setResult(null)
    setError('')
  }, [open, statuses, commonStatus])

  const titles = useMemo(
    () => new Map(items.map((item) => [asId(item.id), item.title])),
    [items],
  )

  async function run(dryRun: boolean) {
    if (!toStatus) {
      setError('请选择目标状态')
      return
    }
    setBusy(true)
    setError('')
    try {
      const versions: Record<string, number> = {}
      for (const item of items) {
        const version = item.version != null ? Number(item.version) : NaN
      if (Number.isFinite(version) && item.id != null) versions[asId(item.id)] = version
      }
      const next = await pmWorkItemApi.batchTransition({
        ids: items.map((item) => asId(item.id)),
        toStatus,
        ...(Object.keys(versions).length ? { versions } : {}),
        dryRun,
      })
      setResult(next)
      if (!dryRun) onFinished?.(next)
    } catch (err) {
      setError(errorMessage(err, '批量流转失败'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>状态流转 {items.length} 个工作项</DialogTitle>
          <p className="text-sm text-muted-foreground">只展示合法目标状态；不可流转的记录会跳过并给出原因。</p>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label>目标状态</Label>
            <Select value={toStatus} onValueChange={setToStatus}>
              <SelectTrigger><SelectValue placeholder="选择目标状态" /></SelectTrigger>
              <SelectContent>
                {targetStatuses.map((status) => (
                  <SelectItem key={status.statusCode} value={status.statusCode}>
                    {status.statusName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
          {result ? <BatchResultList result={result} titles={titles} /> : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button type="button" variant="outline" disabled={busy} onClick={() => run(true)}>影响预览</Button>
          <Button type="button" disabled={busy} onClick={() => run(false)}>执行流转</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
