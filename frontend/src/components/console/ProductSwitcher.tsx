import { Check, ChevronsUpDown, LayoutDashboard, Users } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem, useSidebar } from '@/components/ui/sidebar'
import { resolveShellScope } from '@/shared/console/navigation'
import { groupProducts, resolveActiveProduct } from '@/shared/console/products'

export function ProductSwitcher({ isAdmin }: { isAdmin: boolean }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { isMobile, state } = useSidebar()
  const product = resolveActiveProduct(location.pathname)
  const scope = resolveShellScope(location.pathname)
  const title = product?.name ?? (scope === 'user' ? '用户中心' : '选择产品')
  const subtitle = product?.description ?? 'HFWAS DevOps'
  const Icon = product?.icon ?? (scope === 'user' ? Users : LayoutDashboard)
  const groups = groupProducts()

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              tooltip={title}
              aria-label="切换产品"
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <span className="flex size-8 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
                <Icon className="size-4" />
              </span>
              <span className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-semibold">{title}</span>
                <span className="truncate text-xs text-muted-foreground">{subtitle}</span>
              </span>
              <ChevronsUpDown className="ml-auto" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-72"
            align="start"
            side={isMobile || state === 'collapsed' ? 'right' : 'bottom'}
            sideOffset={4}
          >
            <DropdownMenuGroup>
              <DropdownMenuItem onSelect={() => void navigate('/workbench')}>
                <LayoutDashboard />
                工作台
                {scope === 'workbench' && <Check className="ml-auto" />}
              </DropdownMenuItem>
              {isAdmin && (
                <DropdownMenuItem onSelect={() => void navigate('/user/accounts')}>
                  <Users />
                  用户中心
                  {scope === 'user' && <Check className="ml-auto" />}
                </DropdownMenuItem>
              )}
            </DropdownMenuGroup>
            {groups.map((group) => (
              <DropdownMenuGroup key={group.group}>
                <DropdownMenuSeparator />
                <DropdownMenuLabel>{group.group}</DropdownMenuLabel>
                {group.items.map((item) => {
                  const ItemIcon = item.icon
                  const active = product?.key === item.key
                  return (
                    <DropdownMenuItem
                      key={item.key}
                      disabled={item.comingSoon}
                      onSelect={() => {
                        if (!item.comingSoon) void navigate(item.path)
                      }}
                    >
                      <ItemIcon />
                      <span className="flex min-w-0 flex-col">
                        <span>{item.name}</span>
                        <span className="truncate text-xs text-muted-foreground">
                          {item.comingSoon ? '即将推出' : item.description}
                        </span>
                      </span>
                      {active && <Check className="ml-auto" />}
                    </DropdownMenuItem>
                  )
                })}
              </DropdownMenuGroup>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
