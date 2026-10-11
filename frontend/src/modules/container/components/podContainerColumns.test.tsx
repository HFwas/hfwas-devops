import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import { PodContainerTable } from '@/modules/container/components/podContainerColumns'

describe('PodContainerTable', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('keeps ready and restart columns narrow beside a truncated image', () => {
    container = document.createElement('div')
    document.body.appendChild(container)
    root = createRoot(container)
    const image = 'registry.example.com/library/very-long-image-name:1.2.3'
    act(() => {
      root?.render(
        <PodContainerTable
          rows={[{ name: 'app', state: 'running', image, ready: true, restartCount: 2 }]}
        />,
      )
    })
    expect(container.querySelector('table')?.className).toContain('table-fixed')
    const heads = [...container.querySelectorAll('th')]
    expect(heads.find((head) => head.textContent === '就绪')?.className).toContain('w-16')
    expect(heads.find((head) => head.textContent === '重启')?.className).toContain('w-16')
    const imageCell = [...container.querySelectorAll('td')].find((cell) => cell.textContent === image)
    expect(imageCell?.className).toContain('overflow-hidden')
    expect(imageCell?.querySelector('span')?.className).toContain('truncate')
    expect(imageCell?.querySelector('span')?.getAttribute('title')).toBe(image)
  })
})
