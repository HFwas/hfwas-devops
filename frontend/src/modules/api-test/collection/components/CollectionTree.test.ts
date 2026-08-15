import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CollectionTree from './CollectionTree.vue'

describe('CollectionTree', () => {
  it('renders method-tag--POST for POST items and no folder emoji', () => {
    const wrapper = mount(CollectionTree, {
      props: {
        folders: [{
          id: 1,
          collectionId: 1,
          parentId: null,
          name: 'F',
          description: '',
          sortOrder: 0,
          children: [],
          items: [],
        }],
        items: [{
          id: 2,
          collectionId: 1,
          folderId: null,
          definitionId: 9,
          name: 'Untitled',
          description: '',
          enabled: false,
          sortOrder: 0,
          method: 'POST',
          path: '/',
        }],
      },
    })
    expect(wrapper.html()).toContain('method-tag--POST')
    expect(wrapper.html()).toContain('已禁用')
    expect(wrapper.html()).not.toContain('📁')
  })

  it('uppercases method and marks selected item', () => {
    const wrapper = mount(CollectionTree, {
      props: {
        folders: [],
        items: [{
          id: 5,
          collectionId: 1,
          folderId: null,
          definitionId: 9,
          name: 'X',
          description: '',
          enabled: true,
          sortOrder: 0,
          method: 'post',
          path: '/x',
        }],
        selectedId: 5,
      },
    })
    expect(wrapper.html()).toContain('method-tag--POST')
    expect(wrapper.text()).toMatch(/POST/)
    expect(wrapper.find('.n-tree-node--selected').exists()).toBe(true)
  })
})
