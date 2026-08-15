import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import HistorySidebarList from './HistorySidebarList.vue'

describe('HistorySidebarList', () => {
  it('renders rows and emits select', async () => {
    const wrapper = mount(HistorySidebarList, {
      props: {
        records: [{
          id: 7,
          definitionId: 1,
          environmentId: null,
          name: 'GET /users',
          requestUrl: '/users',
          requestMethod: 'GET',
          responseStatusCode: 200,
          responseSize: 1,
          durationMs: 10,
          status: 'SUCCESS',
          allAssertionsPassed: true,
          createTime: '2026-08-15 12:00:00',
        }],
        loading: false,
      },
    })
    expect(wrapper.get('[data-testid="history-row-7"]').exists()).toBe(true)
    await wrapper.get('[data-testid="history-row-7"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([[7]])
  })

  it('shows empty copy when no records', () => {
    const wrapper = mount(HistorySidebarList, { props: { records: [], loading: false } })
    expect(wrapper.text()).toContain('发送请求后会出现在这里')
  })
})
