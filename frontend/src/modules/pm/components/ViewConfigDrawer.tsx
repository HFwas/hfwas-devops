import { useEffect, useMemo, useRef, useState } from 'react'
import { ArrowDown, ArrowUp, Lock, Plus, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { PmViewConfig } from '@/modules/pm/types'
import type { ColumnCatalog } from '@/modules/pm/utils/columns'
import type { PmConfigTab } from '@/modules/pm/utils/listUrlState'
import { cn } from '@/lib/utils'

/**
 * 视图配置抽屉：显示列 / 搜索字段 / 筛选字段 + 密度与默认排序。
 * 只编辑草稿，保存由调用方落到 URL 或保存视图。
 */
export function ViewConfigDrawer({
  open,
  onOpenChange,
  tab,
  onTabChange,
  catalog,
  config,
  saving,
  onSave,
  onReset,
  onPreview,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  tab: PmConfigTab
  onTabChange: (tab: PmConfigTab) => void
  catalog: ColumnCatalog
  config: PmViewConfig
  saving?: boolean
  onSave: (config: PmViewConfig) => void
  onReset: () => void
  onPreview?: (config: PmViewConfig) => void
}) {
  const [draft, setDraft] = useState<PmViewConfig>(config)
  const onPreviewRef = useRef(onPreview)
  const lastConfigKey = useRef('')

  useEffect(() => {
    onPreviewRef.current = onPreview
  }, [onPreview])

  useEffect(() => {
    if (!open) return
    onPreviewRef.current?.(draft)
  }, [draft, open])

  useEffect(() => {
    if (!open) {
      lastConfigKey.current = ''
      return
    }
    const key = JSON.stringify(config)
    if (lastConfigKey.current !== key) {
      lastConfigKey.current = key
      setDraft(config)
    }
  }, [open, config])

  const columns = useMemo(
    () => draft.listFields.map((key) => catalog.missing(key)),
    [catalog, draft.listFields],
  )
  const columnCandidates = catalog.candidates.filter((column) => column.key !== 'description')
  const searchCandidates = catalog.candidates.filter((column) => column.searchable)
  const filterCandidates = catalog.candidates.filter((column) => column.filterable)

  function toggleList(key: string, checked: boolean) {
    if (key === 'title') return
    setDraft((current) => ({
      ...current,
      listFields: checked
        ? [...current.listFields, key]
        : current.listFields.filter((item) => item !== key),
    }))
  }

  function move(key: string, delta: number) {
    setDraft((current) => {
      const index = current.listFields.indexOf(key)
      const target = index + delta
      if (index < 0 || target < 0 || target >= current.listFields.length) return current
      const list = [...current.listFields]
      ;[list[index], list[target]] = [list[target], list[index]]
      return { ...current, listFields: list }
    })
  }

  function toggleSearch(key: string, checked: boolean) {
    setDraft((current) => {
      if (!checked && current.searchFields.length <= 1) return current
      return {
        ...current,
        searchFields: checked
          ? [...current.searchFields, key]
          : current.searchFields.filter((item) => item !== key),
      }
    })
  }

  function toggleFilter(key: string, checked: boolean) {
    setDraft((current) => ({
      ...current,
      filterFields: checked
        ? [...current.filterFields, key]
        : current.filterFields.filter((item) => item !== key),
    }))
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="flex w-full flex-col overflow-hidden p-0 sm:max-w-[540px]">
        <SheetHeader className="shrink-0 border-b px-5 py-4">
          <SheetTitle>视图配置</SheetTitle>
          <SheetDescription>调整显示列、搜索范围与筛选字段，保存后应用到当前视图。</SheetDescription>
        </SheetHeader>
        <Tabs
          value={tab}
          onValueChange={(value) => onTabChange(value as PmConfigTab)}
          className="flex min-h-0 flex-1 flex-col px-5 py-4"
        >
          <TabsList className="w-full shrink-0">
            <TabsTrigger value="columns">显示列</TabsTrigger>
            <TabsTrigger value="search">搜索字段</TabsTrigger>
            <TabsTrigger value="filter">筛选字段</TabsTrigger>
          </TabsList>

          <TabsContent value="columns" className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pt-4 pr-1">
            <div className="flex flex-col gap-2">
              <Label>已显示列（顺序即列顺序）</Label>
              {columns.map((column, index) => (
                <div key={column.key} className="flex items-center gap-2 rounded-md border px-2 py-1.5">
                  <Checkbox checked disabled={column.key === 'title'} onCheckedChange={() => toggleList(column.key, false)} />
                  <span className="text-sm">{column.label}</span>
                  <span className="flex-1 font-mono text-xs text-muted-foreground">{column.key}</span>
                  {column.missing ? <span className="text-xs text-muted-foreground">字段缺失</span> : null}
                  {column.key === 'title' ? (
                    <>
                      <span className="rounded border px-1 text-[10px] text-muted-foreground">固定</span>
                      <Lock className="size-3.5 text-muted-foreground" />
                    </>
                  ) : null}
                  <Button type="button" size="icon" variant="ghost" aria-label={`上移 ${column.label}`} disabled={index === 0} onClick={() => move(column.key, -1)}>
                    <ArrowUp className="size-4" />
                  </Button>
                  <Button type="button" size="icon" variant="ghost" aria-label={`下移 ${column.label}`} disabled={index === columns.length - 1} onClick={() => move(column.key, 1)}>
                    <ArrowDown className="size-4" />
                  </Button>
                </div>
              ))}
            </div>
            <div className="flex flex-col gap-2">
              <Label>可选列</Label>
              {columnCandidates.filter((column) => !draft.listFields.includes(column.key)).map((column) => (
                <button
                  key={column.key}
                  type="button"
                  className="flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted"
                  onClick={() => toggleList(column.key, true)}
                >
                  <Plus className="size-4 text-muted-foreground" />
                  {column.label}
                  {column.missing ? <span className="text-xs text-muted-foreground">字段缺失</span> : null}
                </button>
              ))}
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1.5">
                <Label>密度</Label>
                <Select value={draft.density} onValueChange={(value) => setDraft((current) => ({ ...current, density: value as PmViewConfig['density'] }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="standard">标准</SelectItem>
                    <SelectItem value="compact">紧凑</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label>默认排序</Label>
                <div className="flex gap-2">
                  <Select
                    value={draft.sort[0]?.field ?? 'update_time'}
                    onValueChange={(value) => setDraft((current) => ({ ...current, sort: [{ field: value, order: current.sort[0]?.order ?? 'DESC' }] }))}
                  >
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {catalog.candidates.filter((column) => column.sortable).map((column) => (
                        <SelectItem key={column.key} value={column.key}>{column.label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Select
                    value={draft.sort[0]?.order ?? 'DESC'}
                    onValueChange={(value) => setDraft((current) => ({ ...current, sort: [{ field: current.sort[0]?.field ?? 'update_time', order: value as 'ASC' | 'DESC' }] }))}
                  >
                    <SelectTrigger className="w-24"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ASC">升序</SelectItem>
                      <SelectItem value="DESC">降序</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>
          </TabsContent>

          <TabsContent value="search" className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pt-4 pr-1">
            <p className="text-sm text-muted-foreground">
              搜索框会在这 {draft.searchFields.length} 个字段中匹配，至少保留 1 个。
            </p>
            {searchCandidates.map((column) => (
              <label key={column.key} className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm">
                <Checkbox
                  checked={draft.searchFields.includes(column.key)}
                  onCheckedChange={(checked) => toggleSearch(column.key, checked === true)}
                />
                {column.label}
              </label>
            ))}
            <p className={cn('text-xs text-muted-foreground', draft.searchFields.length === 1 && 'text-amber-600')}>
              当前 {draft.searchFields.length} 个字段
            </p>
          </TabsContent>

          <TabsContent value="filter" className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pt-4 pr-1">
            <p className="text-sm text-muted-foreground">筛选构建器只会展示勾选的字段。</p>
            {filterCandidates.map((column) => (
              <label key={column.key} className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm">
                <Checkbox
                  checked={draft.filterFields.includes(column.key)}
                  onCheckedChange={(checked) => toggleFilter(column.key, checked === true)}
                />
                {column.label}
              </label>
            ))}
          </TabsContent>
        </Tabs>
        <SheetFooter className="shrink-0 border-t px-5 py-3">
          <div className="flex w-full items-center justify-between gap-2">
            <Button type="button" variant="outline" onClick={onReset}>
              <RotateCcw />
              恢复默认
            </Button>
            <Button type="button" disabled={saving} onClick={() => onSave(draft)}>
              保存配置
            </Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}
