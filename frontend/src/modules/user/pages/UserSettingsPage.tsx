import { DetailShell } from '@/components/console/DetailShell'
import { useAuthStore } from '@/stores/auth'

const ROLE_LABEL: Record<string, string> = {
  admin: '管理员',
  user: '成员',
}

export function UserSettingsPage() {
  const user = useAuthStore((s) => s.user)
  const tenantName = useAuthStore((s) => s.activeTenantName)

  return (
    <DetailShell
      title={user?.displayName || user?.username || '账号设置'}
      description={user ? `${user.username} · ${ROLE_LABEL[user.role] ?? user.role}` : '加载账号资料'}
      meta={tenantName || user?.tenantName}
      tabs={[{ value: '资料', label: '资料' }]}
      value="资料"
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="用户名" value={user?.username} />
        <Field label="显示名" value={user?.displayName} />
        <Field label="邮箱" value={user?.email} />
        <Field label="手机" value={user?.phone} />
        <Field label="当前租户" value={tenantName || user?.tenantName} />
        <Field label="认证来源" value={user?.authSource} />
      </div>
    </DetailShell>
  )
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{value?.trim() ? value : '—'}</span>
    </div>
  )
}
