import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { UserProfile } from '@/modules/user/types'
import { pmWorkItemApi } from '@/modules/pm/api'
import type { BatchActionConfig, BatchOperationResult, PmWorkItem } from '@/modules/pm/types'
import type { ColumnCatalog, PmColumnMeta } from '@/modules/pm/utils/columns'
import { asId } from '@/modules/pm/utils/id'
import { errorMessage } from '@/shared/errors/apiError'
import { BatchResultList } from '@/modules/pm/components/BatchResultList'

function editableMetas(config: BatchActionConfig, catalog: ColumnCatalog): PmColumnMeta[] {
  const whitelist = config.fieldWhitelist ?? []
  return whitelist
    .filter((key) => key !== 'status')
    .map((key) => catalog.missing(key))
    .filter((meta) => !meta.missing)
    .filter((meta) => meta.batchEditable)
}

function optionsOf(meta: PmColumnMeta, optionLabels: Map<string, Map<string, string>>, users: UserProfile[]) {
  if (meta.filterType === 'user') {
    return users.map((user) => ({ value: String(user.id), label: user.displayName || user.username }))
  }
  return [...(optionLabels.get(meta.key)?.entries() ?? [])].map(([value, label]) => ({ value, label }))
}

function ValueEditor({
  meta,
  value,
  disabled,
  optionLabels,
  users,
  onChange,
}: {
  meta: PmColumnMeta
  value: unknown
  disabled: boolean
  optionLabels: Map<string, Map<string, string>>
  users: UserProfile[]
  onChange: (value: unknown) => void
}) {
  const options = optionsOf(meta, optionLabels, users)
  if (meta.filterType === 'enum' || meta.filterType === 'user') {
    if (options.length) {
      return (
        <Select value={String(value ?? '')} onValueChange={onChange} disabled={disabled}>
          <SelectTrigger><SelectValue placeholder="选择新值" /></SelectTrigger>
          <SelectContent>
            {options.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      )
    }
  }
  const type = meta.filterType === 'number' ? 'number' : meta.filterType === 'date' ? 'date' : 'text'
  return (
    <Input
      aria-label={`${meta.label}新值`}
      type={type}
      value={String(value ?? '')}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

/**
 * 批量编辑：字段来自动作配置白名单；先 dry-run 预览，再执行并展示逐条结果。
 */
export function BulkEditDialog({
  open,
  onOpenChange,
  items,
  catalog,
  config,
  optionLabels,
  users,
  onFinished,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  items: PmWorkItem[]
  catalog: ColumnCatalog
  config: BatchActionConfig
  optionLabels: Map<string, Map<string, string>>
  users: UserProfile[]
  onFinished?: (result: BatchOperationResult) => void
}) {
  const metas = useMemo(() => editableMetas(config, catalog), [config, catalog])
  const [field, setField] = useState('')
  const [value, setValue] = useState<unknown>('')
  const [clear, setClear] = useState(false)
  const [result, setResult] = useState<BatchOperationResult | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!open) return
    setField(metas[0]?.key ?? '')
    setValue('')
    setClear(false)
    setResult(null)
    setError('')
  }, [open, metas])

  const meta = metas.find((item) => item.key === field)

  function payload(dryRun: boolean) {
    if (!meta) throw new Error('请选择要修改的字段')
    if (!clear && (value === '' || value == null)) throw new Error('请填写新值，或勾选「清空该字段」')
    const versions: Record<string, number> = {}
    for (const item of items) {
      const version = item.version != null ? Number(item.version) : NaN
      if (Number.isFinite(version) && item.id != null) versions[asId(item.id)] = version
    }
    return {
      ids: items.map((item) => asId(item.id)),
      patch: { [meta.key]: clear ? null : value },
      ...(Object.keys(versions).length ? { versions } : {}),
      dryRun,
    }
  }

  async function run(dryRun: boolean) {
    setBusy(true)
    setError('')
    try {
      const next = await pmWorkItemApi.batchUpdate(payload(dryRun))
      setResult(next)
      if (!dryRun) onFinished?.(next)
    } catch (err) {
      setError(errorMessage(err, '批量更新失败'))
    } finally {
      setBusy(false)
    }
  }

  const titles = useMemo(
    () => new Map(items.map((item) => [asId(item.id), item.title])),
    [items],
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>批量编辑 {items.length} 个工作项</DialogTitle>
          <p className="text-sm text-muted-foreground">只显示动作配置允许批量修改的字段；可先预演查看跳过与失败项。</p>
        </DialogHeader>
        {metas.length === 0 ? (
          <p className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">
            当前项目没有可批量编辑的字段，请先在批量操作配置中设置字段白名单。
          </p>
        ) : (
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label>字段</Label>
              <Select value={field} onValueChange={(next) => { setField(next); setValue(''); setResult(null) }}>
                <SelectTrigger><SelectValue placeholder="选择字段" /></SelectTrigger>
                <SelectContent>
                  {metas.map((item) => (
                    <SelectItem key={item.key} value={item.key}>{item.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>新值 *</Label>
              {meta ? (
                <ValueEditor
                  meta={meta}
                  value={value}
                  disabled={clear}
                  optionLabels={optionLabels}
                  users={users}
                  onChange={setValue}
                />
              ) : null}
            </div>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={clear} onCheckedChange={(checked) => setClear(checked === true)} />
              清空该字段的值
            </label>
            <p className="rounded-md bg-muted/40 p-2 text-xs text-muted-foreground">
              将修改 {items.length} 个工作项；提交后展示成功、跳过与失败明细。跳过项包括工作流不允许或字段不可见的记录。
            </p>
            {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
            {result ? <BatchResultList result={result} titles={titles} /> : null}
          </div>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button type="button" variant="outline" disabled={busy || !metas.length} onClick={() => run(true)}>
            影响预览
          </Button>
          <Button type="button" disabled={busy || !metas.length} onClick={() => run(false)}>
            应用修改
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
