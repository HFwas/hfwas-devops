import type { Component } from 'vue'

/**
 * 顶部二级 Tab 配置（当前产品下的一级功能分区）
 * 只放产品自身的分区；平台工作台、用户中心走整页布局，不在这里挂 Tab。
 * 扩展点：追加一项即可；adminOnly 的 Tab 对非管理员隐藏。
 * matchPrefixes 用于根据当前路由反推激活的 Tab。
 * api-test 已迁入 ApiTestShell 竖轨，不再占用二级 Tab。
 */
export interface ConsoleTab {
  key: string
  label: string
  icon: Component
  path: string
  adminOnly?: boolean
  /** 只有路径完全相等才算命中：进入具体资源后由资源自身的布局接管导航 */
  exact?: boolean
  /** 命中这些前缀时该 Tab 高亮；缺省时用 path 判断 */
  matchPrefixes?: string[]
}

export const CONSOLE_TABS: ConsoleTab[] = []

export const DEFAULT_TAB_KEY = 'projects'

/** 根据当前路径推导激活的 Tab key，路径不属于任何 Tab 时返回 null（顶栏不高亮） */
export function resolveActiveTab(path: string, tabs: ConsoleTab[] = CONSOLE_TABS): string | null {
  let matched: ConsoleTab | null = null
  let matchedLength = -1
  tabs.forEach((tab) => {
    const prefixes = tab.matchPrefixes ?? [tab.path]
    prefixes.forEach((prefix) => {
      const hit = tab.exact ? path === prefix : path === prefix || path.startsWith(`${prefix}/`)
      // 取最长前缀命中，避免歧义
      if (hit && prefix.length > matchedLength) {
        matched = tab
        matchedLength = prefix.length
      }
    })
  })
  return matched ? (matched as ConsoleTab).key : null
}
