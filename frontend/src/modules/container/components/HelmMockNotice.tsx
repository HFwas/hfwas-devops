import { HELM_USE_MOCK } from '@/modules/container/api/helm'

export function HelmMockNotice({ syntheticCluster = false }: { syntheticCluster?: boolean }) {
  if (!HELM_USE_MOCK) return null
  return (
    <p className="rounded-md border bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
      Helm 后端尚未提供，当前为本地示例数据，安装与升级只保存在本次会话。
      {syntheticCluster ? ' 未选择集群，示例 Release 使用 demo-cluster。' : ''}
    </p>
  )
}
