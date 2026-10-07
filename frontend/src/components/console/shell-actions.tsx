import { createContext, useContext, type ReactNode } from 'react'

const ShellActionsContext = createContext<((node: ReactNode) => void) | null>(null)

export const ShellActionsProvider = ShellActionsContext.Provider

/** 模块把特有控件注册到顶栏主操作槽。离开路由时由调用方清掉。 */
export function useShellActions() {
  return useContext(ShellActionsContext)
}
