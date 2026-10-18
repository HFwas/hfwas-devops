import { expect, test } from '@playwright/test'

/**
 * 流水线冻结原型的 E2E。
 *
 * 目标是验证原型的可点击链路与状态反馈，不代表真实后端行为。
 * 选择器优先使用 data-* 与可见文案，避免绑定 CSS 层级。
 */

const visibleRows = (page: import('@playwright/test').Page) =>
  page.locator('tr[data-row]:visible')

test.describe('流水线原型 E2E', () => {
  test('E2E-PIPE-01 列表：搜索与状态筛选只影响当前页', async ({ page }) => {
    await page.goto('/index.html#/list')
    await expect(page.getByRole('heading', { name: '流水线', exact: true })).toBeVisible()
    await expect(page.getByText('状态筛选仅作用于当前页')).toBeVisible()

    const allRows = await page.locator('tr[data-row]').count()
    expect(allRows).toBe(5)
    await expect(visibleRows(page)).toHaveCount(allRows)

    const search = page.getByPlaceholder('搜索流水线、仓库或分支')
    await search.fill('构建')
    await expect.poll(() => visibleRows(page).count()).toBeGreaterThan(0)
    await expect.poll(() => visibleRows(page).count()).toBeLessThan(allRows)
    const matchedTexts = await visibleRows(page).evaluateAll((rows) =>
      rows.map((row) => row.getAttribute('data-text') ?? ''),
    )
    expect(matchedTexts.length).toBeGreaterThan(0)
    expect(matchedTexts.every((text) => text.toLowerCase().includes('构建'))).toBe(true)

    await search.fill('')
    await page.locator('#list-filter button[data-filter="SUCCEEDED"]').click()
    const statuses = await visibleRows(page).evaluateAll((rows) =>
      rows.map((row) => row.getAttribute('data-status')),
    )
    expect(statuses.length).toBeGreaterThan(0)
    expect(statuses.every((status) => status === 'SUCCEEDED')).toBe(true)

    await search.fill('zzz')
    await expect.poll(() => visibleRows(page).count()).toBe(0)
  })

  test('E2E-PIPE-02 新建：添加阶段、打开任务面板并保存', async ({ page }) => {
    await page.goto('/index.html#/list')
    await page.getByRole('button', { name: '新建流水线' }).click()
    await expect(page.locator('.editor-title')).toHaveText('新建流水线')

    await page.getByRole('button', { name: '添加阶段' }).click()
    await expect(page.locator('.toast')).toHaveText('已添加一个新阶段')

    await page.getByRole('button', { name: '添加任务' }).first().click()
    await expect(page.locator('.inspector-head')).toContainText('添加任务')
    await expect(page.locator('.task-groups')).toBeVisible()

    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.locator('.toast')).toHaveText('已保存')
  })

  test('E2E-PIPE-03 运行：填写运行时参数并进入运行详情', async ({ page }) => {
    await page.goto('/index.html#/list')
    await page.locator('tr[data-row]').first().getByRole('button', { name: '运行' }).click()

    const dialog = page.locator('.dialog').filter({ hasText: '运行参数' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText('镜像标签')).toBeVisible()
    await expect(dialog.getByText('部署环境')).toBeVisible()

    await dialog.locator('input').first().fill('v9.9.9')
    await dialog.getByRole('button', { name: '运行' }).click()

    await expect(page).toHaveURL(/#\/run\/1\/128/)
    await expect(page.locator('.detail-title h1')).toContainText('#128')
    await expect(page.locator('.detail-title')).toContainText('运行中')
    await expect(page.locator('.run-canvas')).toBeVisible()
  })

  test('E2E-PIPE-04 运行详情：查看全程与阶段日志', async ({ page }) => {
    await page.goto('/index.html#/run/1/128')

    await page.getByRole('button', { name: '查看全程日志' }).click()
    const sheet = page.locator('.sheet')
    await expect(sheet.locator('.sheet-head')).toContainText('全程日志')
    await expect(sheet).toContainText('BUILD SUCCESS')
    await page.getByLabel('关闭日志').click()
    await expect(page.locator('.sheet')).toHaveCount(0)

    await page.getByRole('button', { name: '日志', exact: true }).first().click()
    await expect(page.locator('.sheet-head')).toContainText('阶段 · 构建')
    await page.getByLabel('关闭日志').click()
    await expect(page.locator('.sheet')).toHaveCount(0)
  })

  test('E2E-PIPE-05 审批与危险操作：通过、取消、删除均有确认', async ({ page }) => {
    await page.goto('/index.html?state=waiting#/run/1/128')
    await expect(page.locator('.detail-title')).toContainText('待审批')

    await page.getByRole('button', { name: '通过', exact: true }).click()
    await expect(page.locator('.toast')).toHaveText('已通过审批')

    await page.getByRole('button', { name: '取消', exact: true }).click()
    const cancelDialog = page.locator('.dialog').filter({ hasText: '取消运行 #128？' })
    await expect(cancelDialog).toBeVisible()
    await expect(cancelDialog).toContainText('正在执行的任务会被中止')
    await cancelDialog.getByRole('button', { name: '取消运行' }).click()
    await expect(page.locator('.dialog')).toHaveCount(0)

    await page.goto('/index.html#/list')
    await page.locator('tr[data-row]').first().getByRole('button', { name: '删除' }).click()
    const deleteDialog = page.locator('.dialog').filter({ hasText: '删除流水线「构建部署流水线」？' })
    await expect(deleteDialog).toBeVisible()
    await expect(deleteDialog).toContainText('删除后无法恢复')
    await deleteDialog.getByRole('button', { name: '删除', exact: true }).click()
    await expect(page.locator('.dialog')).toHaveCount(0)
  })
})
