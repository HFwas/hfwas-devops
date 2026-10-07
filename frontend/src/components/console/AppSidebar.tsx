import { Link, useLocation } from 'react-router'
import { ProductSwitcher } from '@/components/console/ProductSwitcher'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import {
  CONTAINER_NAV_GROUPS,
  clusterIdFromPath,
  containerNavHref,
  isContainerNavActive,
} from '@/modules/container/nav'
import { useContainerCluster } from '@/modules/container/clusterStore'
import { isMenuItemActive, menuForScope, resolveShellScope } from '@/shared/console/navigation'

const groupLabelClass = 'font-semibold uppercase tracking-wide text-muted-foreground'
const activeIconClass = 'data-[active=true]:[&_svg]:text-sidebar-primary'

export function AppSidebar({ isAdmin }: { isAdmin: boolean }) {
  const { pathname } = useLocation()
  const scope = resolveShellScope(pathname)

  return (
    <Sidebar variant="inset" collapsible="icon">
      <SidebarHeader>
        <ProductSwitcher isAdmin={isAdmin} />
      </SidebarHeader>
      <SidebarContent>
        {scope === 'container' ? (
          <ContainerMenu pathname={pathname} />
        ) : (
          menuForScope(scope, { isAdmin }).map((group) => (
            <SidebarGroup key={group.key}>
              {group.label && <SidebarGroupLabel className={groupLabelClass}>{group.label}</SidebarGroupLabel>}
              <SidebarGroupContent>
                <SidebarMenu>
                  {group.items.map((item) => {
                    const Icon = item.icon
                    return (
                      <SidebarMenuItem key={item.key}>
                        <SidebarMenuButton
                          asChild
                          isActive={isMenuItemActive(pathname, item)}
                          tooltip={item.label}
                          className={activeIconClass}
                        >
                          <Link to={item.to}>
                            <Icon />
                            <span>{item.label}</span>
                          </Link>
                        </SidebarMenuButton>
                      </SidebarMenuItem>
                    )
                  })}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ))
        )}
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  )
}

function ContainerMenu({ pathname }: { pathname: string }) {
  const storedClusterId = useContainerCluster((state) => state.currentId)
  const clusterId = clusterIdFromPath(pathname) ?? storedClusterId

  return (
    <>
      {CONTAINER_NAV_GROUPS.map((group) => (
        <SidebarGroup key={group.key}>
          <SidebarGroupLabel className={groupLabelClass}>{group.label}</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {group.items.map((item) => {
                const href = containerNavHref(item, clusterId)
                const Icon = item.icon
                const active = isContainerNavActive(pathname, item)
                return (
                  <SidebarMenuItem key={item.key}>
                    {href ? (
                      <SidebarMenuButton asChild isActive={active} tooltip={item.label} className={activeIconClass}>
                        <Link to={href}>
                          <Icon />
                          <span>{item.label}</span>
                        </Link>
                      </SidebarMenuButton>
                    ) : (
                      <SidebarMenuButton disabled aria-disabled tooltip={item.label} className="opacity-40">
                        <Icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    )}
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      ))}
    </>
  )
}
