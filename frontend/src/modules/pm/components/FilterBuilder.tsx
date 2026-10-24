import { useEffect, useMemo, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { UserProfile } from '@/modules/user/types'
import type { FilterCondition, FilterGroup, FilterOperator, FilterTree } from '@/modules/pm/types'
import type { ColumnCatalog, PmColumnMeta } from '@/modules/pm/utils/columns'
import {
  acceptsMultipleValues,
  acceptsRange,
  buildQuickFilter,
  createCondition,
  createGroup,
  filterTreeToChips,
  filterTreeToExpression,
  isValueLessOperator,
  operatorsFor,
  validateFilterTree,
  type QuickFilter,
} from '@/modules/pm/utils/filterTree'

interface Option {
  value: string
  label: string
}

function cloneTree(tree: FilterTree): FilterTree {
  return JSON.parse(JSON.stringify(tree)) as FilterTree
}

function optionsFor(
  meta: PmColumnMeta,
  optionLabels: Map<string, Map<string, string>>,
  users: UserProfile[],
): Option[] {
  if (meta.filterType === 'user') {
    return users.map((user) => ({ value: String(user.id), label: user.displayName || user.username }))
  }
  const labels = optionLabels.get(meta.key)
  if (!labels) return []
  return [...labels.entries()].map(([value, label]) => ({ value, label }))
}

function ValueEditor({
  condition,
  meta,
  options,
  onChange,
}: {
  condition: FilterCondition
  meta: PmColumnMeta
  options: Option[]
  onChange: (value: unknown) => void
}) {
  if (isValueLessOperator(condition.operator)) {
    return <span className="text-xs text-muted-foreground">无需填写</span>
  }
  if (acceptsRange(condition.operator)) {
    const values = Array.isArray(condition.value) ? condition.value : ['', '']
    const type = meta.filterType === 'number' ? 'number' : meta.filterType === 'date' ? 'date' : 'text'
    return (
      <div className="flex items-center gap-1">
        <Input
          aria-label={`${meta.label}起始值`}
          className="h-8 w-32"
          type={type}
          value={String(values[0] ?? '')}
          onChange={(event) => onChange([event.target.value, values[1] ?? ''])}
        />
        <span className="text-xs text-muted-foreground">至</span>
        <Input
          aria-label={`${meta.label}结束值`}
          className="h-8 w-32"
          type={type}
          value={String(values[1] ?? '')}
          onChange={(event) => onChange([values[0] ?? '', event.target.value])}
        />
      </div>
    )
  }
  if (acceptsMultipleValues(condition.operator)) {
    const selected = Array.isArray(condition.value) ? condition.value.map(String) : []
    if (options.length) {
      return (
        <div className="flex max-h-24 flex-wrap gap-1 overflow-y-auto rounded-md border p-1.5">
          {options.map((option) => {
            const checked = selected.includes(option.value)
            return (
              <label key={option.value} className="flex items-center gap-1 rounded px-1.5 py-0.5 text-xs hover:bg-muted">
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => onChange(checked
                    ? selected.filter((value) => value !== option.value)
                    : [...selected, option.value])}
                />
                {option.label}
              </label>
            )
          })}
        </div>
      )
    }
    return (
      <Input
        aria-label={`${meta.label}值`}
        className="h-8 w-48"
        placeholder="多个值用逗号分隔"
        value={selected.join(',')}
        onChange={(event) => onChange(event.target.value.split(',').map((item) => item.trim()).filter(Boolean))}
      />
    )
  }
  if (meta.filterType === 'enum' || meta.filterType === 'user') {
    if (options.length) {
      return (
        <Select value={String(condition.value ?? '')} onValueChange={onChange}>
          <SelectTrigger className="h-8 w-40"><SelectValue placeholder="选择值" /></SelectTrigger>
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
      aria-label={`${meta.label}值`}
      className="h-8 w-40"
      type={type}
      value={String(condition.value ?? '')}
      onChange={(event) => onChange(event.target.value)}
    />
  )
}

function ConditionRow({
  condition,
  metas,
  optionLabels,
  users,
  onChange,
  onRemove,
}: {
  condition: FilterCondition
  metas: PmColumnMeta[]
  optionLabels: Map<string, Map<string, string>>
  users: UserProfile[]
  onChange: (condition: FilterCondition) => void
  onRemove: () => void
}) {
  const meta = metas.find((item) => item.key === condition.field) ?? metas[0]
  if (!meta) return null
  const operators = operatorsFor(meta.filterType)
  const options = optionsFor(meta, optionLabels, users)
  return (
    <div className="flex flex-wrap items-center gap-1.5 rounded-md border bg-background p-1.5">
      <Select
        value={meta.key}
        onValueChange={(value) => {
          const next = metas.find((item) => item.key === value)
          if (!next) return
          const operator = operatorsFor(next.filterType)[0].value
          onChange({ ...condition, field: value, operator, value: '' })
        }}
      >
        <SelectTrigger className="h-8 w-32"><SelectValue /></SelectTrigger>
        <SelectContent>
          {metas.map((item) => (
            <SelectItem key={item.key} value={item.key}>{item.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select
        value={condition.operator}
        onValueChange={(value) => {
          const operator = value as FilterOperator
          const reset = acceptsMultipleValues(operator) ? [] : acceptsRange(operator) ? ['', ''] : ''
          onChange({ ...condition, operator, value: reset })
        }}
      >
        <SelectTrigger className="h-8 w-28"><SelectValue /></SelectTrigger>
        <SelectContent>
          {operators.map((operator) => (
            <SelectItem key={operator.value} value={operator.value}>{operator.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>
      <ValueEditor
        condition={condition}
        meta={meta}
        options={options}
        onChange={(value) => onChange({ ...condition, value })}
      />
      <Button type="button" size="icon" variant="ghost" aria-label="移除条件" onClick={onRemove}>
        <Trash2 className="size-4" />
      </Button>
    </div>
  )
}

/**
 * 两层级筛选构建器：根条件 + 条件组（组内单一 AND/OR），带表达式、预计匹配与缺值校验。
 */
export function FilterBuilder({
  open,
  onOpenChange,
  tree,
  catalog,
  filterFields,
  optionLabels,
  users,
  currentUserId,
  riskHighValue,
  onApply,
  onEstimate,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  tree: FilterTree
  catalog: ColumnCatalog
  filterFields: string[]
  optionLabels: Map<string, Map<string, string>>
  users: UserProfile[]
  currentUserId?: string
  riskHighValue?: string
  onApply: (tree: FilterTree) => void
  onEstimate?: (tree: FilterTree) => Promise<number>
}) {
  const [draft, setDraft] = useState<FilterTree>(() => cloneTree(tree))
  const [estimate, setEstimate] = useState<number | null>(null)
  const [estimating, setEstimating] = useState(false)
  const [showErrors, setShowErrors] = useState(false)

  useEffect(() => {
    if (open) {
      setDraft(cloneTree(tree))
      setShowErrors(false)
      setEstimate(null)
    }
  }, [open, tree])

  const metas = useMemo(
    () => filterFields
      .map((key) => catalog.missing(key))
      .filter((meta) => !meta.missing && meta.filterable),
    [catalog, filterFields],
  )
  const fieldTypes = useMemo(
    () => new Map(metas.map((meta) => [meta.key, meta.filterType])),
    [metas],
  )
  const fieldLabels = useMemo(
    () => new Map(metas.map((meta) => [meta.key, meta.label])),
    [metas],
  )
  const errors = validateFilterTree(draft, { fieldTypes, fieldLabels })

  useEffect(() => {
    if (!open || !onEstimate) return
    const timer = setTimeout(() => {
      setEstimating(true)
      onEstimate(draft)
        .then((count) => setEstimate(count))
        .catch(() => setEstimate(null))
        .finally(() => setEstimating(false))
    }, 400)
    return () => clearTimeout(timer)
  }, [draft, open, onEstimate])

  function updateCondition(id: string, next: FilterCondition) {
    setDraft((current) => ({
      ...current,
      conditions: current.conditions.map((item) => (item.id === id ? next : item)),
      groups: current.groups.map((group) => ({
        ...group,
        conditions: group.conditions.map((item) => (item.id === id ? next : item)),
      })),
    }))
  }

  function removeCondition(id: string) {
    setDraft((current) => ({
      ...current,
      conditions: current.conditions.filter((item) => item.id !== id),
      groups: current.groups.map((group) => ({
        ...group,
        conditions: group.conditions.filter((item) => item.id !== id),
      })),
    }))
  }

  function updateGroup(id: string, next: FilterGroup) {
    setDraft((current) => ({
      ...current,
      groups: current.groups.map((group) => (group.id === id ? next : group)),
    }))
  }

  function addCondition(groupId?: string) {
    const meta = metas[0]
    if (!meta) return
    const condition = createCondition(meta.key, operatorsFor(meta.filterType)[0].value, '')
    if (!groupId) {
      setDraft((current) => ({ ...current, conditions: [...current.conditions, condition] }))
    } else {
      setDraft((current) => ({
        ...current,
        groups: current.groups.map((group) =>
          group.id === groupId ? { ...group, conditions: [...group.conditions, condition] } : group),
      }))
    }
  }

  function applyQuick(preset: QuickFilter) {
    setDraft(buildQuickFilter(preset, { currentUserId, riskHighValue }))
  }

  function apply() {
    if (errors.length) {
      setShowErrors(true)
      return
    }
    // 只提交一次：调用方在 onApply 里同时写条件与清 overlay。
    // 这里再调 onOpenChange 会拿旧 URL 状态再写一次，把刚应用的条件覆盖掉。
    onApply(draft)
  }

  const chips = filterTreeToChips(draft, { fieldLabels, fieldTypes, optionLabels })
  const expression = filterTreeToExpression(draft, { fieldLabels, fieldTypes, optionLabels })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[88vh] w-[calc(100vw-2rem)] max-w-4xl flex-col overflow-hidden">
        <DialogHeader>
          <DialogTitle>筛选工作项</DialogTitle>
          <p className="text-sm text-muted-foreground">支持两级条件组；条件只使用视图配置中勾选的字段。</p>
        </DialogHeader>
        <div className="flex flex-1 flex-col gap-3 overflow-y-auto pr-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm text-muted-foreground">快捷筛选</span>
            <Button type="button" size="sm" variant="outline" onClick={() => applyQuick('mine')}>我的工作项</Button>
            <Button type="button" size="sm" variant="outline" onClick={() => applyQuick('week')}>本周到期</Button>
            <Button type="button" size="sm" variant="outline" onClick={() => applyQuick('blocked')}>阻塞风险</Button>
            <Button type="button" size="sm" variant="outline" onClick={() => applyQuick('unassigned')}>未分配</Button>
          </div>

          <div className="flex items-center gap-2 rounded-md bg-muted/40 px-3 py-2">
            <span className="text-sm text-muted-foreground">根条件</span>
            <Select value={draft.logic} onValueChange={(value) => setDraft((current) => ({ ...current, logic: value as FilterTree['logic'] }))}>
              <SelectTrigger className="h-8 w-20"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="AND">AND</SelectItem>
                <SelectItem value="OR">OR</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-2">
            {draft.conditions.length === 0 && draft.groups.length === 0 ? (
              <p className="rounded-md border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
                还没有条件，点击下方「添加条件」开始
              </p>
            ) : null}
            {draft.conditions.map((condition) => (
              <ConditionRow
                key={condition.id}
                condition={condition}
                metas={metas}
                optionLabels={optionLabels}
                users={users}
                onChange={(next) => updateCondition(condition.id, next)}
                onRemove={() => removeCondition(condition.id)}
              />
            ))}
            {draft.groups.map((group) => (
              <div key={group.id} className="flex flex-col gap-2 rounded-md border border-dashed p-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm text-muted-foreground">条件组</span>
                  <Select value={group.logic} onValueChange={(value) => updateGroup(group.id, { ...group, logic: value as FilterGroup['logic'] })}>
                    <SelectTrigger className="h-8 w-20"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="AND">AND</SelectItem>
                      <SelectItem value="OR">OR</SelectItem>
                    </SelectContent>
                  </Select>
                  <Button type="button" size="sm" variant="ghost" onClick={() => addCondition(group.id)}>
                    <Plus className="size-3.5" />添加条件
                  </Button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label="移除条件组"
                    onClick={() => setDraft((current) => ({ ...current, groups: current.groups.filter((item) => item.id !== group.id) }))}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </div>
                {group.conditions.map((condition) => (
                  <ConditionRow
                    key={condition.id}
                    condition={condition}
                    metas={metas}
                    optionLabels={optionLabels}
                    users={users}
                    onChange={(next) => updateCondition(condition.id, next)}
                    onRemove={() => removeCondition(condition.id)}
                  />
                ))}
              </div>
            ))}
          </div>

          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" variant="outline" onClick={() => addCondition()}>
              <Plus className="size-3.5" />添加条件
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setDraft((current) => ({ ...current, groups: [...current.groups, createGroup('AND')] }))}
            >
              <Plus className="size-3.5" />添加条件组
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => setDraft({ logic: 'AND', conditions: [], groups: [] })}
            >
              清空全部
            </Button>
          </div>

          {chips.length ? (
            <div className="flex flex-wrap gap-1.5 rounded-md bg-muted/30 p-2 text-xs">
              {chips.map((chip) => (
                <span key={chip.key} className="rounded border bg-background px-1.5 py-0.5">{chip.label}</span>
              ))}
            </div>
          ) : null}
          <div className="flex flex-col gap-1 rounded-md border bg-muted/20 p-2 text-sm">
            <span className="text-muted-foreground">表达式预览</span>
            <span>{expression || '尚未设置条件'}</span>
          </div>

          {showErrors && errors.length ? (
            <div role="alert" className="rounded-md border border-destructive/40 bg-destructive/5 p-2 text-sm text-destructive">
              有 {errors.length} 个条件缺少值。请补全值，或把操作符改为「为空」「今天」等无需填写值的条件。
              <ul className="mt-1 list-inside list-disc text-xs">
                {errors.map((error) => <li key={error}>{error}</li>)}
              </ul>
            </div>
          ) : null}
        </div>
        <DialogFooter className="border-t pt-3">
          <span className="mr-auto text-sm text-muted-foreground">
            {estimating ? '正在统计…' : estimate != null ? `预计匹配 ${estimate} 条` : '调整条件后显示预计匹配数'}
          </span>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button type="button" onClick={apply}>应用筛选</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
