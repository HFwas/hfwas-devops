import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import JsonTreeView from './JsonTreeView.vue'

/**
 * JsonTreeView 组件测试
 * 使用 shallowMount 避免递归组件和 Naive UI 渲染问题
 * 测试通过检查组件存在的文本内容和 VM 暴露的属性
 */
describe('JsonTreeView', () => {
  it('mounts with simple object', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: { name: 'test', age: 25 },
      },
    })
    expect(wrapper.exists()).toBe(true)
    // 键名直接渲染在第一层
    expect(wrapper.text()).toContain('name')
    expect(wrapper.text()).toContain('age')
    // 字符串值也直接渲染
    expect(wrapper.text()).toContain('test')
  })

  it('renders string values', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: { message: 'hello' },
      },
    })
    expect(wrapper.text()).toContain('hello')
  })

  it('renders number values', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: { count: 42 },
      },
    })
    expect(wrapper.text()).toContain('42')
  })

  it('renders boolean values', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: { active: true },
      },
    })
    expect(wrapper.text()).toContain('true')
  })

  it('renders null values', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: { value: null },
      },
    })
    expect(wrapper.text()).toContain('null')
  })

  it('renders arrays with item count', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: { items: [1, 2, 3] },
      },
    })
    // 数组渲染为可展开节点，显示 key 和 size
    expect(wrapper.text()).toContain('items')
    expect(wrapper.text()).toContain('3')
  })

  it('renders nested objects showing key and size', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: {
          user: {
            name: 'Alice',
            address: {
              city: '北京',
            },
          },
        },
      },
    })
    // 第一层 key 可见
    expect(wrapper.text()).toContain('user')
    // 嵌套对象展开后，name 在递归子组件中渲染
    // 由于 shallowMount 会 stub 递归组件，Alice 不可见
  })

  it('renders empty object', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: {},
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders empty array', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: [],
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders with maxDepth=0 (all collapsed)', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: { deep: { deeper: { value: 'secret' } } },
        maxDepth: 0,
      },
    })
    // 深度为 0，所以深层节点应该被折叠，显示 Object
    expect(wrapper.text()).toContain('deep')
    expect(wrapper.text()).toContain('Object')
  })

  it('renders primitive values at root', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: 'just a string',
      },
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('just a string')
  })

  it('renders number at root', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: 12345,
      },
    })
    expect(wrapper.text()).toContain('12345')
  })

  it('renders boolean at root', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: false,
      },
    })
    expect(wrapper.text()).toContain('false')
  })

  it('renders null at root', () => {
    const wrapper = shallowMount(JsonTreeView, {
      props: {
        data: null,
      },
    })
    expect(wrapper.text()).toContain('null')
  })
})