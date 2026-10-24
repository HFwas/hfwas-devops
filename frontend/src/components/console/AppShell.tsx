import { Fragment, useEffect, useState, type ReactNode } from 'react'
import { Bell, CircleHelp, Moon, Search, Settings, Sun } from 'lucide-react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { AppSidebar } from '@/components/console/AppSidebar'
import { ProductSwitcher } from '@/components/console/ProductSwitcher'
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
import { clusterIdFromPath } from '@/modules/container/nav'
import { resolveActiveProduct } from '@/shared/console/products'
import { shellSearchCopy } from '@/shared/console/shellSearch'
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
  const product = resolveActiveProduct(location.pathname)
  // 面包屑首项若是当前产品名，则上提到 header 单独展示，避免重复。
  const productName = product && crumbs[0]?.label === product.name ? crumbs[0].label : null
  const trail = productName ? crumbs.slice(1) : crumbs
  const searchCopy = shellSearchCopy(location.pathname)

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
    if (searchCopy.kind === 'pod') {
      const clusterId = clusterIdFromPath(location.pathname)
      if (!clusterId) {
        toast.message('进入集群后再搜索 Pod')
        return
      }
      void navigate({
        pathname: `/container/clusters/${clusterId}/pods`,
        search: `?keyword=${encodeURIComponent(value)}`,
      })
      return
    }
    if (searchCopy.kind === 'pipeline') {
      void navigate({ pathname: '/pipeline/pipelines', search: `?q=${encodeURIComponent(value)}` })
      return
    }
    void navigate({ pathname: '/pm/projects', search: `?keyword=${encodeURIComponent(value)}` })
  }

  return (
    <ShellActionsProvider value={setActions}>
      <SidebarProvider className="h-svh min-h-0 flex-col gap-0">
        <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur">
          <SidebarTrigger className="-ml-1" aria-label="切换侧栏" />
          <ProductSwitcher isAdmin={isAdmin} variant="header" />
          <Separator orientation="vertical" className="data-[orientation=vertical]:h-4" />
          {productName ? (
            <>
              <span className="shrink-0 text-sm font-semibold">{productName}</span>
              <Separator orientation="vertical" className="data-[orientation=vertical]:h-4" />
            </>
          ) : null}
          <Breadcrumb className="min-w-0 flex-1">
              <BreadcrumbList className="flex-nowrap overflow-hidden">
                {trail.map((crumb, index) => {
                  const current = index === trail.length - 1
                  return (
                    <Fragment key={`${crumb.label}-${index}`}>
                      {index > 0 && <BreadcrumbSeparator className="shrink-0" />}
                      <BreadcrumbItem className={current ? 'min-w-0 shrink' : 'shrink-0'}>
                        {current || !crumb.to ? (
                          <BreadcrumbPage className="block max-w-full truncate" title={crumb.label}>
                            {crumb.label}
                          </BreadcrumbPage>
                        ) : (
                          <BreadcrumbLink asChild>
                            <Link to={crumb.to} className="whitespace-nowrap" title={crumb.label}>
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
                  placeholder={searchCopy.placeholder}
                  aria-label={searchCopy.label}
                  className="h-8 pl-8"
                />
              </div>
            </form>

            <div className="flex shrink-0 items-center gap-2">
              {actions}
              <Button variant="ghost" size="icon" aria-label="帮助" onClick={() => toast.info('帮助中心建设中')}>
                <CircleHelp />
              </Button>
              <Button variant="ghost" size="icon" aria-label="通知（有未读）" className="relative" onClick={() => toast.info('暂无新通知')}>
                <Bell />
                <span className="absolute top-1.5 right-1.5 size-1.5 rounded-full bg-destructive" />
              </Button>
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
        <div className="flex min-h-0 flex-1">
          <AppSidebar isAdmin={isAdmin} />
          <SidebarInset className="min-h-0 min-w-0 overflow-auto">
            <div className="flex flex-1 flex-col gap-4 px-4 py-4 md:gap-6 lg:px-6">
              <Outlet />
            </div>
          </SidebarInset>
        </div>
      </SidebarProvider>
    </ShellActionsProvider>
  )
}
