import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { PmViewConfig } from '@/modules/pm/types'

/**
 * 保存视图：保存当前显示列、搜索字段、筛选字段与排序。
 * 与原型画板 49 对齐：名称必填，共享范围本版仅「仅自己可见」。
 */
export function SaveViewDialog({
  open,
  onOpenChange,
  defaultName,
  config,
  saving = false,
  onSave,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  defaultName: string
  config: PmViewConfig
  saving?: boolean
  onSave: (name: string) => void
}) {
  const [name, setName] = useState(defaultName)
  const [touched, setTouched] = useState(false)

  useEffect(() => {
    if (open) {
      setName(defaultName)
      setTouched(false)
    }
  }, [open, defaultName])

  const invalid = touched && !name.trim()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[520px]">
        <DialogHeader>
          <DialogTitle>保存视图</DialogTitle>
          <p className="text-sm text-muted-foreground">保存当前显示列、搜索字段、筛选与排序</p>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pm-save-view-name">视图名称 *</Label>
            <Input
              id="pm-save-view-name"
              value={name}
              aria-invalid={invalid || undefined}
              onChange={(event) => { setName(event.target.value); setTouched(true) }}
              placeholder="例如：研发待办 · 我的配置"
            />
            {invalid ? <p className="text-xs text-destructive">请输入视图名称</p> : null}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pm-save-view-scope">共享范围</Label>
            <select
              id="pm-save-view-scope"
              className="h-9 w-full rounded-md border border-input bg-background px-2 text-sm"
              defaultValue="private"
              disabled
            >
              <option value="private">仅自己可见</option>
            </select>
          </div>
          <p className="rounded-md border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
            当前配置：{config.listFields.length} 个显示列、{config.searchFields.length} 个搜索字段、
            {config.filterFields.length} 个筛选字段。
          </p>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button
            type="button"
            disabled={saving || !name.trim()}
            onClick={() => {
              setTouched(true)
              if (!name.trim()) return
              onSave(name.trim())
            }}
          >
            保存视图
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
