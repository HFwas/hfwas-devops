import { useQuery } from '@tanstack/react-query'
import { pmProjectApi } from '@/modules/pm/api'
import { asId } from '@/modules/pm/utils/id'
import { useAuthStore } from '@/stores/auth'

export function ProjectSelect({
  value,
  onChange,
}: {
  value: string
  onChange: (value: string) => void
}) {
  const tenantVersion = useAuthStore((s) => s.tenantVersion)
  const activeTenantId = useAuthStore((s) => s.activeTenantId)
  const projectsQuery = useQuery({
    queryKey: ['pm-projects', 'api-test-filter', activeTenantId, tenantVersion],
    queryFn: () => pmProjectApi.page({ pageNo: 1, pageSize: 100 }),
  })
  const projects = projectsQuery.data?.records ?? []

  return (
    <select
      className="h-8 max-w-56 rounded-md border border-input bg-background px-2 text-sm"
      value={value}
      aria-label="按项目筛选"
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="">选择项目</option>
      {projects.map((project) => (
        <option key={asId(project.id)} value={asId(project.id)}>
          {project.name}
        </option>
      ))}
    </select>
  )
}
