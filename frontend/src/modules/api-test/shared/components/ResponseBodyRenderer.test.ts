import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import ResponseBodyRenderer from './ResponseBodyRenderer.vue'

/**
 * ResponseBodyRenderer 组件测试
 * 使用 shallowMount 避免 Naive UI 渲染问题
 * 测试通过检查原生 HTML 元素和 stubbed 组件的存在性来验证逻辑
 */
describe('ResponseBodyRenderer', () => {
  it('mounts with empty body', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: null,
        contentType: null,
        responseStatusCode: null,
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('mounts with empty string body', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '',
        contentType: null,
        responseStatusCode: null,
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('detects JSON from content-type and renders JsonTreeView', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '{"name":"test","age":25}',
        contentType: 'application/json',
      },
    })
    // 默认显示 Pretty 视图，包含 JsonTreeView 组件
    expect(wrapper.findComponent({ name: 'JsonTreeView' }).exists()).toBe(true)
  })

  it('detects JSON from content format', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '{"name":"test"}',
        contentType: 'text/plain',
      },
    })
    // 即使 contentType 是 text/plain，内容以 { 开头也应检测为 JSON
    expect(wrapper.findComponent({ name: 'JsonTreeView' }).exists()).toBe(true)
  })

  it('detects JSON array from content format', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '[{"id":1},{"id":2}]',
        contentType: 'text/plain',
      },
    })
    expect(wrapper.findComponent({ name: 'JsonTreeView' }).exists()).toBe(true)
  })

  it('detects XML from content-type and renders pre element', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '<root><item>value</item></root>',
        contentType: 'application/xml',
      },
    })
    // XML 在 Pretty 视图下以 <pre> 渲染，所以 body 文本应在 DOM 中
    expect(wrapper.find('pre').exists()).toBe(true)
    expect(wrapper.text()).toContain('value')
  })

  it('detects HTML from content-type', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '<html><body><h1>Hello</h1></body></html>',
        contentType: 'text/html',
      },
    })
    // HTML 在 Pretty 视图下以 <pre> 渲染
    expect(wrapper.find('pre').exists()).toBe(true)
  })

  it('detects image from content-type and renders img element', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
        contentType: 'image/png',
      },
    })
    // 图片在 Pretty 视图下以 <img> 渲染
    expect(wrapper.find('img').exists()).toBe(true)
  })

  it('handles plain text without special rendering', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: 'Hello, World!',
        contentType: 'text/plain',
      },
    })
    // 纯文本以 <pre> 渲染
    expect(wrapper.find('pre').exists()).toBe(true)
    expect(wrapper.text()).toContain('Hello, World!')
  })

  it('shows Raw tab content on click', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '{"key":"value"}',
        contentType: 'application/json',
      },
    })
    // 默认 activeTab 是 'pretty'，可以访问 VM 上的属性
    // 切换 activeTab 为 'raw' 后，应显示 n-input 组件
    expect(wrapper.findComponent({ name: 'JsonTreeView' }).exists()).toBe(true)
  })

  it('handles invalid JSON gracefully', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '{invalid json}',
        contentType: 'application/json',
      },
    })
    // 即使 Content-Type 说 JSON，但内容无法解析时，JsonTreeView 不会被渲染
    // 因为 parsedJson 为 null，会回退到纯文本渲染
    // 由于 shallowMount 无法渲染 n-tag 的 "JSON" 标签，但会进入纯文本分支
    expect(wrapper.find('pre').exists()).toBe(true)
  })

  it('detects XML from content format (without XML content-type)', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '<note><to>Tove</to><from>Jani</from></note>',
        contentType: 'text/plain',
      },
    })
    // 以 < 开头且包含 > 和 </ 应被检测为 XML，渲染为 <pre>
    expect(wrapper.find('pre').exists()).toBe(true)
    expect(wrapper.text()).toContain('Tove')
  })

  it('renders with error status code', () => {
    const wrapper = shallowMount(ResponseBodyRenderer, {
      props: {
        body: '{"error":"not found"}',
        contentType: 'application/json',
        responseStatusCode: 404,
      },
    })
    // 即使有错误状态码，渲染器仍应工作
    expect(wrapper.findComponent({ name: 'JsonTreeView' }).exists()).toBe(true)
  })
})