import { Fragment, useEffect, useState, type ReactNode } from 'react'
import { Moon, Search, Settings, Sun } from 'lucide-react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { AppSidebar } from '@/components/console/AppSidebar'
import { resolveBreadcrumbs } from '@/components/console/breadcrumbs'
import { ShellActionsProvider } from '@/components/console/shell-actions'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
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
  const [actions, setActions] = useState<ReactNode>(null)
  const crumbs = resolveBreadcrumbs(location.pathname)

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
    <ShellActionsProvider value={setActions}>
      <SidebarProvider>
        <AppSidebar isAdmin={isAdmin} />
        <SidebarInset className="min-w-0">
          <header className="sticky top-0 z-40 flex h-(--header-height) shrink-0 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur">
            <SidebarTrigger className="-ml-1" aria-label="切换侧栏" />
            <Separator orientation="vertical" className="mr-1 data-[orientation=vertical]:h-4" />
            <Breadcrumb className="min-w-0 flex-1 overflow-hidden">
              <BreadcrumbList className="flex-nowrap">
                {crumbs.map((crumb, index) => {
                  const current = index === crumbs.length - 1
                  return (
                    <Fragment key={`${crumb.label}-${index}`}>
                      {index > 0 && <BreadcrumbSeparator />}
                      <BreadcrumbItem className="min-w-0">
                        {current || !crumb.to ? (
                          <BreadcrumbPage className="truncate">{crumb.label}</BreadcrumbPage>
                        ) : (
                          <BreadcrumbLink asChild>
                            <Link to={crumb.to} className="truncate">
                              {crumb.label}
                            </Link>
                          </BreadcrumbLink>
                        )}
                      </BreadcrumbItem>
                    </Fragment>
                  )
                })}
              </BreadcrumbList>
            </Breadcrumb>

            <form
              className="hidden w-full max-w-sm shrink-0 md:block"
              onSubmit={(event) => {
                event.preventDefault()
                onSearch()
              }}
            >
              <div className="relative">
                <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder="搜索项目名称或编码"
                  aria-label="搜索项目"
                  className="h-8 pl-8"
                />
              </div>
            </form>

            <div className="flex shrink-0 items-center gap-2">
              {actions}
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
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button type="button" className="rounded-full" aria-label="用户菜单">
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
          </header>
          <div className="flex flex-1 flex-col gap-4 px-4 py-4 md:gap-6 lg:px-6">
            <Outlet />
          </div>
        </SidebarInset>
      </SidebarProvider>
    </ShellActionsProvider>
  )
}
