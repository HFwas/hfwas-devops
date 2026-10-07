import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Input } from '@/components/ui/input'
import { userManageApi } from '@/modules/user/api'
import type { UserProfile } from '@/modules/user/types'
import { asId } from '@/modules/pm/utils/id'
import { useAuthStore } from '@/stores/auth'

const ROLE_LABEL: Record<string, string> = {
  admin: '管理员',
  user: '成员',
}

export function UserListPage() {
  const isAdmin = useAuthStore((s) => s.isAdmin())
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['user-accounts', keyword],
    queryFn: () => userManageApi.page({ pageNo: 1, pageSize: 50, keyword: keyword.trim() || undefined }),
    enabled: isAdmin,
  })
  const rows = query.data?.records ?? []
  const total = query.data?.total ?? rows.length

  if (!isAdmin) {
    return <p className="text-sm text-muted-foreground">需要管理员权限才能查看用户列表。</p>
  }

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="用户管理"
        description={query.isSuccess ? `共 ${total} 个账号` : '查看平台账号与启用状态'}
        actions={
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索用户名"
            aria-label="搜索用户"
            className="h-8 w-56"
          />
        }
      />
      <DataTable<UserProfile>
        columns={[
          {
            id: 'name',
            header: '显示名',
            cell: (user) => <span className="font-medium">{user.displayName || user.username}</span>,
          },
          { id: 'username', header: '用户名', cell: (user) => user.username },
          { id: 'email', header: '邮箱', cell: (user) => user.email || '—' },
          { id: 'role', header: '角色', cell: (user) => ROLE_LABEL[user.role] ?? user.role },
          {
            id: 'status',
            header: '状态',
            cell: (user) => (
              <StatusIcon status={user.enabled === 0 ? 'disabled' : 'active'} label={user.enabled === 0 ? '停用' : '启用'} />
            ),
          },
          {
            id: 'tenants',
            header: '租户',
            className: 'max-w-sm truncate',
            cell: (user) => user.tenantNames?.join('、') || user.tenantName || '—',
          },
        ]}
        data={rows}
        getRowId={(user) => asId(user.id) || user.username}
        loading={query.isFetching}
        error={query.isError ? '用户列表加载失败' : undefined}
        empty="没有匹配的用户"
      />
    </div>
  )
}
