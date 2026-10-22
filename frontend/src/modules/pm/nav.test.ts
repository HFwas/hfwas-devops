import { CircleDot } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import {
  ALL_ITEMS_CODE,
  isAllItemsType,
  isProjectItemActive,
  projectBoardPath,
  projectContextFromPath,
  projectItemsPath,
  projectTypeIcon,
  projectWorkflowPath,
} from '@/modules/pm/nav'

describe('projectContextFromPath', () => {
  it('reads the project and type from project routes', () => {
    expect(projectContextFromPath('/pm/projects/12/items/task')).toEqual({ projectId: '12', typeCode: 'task' })
    expect(projectContextFromPath('/pm/projects/12/items/task/99')).toEqual({ projectId: '12', typeCode: 'task' })
    expect(projectContextFromPath('/pm/projects/12/items/all')).toEqual({ projectId: '12', typeCode: 'all' })
    expect(projectContextFromPath('/pm/projects/12/board/bug')).toEqual({ projectId: '12', typeCode: 'bug' })
    expect(projectContextFromPath('/pm/projects/12/settings/workflow/requirement')).toEqual({
      projectId: '12',
      typeCode: 'requirement',
    })
  })

  it('rejects the project list and non-numeric project ids', () => {
    expect(projectContextFromPath('/pm/projects')).toBeNull()
    expect(projectContextFromPath('/pm/projects/abc/items/task')).toBeNull()
    expect(projectContextFromPath('/pipeline/pipelines')).toBeNull()
  })
})

describe('project paths', () => {
  it('builds project scoped hrefs', () => {
    expect(projectItemsPath('12', 'task')).toBe('/pm/projects/12/items/task')
    expect(projectItemsPath('12', ALL_ITEMS_CODE)).toBe('/pm/projects/12/items/all')
    expect(projectBoardPath('12', 'task')).toBe('/pm/projects/12/board/task')
    expect(projectWorkflowPath('12', 'task')).toBe('/pm/projects/12/settings/workflow/task')
  })

  it('highlights the active item type, including detail routes', () => {
    expect(isProjectItemActive('/pm/projects/12/items/task', '12', 'task')).toBe(true)
    expect(isProjectItemActive('/pm/projects/12/items/task/99', '12', 'task')).toBe(true)
    expect(isProjectItemActive('/pm/projects/12/items/bug', '12', 'task')).toBe(false)
    expect(isProjectItemActive('/pm/projects/12/items/all', '12', 'all')).toBe(true)
    expect(isAllItemsType('all')).toBe(true)
    expect(isAllItemsType('task')).toBe(false)
    expect(isProjectItemActive('/pm/projects/12/board/task', '12', 'task')).toBe(false)
  })
})

describe('projectTypeIcon', () => {
  it('falls back to a generic icon for custom types', () => {
    expect(projectTypeIcon('bug')).not.toBe(CircleDot)
    expect(projectTypeIcon('custom_story')).toBe(CircleDot)
  })
})
