export interface ShellSearchCopy {
  placeholder: string
  label: string
  kind: 'project' | 'pod'
}

/** 顶栏搜索文案随当前产品变化。容器页不沿用项目管理的占位。 */
export function shellSearchCopy(pathname: string): ShellSearchCopy {
  if (pathname === '/container' || pathname.startsWith('/container/')) {
    return { placeholder: '搜索 Pod 名称', label: '搜索 Pod', kind: 'pod' }
  }
  return { placeholder: '搜索项目名称或编码', label: '搜索项目', kind: 'project' }
}
