import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router'

export function prepareActEnvironment() {
  ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
}

export function withQuery(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

export function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

export function mountRoute(path: string, pattern: string, element: ReactNode) {
  return mount(
    withQuery(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={pattern} element={element} />
        </Routes>
      </MemoryRouter>,
    ),
  )
}

export function unmount(view: { root: Root; container: HTMLElement } | null) {
  if (!view) return
  act(() => {
    view.root.unmount()
  })
  view.container.remove()
}

export async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

export async function waitForText(root: ParentNode, text: string) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (root.textContent?.includes(text)) return
    await flush()
  }
  if (!root.textContent?.includes(text)) {
    throw new Error(`未找到文本: ${text}`)
  }
}

export function setInputValue(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
  act(() => {
    setter?.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
}

export function click(element: Element | null | undefined) {
  act(() => {
    ;(element as HTMLElement | null)?.click()
  })
}

export function buttonByText(root: ParentNode, text: string) {
  return Array.from(root.querySelectorAll('button')).find((button) => button.textContent?.trim() === text)
}

export function inputByLabel(root: ParentNode, label: string) {
  const labelNode = Array.from(root.querySelectorAll('label')).find((node) => node.textContent?.trim() === label)
  const id = labelNode?.getAttribute('for')
  return id ? (root.querySelector(`#${id}`) as HTMLInputElement | null) : null
}
