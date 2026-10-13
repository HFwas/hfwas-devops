import { AsyncSelect } from '@/components/console/AsyncSelect'
import { pmProjectApi } from '@/modules/pm/api'
import { asId } from '@/modules/pm/utils/id'

/** 项目实体选择器：服务端分页 + 检索 + 下拉到底加载下一页。 */
export function ProjectSelect({
  value,
  onChange,
  clearLabel = '选择项目',
  className,
}: {
  value: string
  onChange: (value: string) => void
  clearLabel?: string
  className?: string
}) {
  return (
    <AsyncSelect
      label="按项目筛选"
      className={className ?? 'w-56'}
      value={value}
      onChange={onChange}
      clearLabel={clearLabel}
      searchPlaceholder="搜索项目"
      emptyText="没有匹配的项目"
      fetchPage={async ({ page, pageSize, keyword }) => {
        const result = await pmProjectApi.page({ pageNo: page, pageSize, keyword: keyword || undefined })
        return {
          records: (result.records ?? []).map((project) => ({ value: asId(project.id), label: project.name })),
          total: result.total,
          pages: result.pages,
        }
      }}
    />
  )
}