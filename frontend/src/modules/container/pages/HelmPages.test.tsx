import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetHelmSession } from '@/modules/container/api/helm'
import { helmMock } from '@/modules/container/api/helmMock'
import { useContainerCluster } from '@/modules/container/clusterStore'
import { HelmInstallDialog } from '@/modules/container/components/HelmInstallDialog'

vi.mock('@/modules/container/api/helm', async () => {
  const mock = await import('@/modules/container/api/helmMock')
  return {
    HELM_USE_MOCK: false,
    helmApi: mock.helmMock,
    resetHelmSession: () => mock.helmMock.reset(),
  }
})
import { HelmChartDetailPage } from '@/modules/container/pages/HelmChartDetailPage'
import { HelmChartListPage } from '@/modules/container/pages/HelmChartListPage'
import { HelmReleaseDetailPage } from '@/modules/container/pages/HelmReleaseDetailPage'

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

function withQuery(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

async function waitForText(root: ParentNode, text: string) {
  for (let attempt = 0; attempt < 15; attempt += 1) {
    if (root.textContent?.includes(text)) return
    await flush()
  }
  expect(root.textContent).toContain(text)
}

describe('helm pages', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    useContainerCluster.setState({ currentId: 'demo-cluster', namespace: '' })
    resetHelmSession()
  })

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
    useContainerCluster.setState({ currentId: null, namespace: '' })
  })

  it('lists charts and opens the install dialog from a chart', async () => {
    const view = mount(
      withQuery(
        <MemoryRouter initialEntries={['/container/helm/charts']}>
          <Routes>
            <Route path="/container/helm/charts" element={<HelmChartListPage />} />
            <Route path="/container/helm/charts/:repositoryId/:name" element={<HelmChartDetailPage />} />
          </Routes>
        </MemoryRouter>,
      ),
    )
    root = view.root
    container = view.container
    await waitForText(container, 'nginx')
    expect(container.textContent).toContain('上传 Chart')

    const nginx = Array.from(container.querySelectorAll('a')).find((link) => link.textContent === 'nginx')
    expect(nginx).toBeTruthy()
    await act(async () => {
      nginx?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    await waitForText(document.body, 'oci://harbor.local/charts/nginx:1.2.0')
    const install = Array.from(document.body.querySelectorAll('button')).find((button) => button.textContent === '安装')
    expect(install?.disabled).toBe(false)
    await act(async () => {
      install?.click()
    })
    await waitForText(document.body, '已填入该版本的默认 values.yaml')
  })

  it('seeds chart defaults into the editable install values and submits them', async () => {
    const chart = await helmMock.getChart('repo-harbor', 'nginx', '1.2.0')
    const defaults = await helmMock.getValues(chart.repositoryId, chart.chartName, chart.version)
    const dryRun = vi.spyOn(helmMock, 'dryRunInstall')
    const view = mount(
      withQuery(
        <MemoryRouter>
          <HelmInstallDialog
            open
            onOpenChange={() => undefined}
            clusterId="demo-cluster"
            defaultNamespace="default"
            chart={{
              repositoryId: chart.repositoryId,
              chartName: chart.chartName,
              version: chart.version,
              chartRef: chart.chartRef,
              artifactId: chart.artifactId,
            }}
          />
        </MemoryRouter>,
      ),
    )
    root = view.root
    container = view.container
    const editor = await waitForEditor('helm-install-values', 'replicaCount: 1')
    expect(editor.readOnly).toBe(false)
    expect(editor.value).toBe(defaults.valuesYaml)
    expect(document.body.textContent).toContain('已填入该版本的默认 values.yaml')
    expect(document.getElementById('helm-install-custom')).toBeNull()

    const preview = Array.from(document.body.querySelectorAll('button')).find((button) => button.textContent?.includes('试运行'))
    expect(preview?.hasAttribute('disabled')).toBe(false)
    await act(async () => {
      preview?.click()
    })
    await flush()
    expect(dryRun).toHaveBeenCalledWith(
      'demo-cluster',
      'default',
      expect.objectContaining({ valuesYaml: defaults.valuesYaml }),
    )
    dryRun.mockRestore()
  })

  it('blocks install dry-run when values YAML is invalid and allows an empty override', async () => {
    const chart = await helmMock.getChart('repo-harbor', 'nginx', '1.2.0')
    const dryRun = vi.spyOn(helmMock, 'dryRunInstall')
    const view = mount(
      withQuery(
        <MemoryRouter>
          <HelmInstallDialog
            open
            onOpenChange={() => undefined}
            clusterId="demo-cluster"
            defaultNamespace="default"
            chart={{
              repositoryId: chart.repositoryId,
              chartName: chart.chartName,
              version: chart.version,
              chartRef: chart.chartRef,
              artifactId: chart.artifactId,
            }}
          />
        </MemoryRouter>,
      ),
    )
    root = view.root
    container = view.container
    const editor = await waitForEditor('helm-install-values', 'replicaCount: 1')
    setTextArea(editor, 'replicaCount: [\n')
    await flush()
    expect(document.body.textContent).toContain('Values 不是合法的 YAML 对象')

    const preview = () => Array.from(document.body.querySelectorAll('button')).find((button) => button.textContent?.includes('试运行'))
    await act(async () => {
      preview()?.click()
    })
    expect(dryRun).not.toHaveBeenCalled()
    expect(document.body.querySelector('[role="alert"]')?.textContent).toContain('Values 不是合法的 YAML 对象')

    setTextArea(editor, '')
    await flush()
    expect(document.body.textContent).not.toContain('Values 不是合法的 YAML 对象')
    await act(async () => {
      preview()?.click()
    })
    await flush()
    expect(dryRun).toHaveBeenCalledWith('demo-cluster', 'default', expect.objectContaining({ valuesYaml: '' }))
    dryRun.mockRestore()
  })

  it('shows release detail tabs and the upgrade drawer', async () => {
    const view = mount(
      withQuery(
        <MemoryRouter initialEntries={['/container/helm/releases/default/demo-nginx']}>
          <Routes>
            <Route path="/container/helm/releases/:namespace/:name" element={<HelmReleaseDetailPage />} />
          </Routes>
        </MemoryRouter>,
      ),
    )
    root = view.root
    container = view.container
    await waitForText(container, 'demo-nginx')
    expect(container.textContent).toContain('资源')
    expect(container.textContent).toContain('历史')

    const history = Array.from(container.querySelectorAll('[role="tab"]')).find((tab) => tab.textContent === '历史')
    await act(async () => {
      history?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    await waitForText(container, '回滚')

    const upgrade = Array.from(container.querySelectorAll('button')).find((button) => button.textContent === '升级')
    await act(async () => {
      upgrade?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    await waitForText(document.body, '保留当前自定义 Values')
    expect(document.body.textContent).toContain('重置为所选版本的默认 Values')
    const editor = await waitForEditor('helm-upgrade-values', 'replicaCount: 2')
    expect(editor.readOnly).toBe(false)
    expect(editor.value).toContain('replicaCount: 2')
    const defaults = await waitForEditor('helm-upgrade-defaults', 'tag: stable')
    expect(defaults.readOnly).toBe(true)
    expect(defaults.value).toContain('tag: stable')
    expect(document.body.textContent).toContain('只读对照，不会提交')
  })
})

async function waitForEditor(id: string, expected: string) {
  for (let attempt = 0; attempt < 15; attempt += 1) {
    const editor = document.getElementById(id)
    if (editor instanceof HTMLTextAreaElement && editor.value.includes(expected)) return editor
    await flush()
  }
  const editor = document.getElementById(id)
  expect(editor).toBeInstanceOf(HTMLTextAreaElement)
  expect((editor as HTMLTextAreaElement).value).toContain(expected)
  return editor as HTMLTextAreaElement
}

function setTextArea(element: HTMLTextAreaElement, value: string) {
  const descriptor = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')
  descriptor?.set?.call(element, value)
  act(() => {
    element.dispatchEvent(new Event('input', { bubbles: true }))
  })
}
