import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

describe('AlertDialog surface', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    container = document.createElement('div')
    document.body.appendChild(container)
    root = createRoot(container)
    act(() => {
      root?.render(
        <AlertDialog open>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>删除流水线？</AlertDialogTitle>
              <AlertDialogDescription>删除后无法恢复。</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>底部</AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>,
      )
    })
  })

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('matches the dialog surface tokens', () => {
    const content = document.querySelector('[data-slot="alert-dialog-content"]')
    expect(content?.className).toContain('rounded-xl')
    expect(content?.className).toContain('bg-card')
    expect(content?.className).toContain('border')
    expect(content?.className).toContain('shadow-lg')
    expect(document.querySelector('[data-slot="alert-dialog-overlay"]')?.className).toContain('bg-black/40')
    expect(document.querySelector('[data-slot="alert-dialog-header"]')?.className ?? '').toContain('border-b')
    expect(document.querySelector('[data-slot="alert-dialog-footer"]')?.className ?? '').toContain('border-t')
  })
})
