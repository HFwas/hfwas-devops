import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import KeyValueEditor from './KeyValueEditor.vue'

/**
 * KeyValueEditor 组件测试
 * 使用 shallowMount 避免 Naive UI 内部渲染问题
 * 测试通过直接调用组件暴露的方法来验证逻辑
 */
describe('KeyValueEditor', () => {
  it('mounts successfully', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: { 'Content-Type': 'application/json', Authorization: 'Bearer token' },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('emits update:pairs when adding a new row', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: { key1: 'value1' },
      },
    })
    ;(wrapper.vm as any).handleAdd()
    expect(wrapper.emitted('update:pairs')).toBeTruthy()
    const emitted = wrapper.emitted('update:pairs')![0][0] as Record<string, string>
    expect(emitted).toHaveProperty('')
    expect(emitted['']).toBe('')
  })

  it('emits update:pairs when updating a key', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: { oldKey: 'value1' },
      },
    })
    ;(wrapper.vm as any).updateKey(0, 'newKey')
    expect(wrapper.emitted('update:pairs')).toBeTruthy()
    const emitted = wrapper.emitted('update:pairs')![0][0] as Record<string, string>
    expect(emitted).not.toHaveProperty('oldKey')
    expect(emitted).toHaveProperty('newKey')
    expect(emitted['newKey']).toBe('value1')
  })

  it('emits update:pairs when updating a value', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: { key1: 'oldValue' },
      },
    })
    ;(wrapper.vm as any).updateValue(0, 'newValue')
    expect(wrapper.emitted('update:pairs')).toBeTruthy()
    const emitted = wrapper.emitted('update:pairs')![0][0] as Record<string, string>
    expect(emitted['key1']).toBe('newValue')
  })

  it('emits update:pairs when deleting a row', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: { key1: 'value1', key2: 'value2' },
      },
    })
    ;(wrapper.vm as any).removeRow(1)
    expect(wrapper.emitted('update:pairs')).toBeTruthy()
    const emitted = wrapper.emitted('update:pairs')![0][0] as Record<string, string>
    expect(emitted).not.toHaveProperty('key2')
    expect(emitted).toHaveProperty('key1')
  })

  it('mounts with empty pairs', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: {},
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('uses custom placeholders', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: {},
        keyPlaceholder: 'Header Name',
        valuePlaceholder: 'Header Value',
      },
    })
    expect(wrapper.props('keyPlaceholder')).toBe('Header Name')
    expect(wrapper.props('valuePlaceholder')).toBe('Header Value')
  })

  it('produces correct pairList from pairs', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: { key1: 'value1', key2: 'value2' },
      },
    })
    const pairList = (wrapper.vm as any).pairList
    expect(pairList).toHaveLength(2)
    expect(pairList[0]).toEqual({ key: 'key1', value: 'value1' })
    expect(pairList[1]).toEqual({ key: 'key2', value: 'value2' })
  })

  it('removes the correct key when delete called', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: { a: '1', b: '2', c: '3' },
      },
    })
    ;(wrapper.vm as any).removeRow(1) // remove 'b'
    expect(wrapper.emitted('update:pairs')).toBeTruthy()
    const emitted = wrapper.emitted('update:pairs')![0][0] as Record<string, string>
    expect(emitted).toHaveProperty('a')
    expect(emitted).not.toHaveProperty('b')
    expect(emitted).toHaveProperty('c')
  })
})