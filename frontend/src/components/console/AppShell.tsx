import { useEffect, useState } from 'react'
import { LayoutDashboard, Moon, Search, Settings, Sun, Users } from 'lucide-react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { ProductSwitcher } from '@/components/console/ProductSwitcher'
import { getConsoleTheme, subscribeConsoleTheme, toggleConsoleTheme } from '@/shared/console/useConsoleTheme'
import { useAuthStore } from '@/stores/auth'

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const user = useAuthStore((s) => s.user)
  const myTenants = useAuthStore((s) => s.myTenants)
  const activeTenantId = useAuthStore((s) => s.activeTenantId)
  const activeTenantName = useAuthStore((s) => s.activeTenantName)
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn())
  const isAdmin = useAuthStore((s) => s.isAdmin())
  const fetchMe = useAuthStore((s) => s.fetchMe)
  const fetchMyTenants = useAuthStore((s) => s.fetchMyTenants)
  const switchTenant = useAuthStore((s) => s.switchTenant)
  const logout = useAuthStore((s) => s.logout)
  const [keyword, setKeyword] = useState('')
  const [dark, setDark] = useState(getConsoleTheme)

  useEffect(() => subscribeConsoleTheme(() => setDark(getConsoleTheme())), [])

  useEffect(() => {
    if (!isLoggedIn) return
    if (!user) {
      void fetchMe()
    } else {
      void fetchMyTenants()
    }
  }, [fetchMe, fetchMyTenants, isLoggedIn, user])

  const avatarText = (user?.displayName || user?.username || '?').slice(0, 1).toUpperCase()
  const consoleActive = location.pathname === '/workbench'

  async function onSwitchTenant(tenantId: string) {
    try {
      await switchTenant(tenantId)
      toast.success('已切换租户')
      await navigate('/workbench')
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '切换租户失败')
    }
  }

  function onSearch() {
    const value = keyword.trim()
    if (!value) return
    void navigate({ pathname: '/pm/projects', search: `?keyword=${encodeURIComponent(value)}` })
  }

  return (
    <div className="flex min-h-svh flex-col bg-muted/40">
      <header className="sticky top-0 z-40 flex h-14 items-center gap-3 border-b bg-background px-4">
        <div className="flex items-center gap-2">
          <Link to="/workbench" className="flex items-center gap-2 text-foreground">
            <span className="flex size-7 items-center justify-center rounded-md bg-primary text-sm font-semibold text-primary-foreground">
              H
            </span>
            <span className="text-sm font-semibold">HFWAS DevOps</span>
          </Link>
          {isLoggedIn && (
            <Button
              variant={consoleActive ? 'secondary' : 'ghost'}
              size="sm"
              onClick={() => void navigate('/workbench')}
            >
              <LayoutDashboard />
              控制台
            </Button>
          )}
          {isLoggedIn && <ProductSwitcher />}
        </div>

        <form
          className="mx-auto hidden w-full max-w-md md:block"
          onSubmit={(event) => {
            event.preventDefault()
            onSearch()
          }}
        >
          {isLoggedIn && (
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="搜索项目名称或编码"
                className="pl-8"
              />
            </div>
          )}
        </form>

        {isLoggedIn && (
          <div className="ml-auto flex items-center gap-2">
            {myTenants.length > 1 && (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="sm">
                    {activeTenantName || '租户'}
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {myTenants.map((tenant) => (
                    <DropdownMenuItem
                      key={String(tenant.id)}
                      disabled={String(tenant.id) === String(activeTenantId)}
                      onSelect={() => void onSwitchTenant(String(tenant.id))}
                    >
                      {tenant.name}
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
            )}
            <Button variant="ghost" size="icon" onClick={toggleConsoleTheme} aria-label="切换主题">
              {dark ? <Sun /> : <Moon />}
            </Button>
            {isAdmin && (
              <Button variant="ghost" size="icon" aria-label="用户中心" onClick={() => void navigate('/user/accounts')}>
                <Users />
              </Button>
            )}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button type="button" className="rounded-full">
                  <Avatar>
                    <AvatarFallback>{avatarText}</AvatarFallback>
                  </Avatar>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onSelect={() => void navigate('/user/settings')}>
                  <Settings />
                  账号设置
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => void logout()}>退出登录</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </header>
      <main className="min-h-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}
