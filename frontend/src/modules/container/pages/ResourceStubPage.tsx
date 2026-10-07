import { PageHeader } from '@/components/console/PageHeader'

/** 菜单已挂上、后端或列表还没做的资源。不返回假数据。 */
export function ResourceStubPage({ title }: { title: string }) {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={title} description="该资源列表即将支持" />
      <div className="flex h-40 items-center justify-center rounded-lg border bg-card text-sm text-muted-foreground">
        即将支持
      </div>
    </div>
  )
}
