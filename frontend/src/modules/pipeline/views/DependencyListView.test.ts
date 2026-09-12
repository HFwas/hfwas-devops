import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const pageComponentsMock = vi.fn()

vi.mock('@/modules/pipeline/api/dependency', () => ({
  dependencyApi: {
    pageComponents: pageComponentsMock,
  },
}))

import DependencyListView from '@/modules/pipeline/views/DependencyListView.vue'

describe('DependencyListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders and loads components on mount', async () => {
    pageComponentsMock.mockResolvedValue({
      records: [
        {
          id: 1,
          name: 'spring-boot-starter-web',
          groupName: 'org.springframework.boot',
          version: '3.3.0',
          language: 'java',
          purl: 'pkg:maven/...',
          license: 'Apache-2.0',
          createTime: '2026-09-12T10:00:00',
        },
      ],
      total: 1,
      size: 20,
      current: 1,
    })

    const wrapper = mount(DependencyListView)
    await flushPromises()

    expect(pageComponentsMock).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('spring-boot-starter-web')
    expect(wrapper.text()).toContain('3.3.0')
    expect(wrapper.text()).toContain('java')
  })

  it('shows empty state when no components', async () => {
    pageComponentsMock.mockResolvedValue({
      records: [],
      total: 0,
      size: 20,
      current: 1,
    })

    const wrapper = mount(DependencyListView)
    await flushPromises()

    expect(pageComponentsMock).toHaveBeenCalledOnce()
    const rows = wrapper.findAll('[data-testid="data-table-row"]')
    expect(rows.length).toBe(0)
  })
})