import { describe, expect, it } from 'vitest'
import { shellSearchCopy } from '@/shared/console/shellSearch'

describe('shellSearchCopy', () => {
  it('pipeline 作用域返回流水线搜索文案', () => {
    expect(shellSearchCopy('/pipeline/pipelines')).toEqual({
      placeholder: '搜索流水线名称',
      label: '搜索流水线',
      kind: 'pipeline',
    })
    expect(shellSearchCopy('/pipeline/pipelines/1/runs/2').kind).toBe('pipeline')
  })

  it('container 作用域保持 Pod 搜索', () => {
    expect(shellSearchCopy('/container/clusters/c1/pods')).toEqual({
      placeholder: '搜索 Pod 名称',
      label: '搜索 Pod',
      kind: 'pod',
    })
  })

  it('PM 作用域使用项目、事项、成员搜索文案', () => {
    expect(shellSearchCopy('/pm/projects')).toEqual({
      placeholder: '搜索项目、事项、成员',
      label: '搜索',
      kind: 'project',
    })
    expect(shellSearchCopy('/pm/projects/1/items/all').placeholder).toBe('搜索项目、事项、成员')
  })

  it('其它作用域保持项目搜索', () => {
    expect(shellSearchCopy('/workbench')).toEqual({
      placeholder: '搜索项目名称或编码',
      label: '搜索项目',
      kind: 'project',
    })
  })
})
