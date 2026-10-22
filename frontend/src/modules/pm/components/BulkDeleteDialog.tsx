import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { pmWorkItemApi } from '@/modules/pm/api'
import type { PmWorkItem } from '@/modules/pm/types'
import { errorMessage } from '@/shared/errors/apiError'
import { asId } from '@/modules/pm/utils/id'

export interface DeleteOutcome {
  id: string
  title: string
  success: boolean
  message?: string
}

/**
 * 批量删除：后端暂无批量删除接口，逐条调用；失败项保留给调用方继续处理。
 */
export function BulkDeleteDialog({
  open,
  onOpenChange,
  items,
  onFinished,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  items: PmWorkItem[]
  onFinished?: (outcomes: DeleteOutcome[]) => void
}) {
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState(0)
  const [outcomes, setOutcomes] = useState<DeleteOutcome[]>([])

  useEffect(() => {
    if (!open) return
    setBusy(false)
    setProgress(0)
    setOutcomes([])
  }, [open])

  async function remove() {
    setBusy(true)
    const results: DeleteOutcome[] = []
    for (let index = 0; index < items.length; index += 1) {
      const item = items[index]
      try {
        await pmWorkItemApi.delete(item.id!)
        results.push({ id: asId(item.id), title: item.title, success: true })
      } catch (err) {
        results.push({
          id: asId(item.id),
          title: item.title,
          success: false,
          message: errorMessage(err, '删除失败'),
        })
      }
      setProgress(index + 1)
    }
    setOutcomes(results)
    setBusy(false)
    onFinished?.(results)
  }

  const failed = outcomes.filter((outcome) => !outcome.success).length

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>删除 {items.length} 个工作项？</DialogTitle>
          <p className="text-sm text-muted-foreground">此操作不可撤销；评论、关联与活动记录会一并移除。</p>
        </DialogHeader>
        <div className="flex flex-col gap-2 text-sm">
          {items.slice(0, 3).map((item) => (
            <span key={asId(item.id)} className="truncate text-muted-foreground">
              {item.itemKey ? `${item.itemKey} · ` : ''}{item.title}
            </span>
          ))}
          {items.length > 3 ? <span className="text-muted-foreground">等共 {items.length} 项</span> : null}
          {busy ? <p className="text-muted-foreground">正在删除 {progress}/{items.length}…</p> : null}
          {outcomes.length ? (
            <div className="rounded-md border p-2">
              <p>成功 {outcomes.length - failed} 项，失败 {failed} 项</p>
              {failed ? (
                <ul className="mt-1 max-h-32 overflow-y-auto text-xs text-destructive">
                  {outcomes.filter((outcome) => !outcome.success).map((outcome) => (
                    <li key={outcome.id}>{outcome.title}：{outcome.message}</li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>
            取消
          </Button>
          <Button type="button" variant="destructive" disabled={busy} onClick={remove}>
            删除 {items.length} 个工作项
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
