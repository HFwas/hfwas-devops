import { describe, expect, it } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { emptyPair } from '../types/keyValue'
import KeyValueEditor from './KeyValueEditor.vue'

describe('KeyValueEditor', () => {
  it('emits an updated row when patching a key', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: [{ enabled: true, key: 'old', value: '1' }, emptyPair()],
      },
    })
    ;(wrapper.vm as any).patch(0, { key: 'next' })
    const emitted = wrapper.emitted('update:pairs')![0][0] as Array<{ key: string }>
    expect(emitted[0].key).toBe('next')
    expect(emitted[emitted.length - 1].key).toBe('')
  })

  it('keeps a trailing empty row after deleting the last named row', () => {
    const wrapper = shallowMount(KeyValueEditor, {
      props: {
        pairs: [{ enabled: true, key: 'a', value: '1' }, emptyPair()],
      },
    })
    ;(wrapper.vm as any).remove(0)
    const emitted = wrapper.emitted('update:pairs')![0][0] as Array<{ key: string }>
    expect(emitted).toHaveLength(1)
    expect(emitted[0].key).toBe('')
  })
})
