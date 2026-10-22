import { describe, expect, it } from 'vitest'
import type { PmSavedView } from '@/modules/pm/types'
import { emptyFilterTree } from '@/modules/pm/utils/filterTree'
import {
  BUILT_IN_VIEWS,
  configToSavedColumns,
  defaultViewConfig,
  isViewDirty,
  normalizeSavedView,
  sameViewConfig,
} from '@/modules/pm/utils/viewConfig'

describe('view config', () => {
  it('definesBuiltInViews', () => {
    expect(BUILT_IN_VIEWS.map((view) => view.id)).toEqual(['dev', 'mine', 'week', 'blocked'])
    expect(BUILT_IN_VIEWS[0].config.listFields).toEqual(defaultViewConfig().listFields)
    expect(BUILT_IN_VIEWS[1].config.sort).toEqual([{ field: 'priority', order: 'ASC' }])
    expect(BUILT_IN_VIEWS[2].config.sort).toEqual([{ field: 'end_time', order: 'ASC' }])
  })

  it('normalizesLegacySavedView', () => {
    const view: PmSavedView = {
      projectId: 1,
      name: '旧视图',
      querySpec: {},
      columns: [{ fieldKey: 'title' }, { fieldKey: 'status' }],
    }
    const normalized = normalizeSavedView(view)
    expect(normalized.config.listFields).toEqual(['title', 'status'])
    expect(normalized.config.searchFields).toEqual(defaultViewConfig().searchFields)
  })

  it('normalizesCurrentSavedView', () => {
    const config = { ...defaultViewConfig(), density: 'compact' as const }
    const view: PmSavedView = {
      projectId: 1,
      name: '新视图',
      querySpec: {},
      columns: configToSavedColumns(config, emptyFilterTree()),
    }
    const normalized = normalizeSavedView(view)
    expect(normalized.config).toEqual(config)
    expect(normalized.filterTree).toEqual(emptyFilterTree())
  })

  it('buildsSavedColumns', () => {
    const config = defaultViewConfig()
    const columns = configToSavedColumns(config, emptyFilterTree())
    expect(columns).toHaveLength(1)
    expect(columns[0].listFields).toEqual(config.listFields)
    expect(JSON.parse(JSON.stringify(columns))).toEqual(columns)
  })

  it('detectsDirtyConfig', () => {
    const base = defaultViewConfig()
    expect(isViewDirty(
      { config: base, filterTree: emptyFilterTree() },
      { config: base, filterTree: emptyFilterTree() },
    )).toBe(false)
    expect(isViewDirty(
      { config: { ...base, density: 'compact' }, filterTree: emptyFilterTree() },
      { config: base, filterTree: emptyFilterTree() },
    )).toBe(true)
    expect(sameViewConfig(base, { ...base })).toBe(true)
  })
})
