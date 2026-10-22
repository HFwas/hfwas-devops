import { describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router'
import { renderToStaticMarkup } from 'react-dom/server'
import type { FieldDefinition, PmWorkItem, StatusDefinition } from '@/modules/pm/types'
import type { UserProfile } from '@/modules/user/types'
import { buildColumnCatalog, renderPmCell, resolveColumnMetas } from '@/modules/pm/utils/columns'

const fields: FieldDefinition[] = [
  { fieldKey: 'score', fieldName: '故事点', fieldType: 'NUMBER', systemFlag: 0 },
  { fieldKey: 'risk', fieldName: '风险', fieldType: 'SELECT', systemFlag: 0 },
]
const catalog = buildColumnCatalog(fields)
const item: PmWorkItem = {
  id: 9,
  projectId: 10,
  itemKey: 'DEMO-9',
  typeCode: 'bug',
  title: '登录页白屏',
  status: 'open',
  priority: 'high',
  assigneeId: 2,
  endTime: '2026-11-01T00:00:00',
  updateTime: '2026-10-22T10:00:00',
  customFields: { score: 8, risk: 'high' },
}
const status: StatusDefinition = {
  statusCode: 'open',
  statusName: '待处理',
  sortOrder: 1,
  isInitial: 1,
  isFinal: 0,
  transitions: [],
}
const user = { id: '2', username: 'zhangsan', displayName: '张三', role: 'user' } as UserProfile
const ctx = {
  projectId: '10',
  types: [{ code: 'bug', name: '缺陷' }],
  statusByType: new Map([['bug', new Map([['open', status]])]]),
  usersById: new Map([['2', user]]),
  optionLabels: new Map([['risk', new Map([['high', '高']])]]),
}

function render(key: string) {
  const column = catalog.missing(key)
  return renderToStaticMarkup(<MemoryRouter>{renderPmCell(item, column, ctx)}</MemoryRouter>)
}

describe('column catalog', () => {
  it('buildsCatalog', () => {
    expect(catalog.byKey.has('title')).toBe(true)
    expect(catalog.byKey.has('end_time')).toBe(true)
    expect(catalog.byKey.has('score')).toBe(true)
    expect(catalog.byKey.get('score')?.queryField).toBe('custom.score')
  })

  it('rendersMissingFieldDash', () => {
    expect(render('storyPoints')).toBe('—')
    expect(catalog.missing('storyPoints').missing).toBe(true)
    expect(resolveColumnMetas(['title', 'storyPoints'], catalog)[1].missing).toBe(true)
  })

  it('rendersCells', () => {
    expect(render('status')).toContain('待处理')
    expect(render('priority')).toContain('高')
    expect(render('assignee_id')).toContain('张三')
    expect(render('end_time')).toContain('2026-11-01')
    expect(render('score')).toContain('8')
    expect(render('risk')).toContain('高')
    expect(render('type_code')).toContain('缺陷')
  })

  it('linksWithItemType', () => {
    const html = render('title')
    expect(html).toContain('/pm/projects/10/items/bug/9')
    expect(html).toContain('登录页白屏')
  })
})
