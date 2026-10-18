import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PipelineCredential } from '@/modules/pipeline/types/pipeline'

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineCredentialApi: {
    page: vi.fn(),
    list: vi.fn(),
    get: vi.fn(),
    save: vi.fn(),
    delete: vi.fn(),
  },
}))

import { pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import { CredentialDetailPage } from '@/modules/pipeline/pages/CredentialDetailPage'
import { CredentialListPage } from '@/modules/pipeline/pages/CredentialListPage'
import {
  buttonByText,
  click,
  flush,
  inputByLabel,
  mountRoute,
  prepareActEnvironment,
  setInputValue,
  unmount,
  waitForText,
} from '@/test-utils/pipeline'

const credentials: PipelineCredential[] = [
  { id: '1', name: 'git-prod', kind: 'PASSWORD', username: 'ada', updateTime: '2026-10-18T00:00:00Z' },
  { id: '2', name: 'harbor-token', kind: 'TOKEN', username: 'x-access-token', updateTime: '2026-10-17T00:00:00Z' },
]

describe('Credential pages', () => {
  let view: ReturnType<typeof mountRoute> | null = null

  beforeEach(() => {
    prepareActEnvironment()
    vi.mocked(pipelineCredentialApi.page).mockResolvedValue({
      records: credentials,
      total: credentials.length,
      current: 1,
      size: 20,
      pages: 1,
    })
    vi.mocked(pipelineCredentialApi.get).mockResolvedValue(credentials[0])
  })

  afterEach(() => {
    unmount(view)
    view = null
    vi.clearAllMocks()
  })

  it('PIPE-147: renders credential list with kind labels', async () => {
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, 'git-prod')

    expect(view.container.textContent).toContain('用户名密码')
    expect(view.container.textContent).toContain('harbor-token')
  })

  it('PIPE-149: shows empty state', async () => {
    vi.mocked(pipelineCredentialApi.page).mockResolvedValue({ records: [], total: 0, current: 1, size: 20, pages: 0 })
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, '还没有凭证')
  })

  it('PIPE-149: shows error state', async () => {
    vi.mocked(pipelineCredentialApi.page).mockRejectedValue(new Error('boom'))
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, '凭证列表加载失败')
  })

  it('PIPE-147: searches by keyword', async () => {
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, 'git-prod')

    setInputValue(view.container.querySelector('[aria-label="搜索凭证"]') as HTMLInputElement, 'git')
    await flush()

    expect(pipelineCredentialApi.page).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: 'git', pageNo: 1 }),
    )
  })

  it('PIPE-147: blocks create when name is empty', async () => {
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, 'git-prod')

    click(buttonByText(view.container, '新建凭证'))
    await waitForText(document.body, '新建凭证')
    click(buttonByText(document.body, '保存'))
    await flush()

    await waitForText(document.body, '请填写名称')
    const nameInput = inputByLabel(document.body, '名称') as HTMLInputElement
    expect(nameInput.getAttribute('aria-invalid')).toBe('true')
    expect(nameInput.getAttribute('aria-describedby')).toBe('cred-name-error')
    expect(document.activeElement).toBe(nameInput)
    expect(pipelineCredentialApi.save).not.toHaveBeenCalled()
  })

  it('PIPE-147: clears error after name is provided', async () => {
    vi.mocked(pipelineCredentialApi.save).mockResolvedValue('3')
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, 'git-prod')

    click(buttonByText(view.container, '新建凭证'))
    await waitForText(document.body, '新建凭证')
    click(buttonByText(document.body, '保存'))
    await waitForText(document.body, '请填写名称')

    setInputValue(inputByLabel(document.body, '名称') as HTMLInputElement, 'git-dev')
    await flush()
    expect(document.body.textContent).not.toContain('请填写名称')

    click(buttonByText(document.body, '保存'))
    await flush()
    expect(pipelineCredentialApi.save).toHaveBeenCalledWith(expect.objectContaining({ name: 'git-dev' }))
  })

  it('PIPE-147: creates credential from dialog', async () => {
    vi.mocked(pipelineCredentialApi.save).mockResolvedValue('3')
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, 'git-prod')

    click(buttonByText(view.container, '新建凭证'))
    await waitForText(document.body, '新建凭证')
    setInputValue(inputByLabel(document.body, '名称') as HTMLInputElement, 'git-dev')
    setInputValue(inputByLabel(document.body, '用户名') as HTMLInputElement, 'dev')
    setInputValue(inputByLabel(document.body, '密钥') as HTMLInputElement, 'secret')
    click(buttonByText(document.body, '保存'))
    await flush()

    expect(pipelineCredentialApi.save).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'git-dev', kind: 'PASSWORD', username: 'dev', secret: 'secret' }),
    )
  })

  it('PIPE-147: deletes credential after confirmation', async () => {
    vi.mocked(pipelineCredentialApi.delete).mockResolvedValue(undefined)
    view = mountRoute('/pipeline/credentials', '/pipeline/credentials', <CredentialListPage />)
    await waitForText(view.container, 'git-prod')

    click(buttonByText(view.container, '删除'))
    await waitForText(document.body, '删除凭证「git-prod」？')
    const dialog = document.querySelector('[role="alertdialog"]')
    click(buttonByText(dialog ?? document.body, '删除'))
    await flush()

    expect(pipelineCredentialApi.delete).toHaveBeenCalledWith('1')
  })

  it('PIPE-147: detail page loads and keeps secret blank on save', async () => {
    vi.mocked(pipelineCredentialApi.save).mockResolvedValue('1')
    view = mountRoute('/pipeline/credentials/1', '/pipeline/credentials/:credentialId', <CredentialDetailPage />)
    await waitForText(view.container, 'git-prod')

    expect((inputByLabel(view.container, '新密钥') as HTMLInputElement).value).toBe('')
    click(buttonByText(view.container, '保存'))
    await flush()

    expect(pipelineCredentialApi.save).toHaveBeenCalledWith(
      expect.objectContaining({ id: '1', name: 'git-prod', secret: undefined }),
    )
  })

  it('PIPE-149: detail page shows load error', async () => {
    vi.mocked(pipelineCredentialApi.get).mockRejectedValue(new Error('boom'))
    view = mountRoute('/pipeline/credentials/1', '/pipeline/credentials/:credentialId', <CredentialDetailPage />)
    await waitForText(view.container, '凭证加载失败')
  })
})
