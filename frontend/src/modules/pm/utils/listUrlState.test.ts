import { describe, expect, it } from 'vitest'
import type { FieldDefinition } from '@/modules/pm/types'
import { buildColumnCatalog } from '@/modules/pm/utils/columns'
import {
  DEFAULT_PAGE_SIZE,
  effectiveConfig,
  isListConfigDirty,
  parsePmListUrl,
  pruneSelection,
  serializePmListUrl,
} from '@/modules/pm/utils/listUrlState'
import { defaultViewConfig } from '@/modules/pm/utils/viewConfig'
import { emptyFilterTree } from '@/modules/pm/utils/filterTree'

const fields: FieldDefinition[] = [
  { fieldKey: 'risk', fieldName: '风险', fieldType: 'SELECT', systemFlag: 0, batchEditable: 1 },
  { fieldKey: 'score', fieldName: '分数', fieldType: 'NUMBER', systemFlag: 0 },
]

function context() {
  const catalog = buildColumnCatalog(fields)
  const fieldTypes = new Map(catalog.candidates.map((column) => [column.key, column.filterType]))
  return { fallbackConfig: defaultViewConfig(), catalog, fieldTypes }
}

describe('list URL state', () => {
  it('defaultsAndClamping', () => {
    const state = parsePmListUrl(new URLSearchParams(), context())
    expect(state.view).toBe('dev')
    expect(state.page).toBe(1)
    expect(state.size).toBe(DEFAULT_PAGE_SIZE)
    expect(state.filterTree).toEqual(emptyFilterTree())
    expect(effectiveConfig(state, defaultViewConfig()).listFields).toEqual(
      defaultViewConfig().listFields,
    )
  })

  it('dropsInvalidValues', () => {
    const state = parsePmListUrl(
      new URLSearchParams('page=0&size=37&sort=bad:x&overlay=nope&density=huge'),
      context(),
    )
    expect(state.page).toBe(1)
    expect(state.size).toBe(DEFAULT_PAGE_SIZE)
    expect(state.sort).toBeUndefined()
    expect(state.overlay).toBeUndefined()
    expect(state.density).toBeUndefined()
  })

  it('omitsDefaultsOnSerialize', () => {
    const state = parsePmListUrl(new URLSearchParams(), context())
    const params = serializePmListUrl(state, { fallbackConfig: defaultViewConfig() })
    expect(params.toString()).toBe('')
  })

  it('roundTrips', () => {
    const original = new URLSearchParams(
      'view=mine&q=登录&cols=itemKey,title,status&search=title,description&ff=status,priority&density=compact&sort=priority:ASC,update_time:DESC&page=3&size=20&sel=1,2',
    )
    const state = parsePmListUrl(original, context())
    const params = serializePmListUrl(state, { fallbackConfig: defaultViewConfig() })
    const reparsed = parsePmListUrl(params, context())
    expect(reparsed).toEqual(state)
  })

  it('normalizesAliases', () => {
    const state = parsePmListUrl(new URLSearchParams('cols=type,assignee,updated'), context())
    expect(state.cols).toEqual(['type_code', 'assignee_id', 'update_time'])
  })

  it('detectsDirtyOnlyForConfig', () => {
    const empty = parsePmListUrl(new URLSearchParams(), context())
    const fallback = defaultViewConfig()
    expect(isListConfigDirty(empty, fallback)).toBe(false)
    const withQuery = parsePmListUrl(new URLSearchParams('q=abc&page=2'), context())
    expect(isListConfigDirty(withQuery, fallback)).toBe(false)
    const configured = parsePmListUrl(new URLSearchParams('density=compact'), context())
    expect(isListConfigDirty(configured, fallback)).toBe(true)
  })

  it('preservesCustomFilterBeforeMetadata', () => {
    const base = context()
    const early = {
      ...base,
      fieldTypes: new Map(base.fieldTypes),
      fieldsReady: false,
    }
    const tree = {
      logic: 'AND',
      conditions: [{ id: 'c1', field: 'risk', operator: 'EQ', value: 'high' }],
      groups: [],
    }
    const state = parsePmListUrl(new URLSearchParams(`f=${encodeURIComponent(JSON.stringify(tree))}&sort=risk:ASC`), early)
    expect(state.filterTree.conditions).toHaveLength(1)
    expect(state.filterTree.conditions[0].field).toBe('risk')
    expect(state.sort).toEqual([{ field: 'risk', order: 'ASC' }])
  })

  it('prunesSelectionToPage', () => {
    expect(pruneSelection(['1', '2', '3'], ['2', '3', '4'])).toEqual(['2', '3'])
  })
})
