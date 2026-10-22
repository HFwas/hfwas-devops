import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownUp, Columns3, Filter, Plus, Trash2 } from 'lucide-react'
import { useLocation, useParams } from 'react-router'
import { toast } from 'sonner'
import { BulkActionBar } from '@/components/console/BulkActionBar'
import { FilterBar } from '@/components/console/FilterBar'
import { ConfirmDialog } from '@/components/console/ConfirmDialog'
import { ListPageShell } from '@/components/console/ListPageShell'
import { Pagination } from '@/components/console/Pagination'
import { SearchInput } from '@/components/console/SearchInput'
import { DataTable } from '@/components/console/DataTable'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { pmBatchActionConfigApi, pmFieldApi, pmModuleApi, pmProjectApi, pmProjectIssueTypeApi, pmStatusApi, pmViewApi, pmWorkItemApi } from '@/modules/pm/api'
import { ALL_ITEMS_CODE } from '@/modules/pm/nav'
import { BulkDeleteDialog } from '@/modules/pm/components/BulkDeleteDialog'
import { BulkEditDialog } from '@/modules/pm/components/BulkEditDialog'
import { FilterBuilder } from '@/modules/pm/components/FilterBuilder'
import { TransitionDialog } from '@/modules/pm/components/TransitionDialog'
import { ViewConfigDrawer } from '@/modules/pm/components/ViewConfigDrawer'
import { WorkItemViewTabs } from '@/modules/pm/components/WorkItemViewTabs'
import { usePmListUrlState } from '@/modules/pm/hooks/usePmListUrlState'
import type {
  BatchActionConfig,
  FieldDefinition,
  FilterTree,
  PmSavedView,
  PmWorkItem,
  PmWorkItemType,
  ResolvedFieldOption,
  StatusDefinition,
} from '@/modules/pm/types'
import { PRIORITY_OPTIONS, typeLabel } from '@/modules/pm/types'
import { buildColumnCatalog, renderPmCell, type CellContext } from '@/modules/pm/utils/columns'
import { cropFilterTree, emptyFilterTree, filterTreeToChips } from '@/modules/pm/utils/filterTree'
import { formatDateTime } from '@/modules/pm/utils/format'
import { asId } from '@/modules/pm/utils/id'
import { effectiveConfig, pruneSelection } from '@/modules/pm/utils/listUrlState'
import { buildQuerySpec, estimateCountSpec } from '@/modules/pm/utils/querySpec'
import { defaultViewConfig, builtInView, configToSavedColumns, isBuiltInView, isViewDirty, normalizeSavedView } from '@/modules/pm/utils/viewConfig'
import { resolveRouteProjectId } from '@/modules/pm/utils/projectRoute'
import { userAuthApi } from '@/modules/user/api'
import { useAuthStore } from '@/stores/auth'
import { isApiError } from '@/shared/errors/apiError'

const DEFAULT_CONFIG = (actionCode: string): BatchActionConfig => ({
  actionCode,
  enabled: actionCode !== 'DELETE',
  maxItems: 100,
  requireConfirm: true,
  allowedRoles: [],
  fieldWhitelist: actionCode === 'UPDATE_FIELDS'
    ? ['priority', 'assignee_id', 'module_id', 'end_time']
    : null,
})

const FORBIDDEN_CODES = new Set([403, 11002, 11005, 20002])

function actionAllowed(config: BatchActionConfig, count: number, role?: string) {
  if (!config.enabled) return false
  if (count > config.maxItems) return false
  const roles = config.allowedRoles ?? []
  if (!roles.length) return true
  return roles.includes((role ?? 'user').toLowerCase())
}

export function WorkItemListPage() {
  const { projectId: projectParam = '', typeCode: typeParam = ALL_ITEMS_CODE } = useParams()
  const projectId = resolveRouteProjectId(projectParam)
  const typeCode = typeParam === ALL_ITEMS_CODE ? undefined : typeParam
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((state) => state.user)
  const location = useLocation()
  const viewParam = useMemo(() => new URLSearchParams(location.search).get('view') || 'dev', [location.search])
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<PmWorkItem | null>(null)
  const [previewConfig, setPreviewConfig] = useState<import('@/modules/pm/types').PmViewConfig | null>(null)
  const [createType, setCreateType] = useState(typeCode ?? '')
  const [createTitle, setCreateTitle] = useState('')
  const lastTypeRef = useRef(typeParam)

  const projectQuery = useQuery({
    queryKey: ['pm-project', projectId],
    queryFn: () => pmProjectApi.getById(projectId!),
    enabled: Boolean(projectId),
  })
  const typesQuery = useQuery({
    queryKey: ['pm-issue-types', projectId],
    queryFn: () => pmProjectIssueTypeApi.list(projectId!),
    enabled: Boolean(projectId),
  })
  const enabledTypes = useMemo(
    () => (typesQuery.data ?? []).filter((type) => type.enabled !== 0),
    [typesQuery.data],
  )
  const fieldsQuery = useQuery({
    queryKey: ['pm-list-fields', projectId, typeParam],
    queryFn: () => typeCode ? pmFieldApi.list(projectId!, typeCode) : pmFieldApi.catalog(projectId!),
    enabled: Boolean(projectId),
  })
  const savedViewsQuery = useQuery({
    queryKey: ['pm-saved-views', projectId, typeParam],
    queryFn: () => pmViewApi.list(projectId!, typeCode),
    enabled: Boolean(projectId),
  })
  const usersQuery = useQuery({
    queryKey: ['pm-list-users'],
    queryFn: () => userAuthApi.userOptions(),
  })
  const modulesQuery = useQuery({
    queryKey: ['pm-modules', projectId],
    queryFn: () => pmModuleApi.flat(projectId!),
    enabled: Boolean(projectId),
  })
  const configsQuery = useQuery({
    queryKey: ['pm-batch-action-configs', projectId],
    queryFn: () => pmBatchActionConfigApi.list(projectId!),
    enabled: Boolean(projectId),
  })
  const statusesQuery = useQuery({
    queryKey: ['pm-list-statuses', projectId, typeParam, enabledTypes.map((type) => type.code).join(',')],
    enabled: Boolean(projectId) && (Boolean(typeCode) || enabledTypes.length > 0),
    queryFn: async () => {
      const typeCodes = typeCode ? [typeCode] : enabledTypes.map((type) => type.code)
      const entries = await Promise.all(typeCodes.map(async (code) => {
        try {
          return [code, await pmStatusApi.options(projectId!, code)] as const
        } catch {
          return [code, [] as StatusDefinition[]] as const
        }
      }))
      return Object.fromEntries(entries) as Record<string, StatusDefinition[]>
    },
  })
  const fields = (fieldsQuery.data ?? []) as FieldDefinition[]
  const catalog = useMemo(() => buildColumnCatalog(fields), [fields])
  const fieldTypes = useMemo(
    () => new Map(catalog.candidates.map((column) => [column.key, column.filterType])),
    [catalog],
  )
  const optionFields = useMemo(
    () => fields.filter((field) =>
      field.id != null && (field.systemFlag == null || field.systemFlag === 0) &&
      ['SELECT', 'MULTI_SELECT', 'STATUS', 'PRIORITY'].includes(field.fieldType)),
    [fields],
  )
  const fieldOptionsQuery = useQuery({
    queryKey: ['pm-field-options', projectId, typeParam, optionFields.map((field) => field.fieldKey).join(',')],
    enabled: Boolean(projectId) && optionFields.length > 0,
    queryFn: async () => {
      const entries = await Promise.all(optionFields.map(async (field) => {
        try {
          return [field.fieldKey, await pmFieldApi.resolveOptions(field.id!)] as const
        } catch {
          return [field.fieldKey, [] as ResolvedFieldOption[]] as const
        }
      }))
      return Object.fromEntries(entries) as Record<string, ResolvedFieldOption[]>
    },
  })

  const savedViews = ((savedViewsQuery.data ?? []) as PmSavedView[])
    .filter((view) => (view.typeCode ?? undefined) === (typeCode ?? undefined))
  const viewSaved = savedViews.find((view) => String(view.id) === viewParam)
  const normalizedSaved = useMemo(() => viewSaved ? normalizeSavedView(viewSaved) : null, [viewSaved])
  const builtIn = builtInView(viewParam)
  const fallbackConfig = useMemo(
    () => normalizedSaved?.config ?? builtIn?.config ?? defaultViewConfig(),
    [normalizedSaved, builtIn],
  )
  const baselineTree = useMemo(
    () => normalizedSaved?.filterTree ?? emptyFilterTree(),
    [normalizedSaved],
  )

  const url = usePmListUrlState({ fallbackConfig, catalog, fieldTypes, fieldsReady: fieldsQuery.isFetched })
  const { state } = url
  const activeView = state.view
  const activeSavedView = savedViews.find((view) => String(view.id) === activeView)
  const activeBuiltIn = builtInView(activeView)
  const activeNormalized = useMemo(
    () => activeSavedView ? normalizeSavedView(activeSavedView) : null,
    [activeSavedView],
  )
  const activeFallbackConfig = useMemo(
    () => activeNormalized?.config ?? activeBuiltIn?.config ?? defaultViewConfig(),
    [activeNormalized, activeBuiltIn],
  )
  const activeBaselineTree = useMemo(
    () => activeNormalized?.filterTree ?? emptyFilterTree(),
    [activeNormalized],
  )
  const activeConfig = useMemo(() => effectiveConfig(state, activeFallbackConfig), [state, activeFallbackConfig])
  const displayConfig = previewConfig ?? activeConfig
  const dirty = useMemo(() => isViewDirty(
    { config: displayConfig, filterTree: state.filterTree },
    { config: activeFallbackConfig, filterTree: activeBaselineTree },
  ), [displayConfig, activeFallbackConfig, activeBaselineTree, state.filterTree])

  const statusByType = useMemo(() => {
    const map = new Map<string, Map<string, StatusDefinition>>()
    for (const [code, list] of Object.entries(statusesQuery.data ?? {})) {
      map.set(code, new Map(list.map((status) => [status.statusCode, status])))
    }
    return map
  }, [statusesQuery.data])
  const allStatuses = useMemo(() => {
    const map = new Map<string, StatusDefinition>()
    for (const status of Object.values(statusesQuery.data ?? {}).flat()) {
      if (!map.has(status.statusCode)) map.set(status.statusCode, status)
    }
    return [...map.values()]
  }, [statusesQuery.data])

  const users = usersQuery.data ?? []
  const usersById = useMemo(
    () => new Map(users.map((user) => [String(user.id), user])),
    [users],
  )
  const optionLabels = useMemo(() => {
    const map = new Map<string, Map<string, string>>()
    map.set('status', new Map(allStatuses.map((status) => [status.statusCode, status.statusName])))
    map.set('priority', new Map(PRIORITY_OPTIONS.map((option) => [option.value, option.label])))
    map.set('type_code', new Map(enabledTypes.map((type) => [type.code, type.name])))
    map.set('module_id', new Map((modulesQuery.data ?? []).map((module) => [String(module.id), module.name])))
    for (const [fieldKey, options] of Object.entries(fieldOptionsQuery.data ?? {})) {
      map.set(fieldKey, new Map(options.map((option) => [option.value, option.label])))
    }
    return map
  }, [allStatuses, enabledTypes, modulesQuery.data, fieldOptionsQuery.data])

  const riskLabels = optionLabels.get('risk')
  const riskHighValue = riskLabels
    ? [...riskLabels.entries()].find(([, label]) => label === '高' || label === '高风险')?.[0]
    : undefined

  const spec = useMemo(() => buildQuerySpec({
    projectId: projectId!,
    typeCode,
    viewId: activeView,
    filterTree: state.filterTree,
    q: state.q,
    searchFields: activeConfig.searchFields,
    sort: displayConfig.sort,
    page: state.page,
    size: state.size,
    currentUserId: currentUser?.id != null ? String(currentUser.id) : undefined,
    users,
    catalog,
    riskHighValue,
    enabledTypeCodes: enabledTypes.map((type) => type.code),
  }), [projectId, typeCode, activeView, state.filterTree, state.q, activeConfig.searchFields, displayConfig.sort,
    state.page, state.size, currentUser?.id, users, catalog, riskHighValue, enabledTypes])

  const itemsQuery = useQuery({
    queryKey: ['pm-work-items', projectId, typeParam, spec],
    queryFn: () => pmWorkItemApi.page(spec),
    enabled: Boolean(projectId) && fieldsQuery.isFetched && typesQuery.isFetched,
    placeholderData: keepPreviousData,
  })
  const rows = itemsQuery.data?.records ?? []
  const total = Number(itemsQuery.data?.total ?? 0)
  const pageCount = Math.max(1, Math.ceil(total / state.size))
  const pageIds = useMemo(() => rows.map((row) => asId(row.id)), [rows])
  const selectedItems = useMemo(
    () => rows.filter((row) => state.selectedIds.includes(asId(row.id))),
    [rows, state.selectedIds],
  )
  const latestUpdate = rows.reduce<string | undefined>((latest, row) =>
    row.updateTime && (!latest || row.updateTime > latest) ? row.updateTime : latest, undefined)

  const forbidden = isApiError(itemsQuery.error) && FORBIDDEN_CODES.has(itemsQuery.error.code)
  const metaError = typesQuery.isError ? (typesQuery.error as Error).message || '事项类型加载失败' : undefined

  useEffect(() => {
    if (!savedViewsQuery.isSuccess) return
    if (isBuiltInView(state.view)) return
    if (savedViews.some((view) => String(view.id) === state.view)) return
    url.switchView('dev')
  }, [savedViewsQuery.isSuccess, savedViews, state.view, url])

  useEffect(() => {
    if (forbidden && state.overlay) url.setOverlay(undefined)
  }, [forbidden, state.overlay, url])

  useEffect(() => {
    if (!itemsQuery.data) return
    const pruned = pruneSelection(state.selectedIds, pageIds)
    if (pruned.length !== state.selectedIds.length) url.patch({ selectedIds: pruned })
  }, [itemsQuery.data, pageIds, state.selectedIds, url])

  useEffect(() => {
    if (itemsQuery.data && state.page > pageCount) url.patch({ page: pageCount })
  }, [itemsQuery.data, pageCount, state.page, url])

  useEffect(() => {
    if (lastTypeRef.current === typeParam) return
    lastTypeRef.current = typeParam
    url.patch({ selectedIds: [], overlay: undefined, page: 1 })
  }, [typeParam, url])

  useEffect(() => {
    if (!itemsQuery.data) return
    if (state.overlay && ['bulk-edit', 'transition', 'delete'].includes(state.overlay) && selectedItems.length === 0) {
      url.setOverlay(undefined)
    }
  }, [itemsQuery.data, state.overlay, selectedItems.length, url])

  useEffect(() => {
    const allowed = new Set(activeConfig.filterFields)
    const cropped = cropFilterTree(state.filterTree, allowed)
    if (JSON.stringify(cropped) !== JSON.stringify(state.filterTree)) {
      url.patch({ filterTree: cropped, page: 1, selectedIds: [] })
    }
  }, [activeConfig.filterFields, state.filterTree, url])

  const invalidateList = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ['pm-work-items'] })
    await queryClient.invalidateQueries({ queryKey: ['pm-issue-types', projectId] })
  }, [queryClient, projectId])

  const saveView = useMutation({
    mutationFn: async (draft: typeof activeConfig) => {
      const name = activeSavedView?.name ?? activeBuiltIn?.label ?? '自定义视图'
      const existing = activeSavedView ?? savedViews.find((view) =>
        view.name === name && (view.typeCode ?? undefined) === (typeCode ?? undefined))
      const id = await pmViewApi.save({
        ...(existing?.id != null ? { id: existing.id } : {}),
        projectId: projectId!,
        name,
        typeCode,
        querySpec: spec,
        columns: configToSavedColumns(draft, state.filterTree),
      })
      return String(existing?.id ?? id)
    },
    onSuccess: async (id) => {
      await queryClient.invalidateQueries({ queryKey: ['pm-saved-views', projectId] })
      setPreviewConfig(null)
      url.patch({
        view: id,
        cols: undefined,
        search: undefined,
        ff: undefined,
        density: undefined,
        sort: undefined,
        page: 1,
        selectedIds: [],
        overlay: undefined,
      })
      toast.success('视图配置已保存')
    },
    onError: (error: Error) => toast.error(error.message || '保存视图失败'),
  })

  const createItem = useMutation({
    mutationFn: () => pmWorkItemApi.save({
      projectId: projectId!,
      typeCode: createType || typeCode || enabledTypes[0]?.code || 'task',
      title: createTitle.trim(),
    }),
    onSuccess: async () => {
      toast.success('事项已创建')
      setCreateOpen(false)
      setCreateTitle('')
      await invalidateList()
    },
    onError: (error: Error) => toast.error(error.message || '创建失败'),
  })

  const configMap = useMemo(() => {
    const map = new Map<string, BatchActionConfig>()
    for (const config of configsQuery.data ?? []) map.set(config.actionCode, config)
    return map
  }, [configsQuery.data])
  const updateConfig = configMap.get('UPDATE_FIELDS') ?? DEFAULT_CONFIG('UPDATE_FIELDS')
  const transitionConfig = configMap.get('TRANSITION') ?? DEFAULT_CONFIG('TRANSITION')
  const deleteConfig = configMap.get('DELETE') ?? DEFAULT_CONFIG('DELETE')
  const role = currentUser?.role
  const deleteEnabled = actionAllowed(deleteConfig, selectedItems.length, role)

  const labels = useMemo(
    () => new Map(catalog.candidates.map((column) => [column.key, column.label])),
    [catalog],
  )
  const chips = filterTreeToChips(state.filterTree, { fieldLabels: labels, fieldTypes, optionLabels })
  const searchHint = activeConfig.searchFields
    .slice(0, 3)
    .map((key) => catalog.missing(key).label)
    .join('、')

  function removeChip(id: string) {
    url.patch({
      filterTree: {
        ...state.filterTree,
        conditions: state.filterTree.conditions.filter((condition) => condition.id !== id),
        groups: state.filterTree.groups
          .map((group) => ({ ...group, conditions: group.conditions.filter((condition) => condition.id !== id) }))
          .filter((group) => group.conditions.length > 0),
      },
      page: 1,
      selectedIds: [],
    })
  }

  function cycleSort() {
    const current = activeConfig.sort[0]
    const presets = [
      { field: 'update_time', order: 'DESC' as const },
      { field: 'priority', order: 'ASC' as const },
      { field: 'end_time', order: 'ASC' as const },
    ]
    const index = presets.findIndex((preset) => preset.field === current?.field && preset.order === current?.order)
    url.patch({ sort: [presets[(index + 1) % presets.length]], page: 1, selectedIds: [] })
  }

  const estimate = useCallback(async (tree: FilterTree) => {
    const result = await pmWorkItemApi.page(estimateCountSpec(buildQuerySpec({
      projectId: projectId!,
      typeCode,
      viewId: activeView,
      filterTree: tree,
      q: state.q,
      searchFields: activeConfig.searchFields,
      sort: displayConfig.sort,
      page: 1,
      size: 1,
      currentUserId: currentUser?.id != null ? String(currentUser.id) : undefined,
      users,
      catalog,
      riskHighValue,
      enabledTypeCodes: enabledTypes.map((type) => type.code),
    })))
    return Number(result.total)
  }, [projectId, typeCode, activeView, state.q, activeConfig.searchFields, displayConfig.sort,
    currentUser?.id, users, catalog, riskHighValue, enabledTypes])

  if (!projectId) {
    return <p className="text-sm text-destructive">项目地址无效</p>
  }

  const cellContext: CellContext = {
    projectId,
    types: enabledTypes,
    statusByType,
    usersById,
    optionLabels,
  }
  const tableColumns = [
    ...displayConfig.listFields.map((key) => {
      const meta = catalog.missing(key)
      return {
        id: key,
        header: meta.label,
        sortable: meta.sortable,
        headClassName: meta.widthClass,
        className: meta.widthClass,
        cell: (row: PmWorkItem) => renderPmCell(row, meta, cellContext),
      }
    }),
    ...(deleteConfig.enabled ? [{
      id: 'actions',
      header: '',
      headClassName: 'w-11',
      className: 'w-11',
      cell: (row: PmWorkItem) => (
        <Button
          type="button"
          size="icon"
          variant="ghost"
          aria-label={`删除 ${row.title}`}
          disabled={!deleteEnabled}
          onClick={() => setDeleteTarget(row)}
        >
          <Trash2 className="size-4" />
        </Button>
      ),
    }] : []),
  ]

  const bulkActions = [
    {
      key: 'edit',
      label: '批量编辑',
      disabled: !actionAllowed(updateConfig, selectedItems.length, role),
      onSelect: () => url.setOverlay('bulk-edit'),
    },
    {
      key: 'transition',
      label: '状态流转',
      disabled: !actionAllowed(transitionConfig, selectedItems.length, role),
      onSelect: () => url.setOverlay('transition'),
    },
    ...(deleteConfig.enabled ? [{
      key: 'delete',
      label: '删除',
      variant: 'destructive' as const,
      disabled: !deleteEnabled,
      onSelect: () => url.setOverlay('delete'),
    }] : []),
  ]

  return (
    <>
      <ListPageShell
        title={typeCode ? typeLabel(typeCode, enabledTypes) : '工作项'}
        description={[
          projectQuery.data?.name,
          activeSavedView?.name ?? activeBuiltIn?.label,
          `共 ${total} 条`,
          latestUpdate ? `更新于 ${formatDateTime(latestUpdate)}` : '',
        ].filter(Boolean).join(' · ')}
        tabs={!forbidden ? (
          <WorkItemViewTabs
            activeView={activeView}
            savedViews={savedViews}
            dirty={dirty}
            onSelect={(view) => url.switchView(view)}
          />
        ) : undefined}
        actions={forbidden ? undefined : (
          <Button type="button" onClick={() => { setCreateType(typeCode ?? enabledTypes[0]?.code ?? 'task'); setCreateOpen(true) }}>
            <Plus />
            新建
          </Button>
        )}
        toolbar={forbidden ? undefined : (
          <div className="flex w-full flex-wrap items-center gap-2">
            <SearchInput
              value={state.q}
              onChange={(q) => url.patch({ q, page: 1, selectedIds: [] })}
              placeholder={`搜索 ${searchHint}${activeConfig.searchFields.length > 3 ? ' 等' : ''}`}
              debounceMs={350}
            />
            <Button type="button" variant="outline" size="sm" onClick={() => url.setOverlay('view-config', 'search')}>
              {activeConfig.searchFields.length} 个字段
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={() => url.setOverlay('filter')}>
              <Filter className="size-4" />
              筛选{chips.length ? ` (${chips.length})` : ''}
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={() => url.setOverlay('view-config', 'columns')}>
              <Columns3 className="size-4" />
              显示
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={cycleSort}>
              <ArrowDownUp className="size-4" />
              排序
            </Button>
            <div className="flex overflow-hidden rounded-md border">
              {(['standard', 'compact'] as const).map((density) => (
                <button
                  key={density}
                  type="button"
                  className={`px-2.5 py-1 text-xs ${activeConfig.density === density ? 'bg-primary text-primary-foreground' : 'bg-background hover:bg-muted'}`}
                  onClick={() => url.patchConfig({ density })}
                >
                  {density === 'standard' ? '标准' : '紧凑'}
                </button>
              ))}
            </div>
            <span className="ml-auto text-sm text-muted-foreground">共 {total} 条</span>
          </div>
        )}
        filters={forbidden ? undefined : (
          <FilterBar
            activeFilters={chips.map((chip) => ({ key: chip.key, label: chip.label, onRemove: () => removeChip(chip.key) }))}
            onReset={() => url.patch({ filterTree: emptyFilterTree(), page: 1, selectedIds: [] })}
            extra={(
              <div className="flex items-center gap-2">
                {chips.length ? <span className="text-xs text-muted-foreground">筛后 {total} 条</span> : null}
                <Button type="button" size="sm" variant="outline" onClick={() => url.setOverlay('filter')}>
                  <Plus className="size-3.5" />添加筛选
                </Button>
              </div>
            )}
          />
        )}
        loading={itemsQuery.isPending && !metaError}
        error={!forbidden ? metaError ?? (itemsQuery.isError ? (itemsQuery.error as Error).message || '工作项加载失败' : undefined) : undefined}
        onRetry={() => { void typesQuery.refetch(); void itemsQuery.refetch() }}
        forbidden={forbidden ? '当前账号没有该项目的访问权限。' : undefined}
        empty={!forbidden && !itemsQuery.isPending && !itemsQuery.isError && rows.length === 0}
        emptyTitle="没有匹配的工作项"
        footer={!forbidden ? (
          <Pagination
            page={Math.min(state.page, pageCount)}
            pageCount={pageCount}
            total={total}
            pageSize={state.size}
            onPageChange={(page) => url.patch({ page, selectedIds: [] })}
            onPageSizeChange={(size) => url.patch({ size, page: 1, selectedIds: [] })}
          />
        ) : undefined}
        emptyContent={(
          <div className="flex flex-col items-center gap-2">
            <span>当前类型与筛选条件下没有结果，可以清空搜索或筛选后重试。</span>
            <div className="flex gap-2">
              <Button type="button" size="sm" variant="outline" onClick={() => url.patch({ q: '', page: 1 })}>清空搜索</Button>
              <Button type="button" size="sm" variant="outline" onClick={() => url.patch({ filterTree: emptyFilterTree(), page: 1 })}>清空筛选</Button>
            </div>
          </div>
        )}
      >
        {!itemsQuery.isPending && !forbidden ? (
          <DataTable<PmWorkItem>
            columns={tableColumns}
            data={rows}
            getRowId={(row) => asId(row.id)}
            loading={itemsQuery.isFetching}
            selectable={!forbidden}
            selectedIds={state.selectedIds}
            onSelectionChange={(ids) => url.patch({ selectedIds: ids })}
            sorts={displayConfig.sort}
            onSortChange={(sorts) => url.patch({ sort: sorts.length ? sorts : undefined, page: 1, selectedIds: [] })}
            density={displayConfig.density === 'compact' ? 'compact' : 'standard'}
            stickyHeader
            fixed
          />
        ) : null}
      </ListPageShell>

      {!forbidden ? (
        <BulkActionBar
          selectedCount={selectedItems.length}
          actions={bulkActions}
          onClear={() => url.patch({ selectedIds: [] })}
        />
      ) : null}

      <ViewConfigDrawer
        open={state.overlay === 'view-config'}
        onOpenChange={(open) => {
          if (!open) setPreviewConfig(null)
          url.setOverlay(open ? 'view-config' : undefined, state.configTab)
        }}
        tab={state.configTab}
        onTabChange={(tab) => url.setOverlay('view-config', tab)}
        catalog={catalog}
        config={activeConfig}
        saving={saveView.isPending}
        onSave={(draft) => saveView.mutate(draft)}
        onReset={() => { setPreviewConfig(null); url.resetConfig() }}
        onPreview={(draft) => setPreviewConfig(draft)}
      />

      <FilterBuilder
        open={state.overlay === 'filter'}
        onOpenChange={(open) => url.setOverlay(open ? 'filter' : undefined)}
        tree={state.filterTree}
        catalog={catalog}
        filterFields={activeConfig.filterFields}
        optionLabels={optionLabels}
        users={users}
        currentUserId={currentUser?.id != null ? String(currentUser.id) : undefined}
        riskHighValue={riskHighValue}
        onApply={(tree) => url.patch({ filterTree: tree, page: 1, selectedIds: [], overlay: undefined })}
        onEstimate={estimate}
      />

      <BulkEditDialog
        open={state.overlay === 'bulk-edit' && selectedItems.length > 0}
        onOpenChange={(open) => url.setOverlay(open ? 'bulk-edit' : undefined)}
        items={selectedItems}
        catalog={catalog}
        config={updateConfig}
        optionLabels={optionLabels}
        users={users}
        onFinished={async (result) => {
          await invalidateList()
          if (result.failed === 0 && result.skipped === 0) {
            url.patch({ selectedIds: [], overlay: undefined })
            toast.success(`已更新 ${result.success} 项`)
          } else {
            const retryIds = result.items.filter((item) => item.outcome !== 'SUCCESS').map((item) => item.id)
            url.patch({ selectedIds: retryIds })
            toast.message(`成功 ${result.success}，跳过 ${result.skipped}，失败 ${result.failed}`)
          }
        }}
      />

      <TransitionDialog
        open={state.overlay === 'transition' && selectedItems.length > 0}
        onOpenChange={(open) => url.setOverlay(open ? 'transition' : undefined)}
        items={selectedItems}
        statuses={allStatuses}
        onFinished={async (result) => {
          await invalidateList()
          if (result.failed === 0 && result.skipped === 0) {
            url.patch({ selectedIds: [], overlay: undefined })
            toast.success(`已流转 ${result.success} 项`)
          } else {
            const retryIds = result.items.filter((item) => item.outcome !== 'SUCCESS').map((item) => item.id)
            url.patch({ selectedIds: retryIds })
            toast.message(`成功 ${result.success}，跳过 ${result.skipped}，失败 ${result.failed}`)
          }
        }}
      />

      <BulkDeleteDialog
        open={state.overlay === 'delete' && selectedItems.length > 0}
        onOpenChange={(open) => url.setOverlay(open ? 'delete' : undefined)}
        items={selectedItems}
        onFinished={async (outcomes) => {
          await invalidateList()
          const failedIds = outcomes.filter((outcome) => !outcome.success).map((outcome) => outcome.id)
          if (!failedIds.length) {
            url.patch({ selectedIds: [], overlay: undefined })
            toast.success(`已删除 ${outcomes.length} 个工作项`)
          } else {
            url.patch({ selectedIds: failedIds, overlay: undefined })
            toast.error(`删除完成：成功 ${outcomes.length - failedIds.length}，失败 ${failedIds.length}`)
          }
        }}
      />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}
        title="删除工作项？"
        description={deleteTarget ? `「${deleteTarget.title}」删除后不可恢复。` : undefined}
        confirmText="删除"
        destructive
        onConfirm={() => {
          if (!deleteTarget?.id) return
          void pmWorkItemApi.delete(deleteTarget.id).then(() => {
            toast.success('事项已删除')
            setDeleteTarget(null)
            void invalidateList()
          }).catch((error: Error) => toast.error(error.message || '删除失败'))
        }}
      />

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建工作项</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            {!typeCode ? (
              <div className="flex flex-col gap-1.5">
                <Label>类型</Label>
                <Select value={createType} onValueChange={setCreateType}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {enabledTypes.map((type) => (
                      <SelectItem key={type.code} value={type.code}>{type.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="pm-create-title">标题</Label>
              <Input
                id="pm-create-title"
                value={createTitle}
                onChange={(event) => setCreateTitle(event.target.value)}
                placeholder="简要说明"
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button
              type="button"
              disabled={createItem.isPending || !createTitle.trim()}
              onClick={() => createItem.mutate()}
            >
              创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
