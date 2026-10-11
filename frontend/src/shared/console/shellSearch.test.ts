import { describe, expect, it } from 'vitest'
import { shellSearchCopy } from '@/shared/console/shellSearch'

describe('shellSearchCopy', () => {
  it('uses container wording on container pages and project wording elsewhere', () => {
    expect(shellSearchCopy('/container/clusters/c1/pods')).toMatchObject({
      placeholder: '搜索 Pod 名称',
      label: '搜索 Pod',
      kind: 'pod',
    })
    expect(shellSearchCopy('/pm/projects').placeholder).toBe('搜索项目名称或编码')
  })
})
