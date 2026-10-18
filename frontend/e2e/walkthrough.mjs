import { spawn } from 'node:child_process'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from '@playwright/test'

/**
 * AI 走查证据采集脚本。
 *
 * 它不做“好/坏”判断，只按 persona 任务操作页面，记录：
 * - 每一步的意图、动作、目标、URL、截图、页面观察
 * - 控制台错误
 * - ARIA 快照
 * - Playwright trace 与视频
 *
 * 结论由评审者（AI 或人）基于这些证据撰写。
 */

const PORT = Number(process.env.PLAYWRIGHT_PROTOTYPE_PORT || 8099)
const BASE = `http://127.0.0.1:${PORT}/index.html`
const OUT = path.resolve('test-results/pilot')
const SHOTS = path.join(OUT, 'shots')
const ARIA = path.join(OUT, 'aria')

const tasks = [
  { id: 'UX-01', persona: '首次使用的研发', goal: '找到并新建一条流水线' },
  { id: 'UX-02', persona: '首次使用的研发', goal: '给流水线添加一个构建任务' },
  { id: 'UX-03', persona: '日常使用的研发', goal: '用运行时参数启动一次运行' },
  { id: 'UX-04', persona: '排障中的研发', goal: '找到失败任务的日志' },
  { id: 'UX-05', persona: '审批人', goal: '处理待审批并取消一次运行' },
]

const actions = []
const consoleMessages = []
let stepNo = 0

async function waitForServer(url) {
  for (let i = 0; i < 60; i += 1) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {
      // retry
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`prototype server not ready: ${url}`)
}

async function textOf(locator) {
  if ((await locator.count()) === 0) return null
  return (await locator.first().textContent())?.trim() || null
}

async function observe(page) {
  const bodyText = (await page.locator('body').innerText()).replace(/\s+/g, ' ').trim()
  return {
    title: await textOf(page.locator('h1')),
    toast: await textOf(page.locator('.toast')),
    dialog: await textOf(page.locator('.dialog h3')),
    sheet: await textOf(page.locator('.sheet-head span').first()),
    editorTitle: await textOf(page.locator('.editor-title')),
    inspector: await textOf(page.locator('.inspector-head span')),
    visibleRows: await page.locator('tr[data-row]:visible').count(),
    editorJobs: await page.locator('.editor-job').count(),
    bodyText: bodyText.slice(0, 320),
  }
}

async function record(page, task, intent, action, target, fn) {
  const before = await observe(page)
  const startedAt = Date.now()
  if (fn) await fn()
  await page.waitForTimeout(120)
  stepNo += 1
  const file = `${task}-${String(stepNo).padStart(2, '0')}.png`
  await page.screenshot({ path: path.join(SHOTS, file) })
  try {
    const aria = await page.locator('body').ariaSnapshot()
    await writeFile(path.join(ARIA, `${task}-${String(stepNo).padStart(2, '0')}.yml`), aria, 'utf8')
  } catch {
    // ARIA snapshot is best-effort evidence.
  }
  const after = await observe(page)
  actions.push({
    task,
    step: stepNo,
    intent,
    action,
    target,
    url: page.url(),
    screenshot: `shots/${file}`,
    durationMs: Date.now() - startedAt,
    before,
    after,
  })
}

async function main() {
  await rm(OUT, { recursive: true, force: true })
  await mkdir(SHOTS, { recursive: true })
  await mkdir(ARIA, { recursive: true })

  const server = spawn(process.execPath, ['e2e/support/prototype-server.mjs'], {
    stdio: 'ignore',
    env: { ...process.env, PLAYWRIGHT_PROTOTYPE_PORT: String(PORT) },
  })

  try {
    await waitForServer(`${BASE}#/list`)
    const browser = await chromium.launch()
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      recordVideo: { dir: path.join(OUT, 'video') },
    })
    await context.tracing.start({ screenshots: true, snapshots: true, sources: true })
    const page = await context.newPage()
    page.on('console', (message) => {
      if (message.type() === 'error') consoleMessages.push({ type: 'console', text: message.text() })
    })
    page.on('pageerror', (error) => consoleMessages.push({ type: 'pageerror', text: error.message }))

    // UX-01：首次用户从列表新建流水线
    await record(page, 'UX-01', '打开流水线列表，确认从哪里开始', 'goto', '#/list', async () => {
      await page.goto(`${BASE}#/list`)
    })
    await record(page, 'UX-01', '寻找新建入口', 'click', '新建流水线', async () => {
      await page.getByRole('button', { name: '新建流水线' }).click()
    })
    await record(page, 'UX-01', '填写流水线名称', 'fill', '名称', async () => {
      await page.locator('.basic .field input').first().fill('试点流水线')
    })
    await record(page, 'UX-01', '保存流水线，观察反馈', 'click', '保存', async () => {
      await page.getByRole('button', { name: '保存', exact: true }).click()
    })

    // UX-02：首次用户添加任务
    await record(page, 'UX-02', '打开已有流水线的编辑页', 'goto', '#/editor/1', async () => {
      await page.goto(`${BASE}#/editor/1`)
    })
    await record(page, 'UX-02', '寻找添加任务入口', 'click', '添加任务', async () => {
      await page.getByRole('button', { name: '添加任务' }).first().click()
    })
    await record(page, 'UX-02', '点击一个任务，观察是否加入阶段', 'click', 'Sonar 检查', async () => {
      const item = page.locator('.task-item').filter({ hasText: 'Sonar 检查' })
      if ((await item.count()) > 0) await item.first().click()
    })

    // UX-03：日常用户带参数运行
    await record(page, 'UX-03', '从列表发起一次运行', 'click', '运行', async () => {
      await page.goto(`${BASE}#/list`)
      await page.locator('tr[data-row]').first().getByRole('button', { name: '运行' }).click()
    })
    await record(page, 'UX-03', '填写运行时参数', 'fill', '镜像标签', async () => {
      await page.locator('.dialog input').first().fill('v9.9.9')
    })
    await record(page, 'UX-03', '提交运行，观察是否进入运行详情', 'click', '运行', async () => {
      await page.locator('.dialog').getByRole('button', { name: '运行' }).click()
    })

    // UX-04：排障用户查看失败日志
    await record(page, 'UX-04', '打开失败运行，寻找失败原因', 'goto', '?state=failed#/run/1/128', async () => {
      await page.goto(`${BASE}?state=failed#/run/1/128`)
    })
    await record(page, 'UX-04', '打开全程日志', 'click', '查看全程日志', async () => {
      await page.getByRole('button', { name: '查看全程日志' }).click()
    })
    await record(page, 'UX-04', '尝试用 Esc 关闭日志（README 声称支持）', 'press', 'Escape', async () => {
      await page.keyboard.press('Escape')
    })
    await record(page, 'UX-04', '用关闭按钮关闭日志', 'click', '关闭日志', async () => {
      await page.getByLabel('关闭日志').click()
    })
    await record(page, 'UX-04', '点击第一个任务，检查日志标题是否对应任务', 'click', '克隆代码', async () => {
      await page.locator('.run-job').first().click()
    })
    await record(page, 'UX-04', '关闭任务日志', 'click', '关闭日志', async () => {
      await page.getByLabel('关闭日志').click()
    })

    // UX-05：审批人处理审批与危险操作
    await record(page, 'UX-05', '打开待审批运行', 'goto', '?state=waiting#/run/1/128', async () => {
      await page.goto(`${BASE}?state=waiting#/run/1/128`)
    })
    await record(page, 'UX-05', '点击通过，观察反馈', 'click', '通过', async () => {
      await page.getByRole('button', { name: '通过', exact: true }).click()
    })
    await record(page, 'UX-05', '打开取消运行确认框', 'click', '取消', async () => {
      await page.getByRole('button', { name: '取消', exact: true }).click()
    })
    await record(page, 'UX-05', '确认取消运行，观察成功反馈', 'click', '取消运行', async () => {
      await page.locator('.dialog').getByRole('button', { name: '取消运行' }).click()
    })
    await record(page, 'UX-05', '打开删除流水线确认框', 'click', '删除', async () => {
      await page.goto(`${BASE}#/list`)
      await page.locator('tr[data-row]').first().getByRole('button', { name: '删除' }).click()
    })
    await record(page, 'UX-05', '确认删除，观察成功反馈', 'click', '删除', async () => {
      await page.locator('.dialog').getByRole('button', { name: '删除', exact: true }).click()
    })

    // 额外状态覆盖：空态、加载、校验失败
    for (const [name, url] of [
      ['empty-list', `${BASE}?state=empty#/list`],
      ['loading-list', `${BASE}?state=loading#/list`],
      ['invalid-editor', `${BASE}?state=invalid#/editor/new`],
    ]) {
      await record(page, 'STATE', `检查状态：${name}`, 'goto', name, async () => {
        await page.goto(url)
      })
    }

    await context.tracing.stop({ path: path.join(OUT, 'trace.zip') })
    await context.close()
    await browser.close()

    await writeFile(
      path.join(OUT, 'actions.json'),
      JSON.stringify({ generatedAt: new Date().toISOString(), tasks, actions, consoleMessages }, null, 2),
      'utf8',
    )
    console.log(`walkthrough evidence written to ${OUT}`)
    console.log(`steps: ${actions.length}; console errors: ${consoleMessages.length}`)
  } finally {
    server.kill()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
