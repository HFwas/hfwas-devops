export interface ShellSearchCopy {
  placeholder: string
  label: string
  kind: 'project' | 'pod' | 'pipeline'
}

/** 顶栏搜索文案随当前产品变化。容器与流水线页不沿用项目管理的占位。 */
export function shellSearchCopy(pathname: string): ShellSearchCopy {
  if (pathname === '/container' || pathname.startsWith('/container/')) {
    return { placeholder: '搜索 Pod 名称', label: '搜索 Pod', kind: 'pod' }
  }
  if (pathname === '/pipeline' || pathname.startsWith('/pipeline/')) {
    return { placeholder: '搜索流水线名称', label: '搜索流水线', kind: 'pipeline' }
  }
  return { placeholder: '搜索项目名称或编码', label: '搜索项目', kind: 'project' }
}
