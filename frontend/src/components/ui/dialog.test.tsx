import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

describe('Dialog surface', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    container = document.createElement('div')
    document.body.appendChild(container)
    root = createRoot(container)
    act(() => {
      root?.render(
        <Dialog open>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>运行参数</DialogTitle>
            </DialogHeader>
            <p>内容</p>
            <DialogFooter>底部</DialogFooter>
          </DialogContent>
        </Dialog>,
      )
    })
  })

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('uses the kite surface tokens', () => {
    const content = document.querySelector('[data-slot="dialog-content"]')
    expect(content?.className).toContain('rounded-xl')
    expect(content?.className).toContain('bg-card')
    expect(content?.className).toContain('border')
    expect(content?.className).toContain('shadow-lg')
    expect(document.querySelector('[data-slot="dialog-overlay"]')?.className).toContain('bg-black/40')
    expect(content?.querySelector('[data-slot="dialog-header"]')?.className ?? '').toContain('border-b')
    expect(content?.querySelector('[data-slot="dialog-footer"]')?.className ?? '').toContain('border-t')
  })
})
